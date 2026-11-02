package com.commerce.order;

import com.commerce.cart.CartService;
import com.commerce.catalog.Product;
import com.commerce.catalog.ProductImage;
import com.commerce.common.api.PageResponse;
import com.commerce.common.exception.BusinessRuleException;
import com.commerce.common.exception.InsufficientStockException;
import com.commerce.common.exception.PriceChangedException;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.order.OrderDtos.*;
import com.commerce.order.OrderRepositories.*;
import com.commerce.payment.Payment;
import com.commerce.payment.PaymentProvider;
import com.commerce.payment.PaymentProviders;
import com.commerce.payment.PaymentRepository;
import com.commerce.security.CurrentUser;
import com.commerce.shop.SettingsService;
import com.commerce.user.UserAddress;
import com.commerce.user.UserAddressRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Customer-facing order placement and queries. Totals, stock and ownership are all decided on the server. */
@Service
public class OrderService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CheckoutQuoteService quotes;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final PaymentProviders providers;
    private final UserAddressRepository addresses;
    private final OrderLifecycle lifecycle;
    private final OrderAssembler assembler;
    private final com.commerce.inventory.InventoryService inventory;
    private final CartService cart;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrderService(CheckoutQuoteService quotes, OrderRepository orders, OrderItemRepository items, PaymentRepository payments,
                        PaymentProviders providers, UserAddressRepository addresses, OrderLifecycle lifecycle, OrderAssembler assembler,
                        com.commerce.inventory.InventoryService inventory, CartService cart, SettingsService settings,
                        PlatformTransactionManager txManager, Clock clock) {
        this.quotes = quotes;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.providers = providers;
        this.addresses = addresses;
        this.lifecycle = lifecycle;
        this.assembler = assembler;
        this.inventory = inventory;
        this.cart = cart;
        this.settings = settings;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /** Idempotent: the same key from the same customer returns the original order instead of creating a second one. */
    public PlacedOrder place(String idempotencyKey, PlaceOrderRequest req) {
        Long userId = CurrentUser.requireId();
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 80) throw new ValidationFailedException("Idempotency-Key", "A valid Idempotency-Key header is required.");
        if (req == null) throw new ValidationFailedException("body", "Request body is required.");
        try {
            return tx.execute(status -> doPlace(userId, key, req));
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key won the unique index; return its order.
            return tx.execute(status -> orders.findByUserIdAndIdempotencyKey(userId, key).map(this::placedView).orElseThrow(() -> race));
        }
    }

    private PlacedOrder doPlace(Long userId, String key, PlaceOrderRequest req) {
        CustomerOrder existing = orders.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing != null) return placedView(existing);

        if (!req.acceptedTerms()) throw new ValidationFailedException("acceptedTerms", "Please accept the terms to place your order.");
        String method = req.paymentMethod() == null ? "" : req.paymentMethod().trim().toUpperCase();
        boolean cod = "COD".equals(method);
        if (!cod && !"ONLINE".equals(method)) throw new ValidationFailedException("paymentMethod", "Choose a valid payment method.");
        if (cod && !settings.getBool("payments.cod.enabled", true)) throw new BusinessRuleException("Cash on delivery is not available right now.");
        PaymentProvider provider = null;
        if (!cod) {
            if (!settings.getBool("payments.online.enabled", false)) throw new BusinessRuleException("Online payment is not available right now.");
            provider = providers.online().orElseThrow(() -> new BusinessRuleException("Online payment is not available right now."));
        }
        if (req.addressId() == null) throw new ValidationFailedException("addressId", "Choose a delivery address.");
        UserAddress a = addresses.findByIdAndUserId(req.addressId(), userId).orElseThrow(() -> new ResourceNotFoundException("Address"));

        CheckoutQuoteService.Resolved resolved = quotes.resolve(userId);
        CheckoutQuoteService.Quote q = resolved.quote();
        if (q.items().isEmpty() && q.issues().isEmpty()) throw new BusinessRuleException("Your cart is empty.");
        if (!q.canCheckout()) throw new InsufficientStockException(q.issues().isEmpty() ? "Some items are unavailable." : q.issues().get(0).message());
        if (req.expectedTotal() == null || req.expectedTotal().compareTo(q.total()) != 0) {
            throw new PriceChangedException("The price changed. The new total is " + q.total().toPlainString() + " " + q.currency() + ". Please review and confirm.");
        }

        var now = clock.instant();
        CustomerOrder draft = new CustomerOrder();
        draft.setOrderNumber(newOrderNumber());
        draft.setUserId(userId);
        draft.setStatus(cod ? OrderStateMachine.PLACED : OrderStateMachine.PENDING_PAYMENT);
        draft.setPaymentMethod(method);
        draft.setPaymentStatus(cod ? Payment.PENDING_COLLECTION : Payment.INITIATED);
        draft.setSubtotal(q.subtotal());
        draft.setShippingAmount(q.shippingAmount());
        draft.setTaxAmount(q.taxAmount());
        draft.setDiscountAmount(q.discountAmount());
        draft.setTotal(q.total());
        draft.setCurrency(q.currency());
        draft.setShipRecipient(a.getRecipientName());
        draft.setShipPhone(a.getPhone());
        draft.setShipLine1(a.getLine1());
        draft.setShipLine2(a.getLine2());
        draft.setShipLocality(a.getLocality());
        draft.setShipCity(a.getCity());
        draft.setShipState(a.getState());
        draft.setShipPostalCode(a.getPostalCode());
        draft.setShipCountry(a.getCountry());
        draft.setIdempotencyKey(key);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        final CustomerOrder o = orders.saveAndFlush(draft);

        // Snapshot lines, then touch stock in a consistent (product id) order to avoid deadlocks.
        List<CheckoutQuoteService.Item> lines = new ArrayList<>(q.items());
        for (int idx = 0; idx < lines.size(); idx++) {
            CheckoutQuoteService.Item l = lines.get(idx);
            Product p = resolved.products().get(idx);
            ProductImage img = resolved.primaryImages().get(idx);
            OrderItem oi = new OrderItem();
            oi.setOrderId(o.getId());
            oi.setProductId(p.getId());
            oi.setSku(l.sku());
            oi.setTitle(l.title());
            oi.setImageKey(img == null ? null : img.getStorageKey());
            oi.setUnitPrice(l.unitPrice());
            oi.setQuantity(l.quantity());
            oi.setLineTotal(l.lineTotal());
            items.save(oi);
        }
        lines.stream().sorted(Comparator.comparing(CheckoutQuoteService.Item::productId)).forEach(l -> {
            if (cod) inventory.sell(l.productId(), l.quantity(), o.getOrderNumber());
            else inventory.reserve(l.productId(), l.quantity(), o.getOrderNumber());
        });

        Payment pay = new Payment();
        pay.setOrderId(o.getId());
        pay.setMethod(method);
        pay.setAmount(o.getTotal());
        pay.setCurrency(o.getCurrency());
        pay.setIdempotencyKey("pay-" + o.getOrderNumber());
        pay.setCreatedAt(now);
        pay.setUpdatedAt(now);
        PaymentInfo info;
        if (cod) {
            pay.setProvider("COD");
            pay.setStatus(Payment.PENDING_COLLECTION);
            payments.save(pay);
            info = new PaymentInfo(pay.getId(), pay.getStatus(), "COD", false, false);
        } else {
            PaymentProvider.Initiation init = provider.initiate(o.getId(), o.getTotal(), o.getCurrency());
            pay.setProvider(provider.code());
            pay.setStatus(Payment.INITIATED);
            pay.setProviderReference(init.providerReference());
            payments.save(pay);
            info = new PaymentInfo(pay.getId(), pay.getStatus(), provider.code(), provider.mock(), init.requiresAction());
        }
        lifecycle.record(o, o.getStatus(), cod ? "Order placed (cash on delivery)" : "Awaiting payment", "customer");
        if (cod) cart.clearFor(userId);   // online carts are cleared only once payment is verified
        return PlacedOrder.of(assembler.detail(o), info);
    }

    private PlacedOrder placedView(CustomerOrder o) {
        Payment pay = payments.findByOrderIdOrderByIdAsc(o.getId()).stream().reduce((x, y) -> y).orElse(null);
        PaymentInfo info = pay == null ? null : new PaymentInfo(pay.getId(), pay.getStatus(), pay.getProvider(),
                providers.byCode(pay.getProvider()).map(PaymentProvider::mock).orElse(false), Payment.INITIATED.equals(pay.getStatus()));
        return PlacedOrder.of(assembler.detail(o), info);
    }

    private String newOrderNumber() {
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(clock.instant().atOffset(ZoneOffset.UTC));
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("JW").append(day).append('-');
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            if (!orders.existsByOrderNumber(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Could not allocate an order number");
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> mine(int page, int size) {
        Long userId = CurrentUser.requireId();
        var p = orders.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return PageResponse.of(p, o -> new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(),
                o.getTotal(), o.getCurrency(), o.getCreatedAt(), items.totalQuantity(o.getId())));
    }

    @Transactional(readOnly = true)
    public OrderDetail mineDetail(Long orderId) {
        return assembler.detail(orders.findByIdAndUserId(orderId, CurrentUser.requireId()).orElseThrow(() -> new ResourceNotFoundException("Order")));
    }

    @Transactional
    public OrderDetail cancelMine(Long orderId) {
        CustomerOrder o = orders.findByIdAndUserId(orderId, CurrentUser.requireId()).orElseThrow(() -> new ResourceNotFoundException("Order"));
        if (!OrderStateMachine.customerCanCancel(o.getStatus())) {
            throw new BusinessRuleException("This order can no longer be cancelled online. Please contact support.");
        }
        lifecycle.cancel(o, "customer", "Cancelled by customer");
        return assembler.detail(o);
    }
}
