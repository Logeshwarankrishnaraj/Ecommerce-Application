package com.commerce.inventory;

import com.commerce.audit.AuditService;
import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.catalog.Product;
import com.commerce.common.api.PageResponse;
import com.commerce.common.util.Like;
import com.commerce.common.exception.*;
import com.commerce.inventory.InventoryRepositories.*;
import com.commerce.security.CurrentUser;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock model (documented decision): COD orders decrement on-hand at placement; online-payment orders first
 * reserve stock and convert the reservation to a decrement when payment is verified, or release it on
 * failure/cancel. available = on_hand - reserved. Every change writes an immutable movement row and happens
 * under a row lock so two buyers of the last unit cannot both succeed.
 */
@Service
public class InventoryService {
    public record Row(Long productId, String sku, String title, int onHand, int reserved, int available, int lowStockThreshold, boolean low) {}
    public record MovementRow(Long movementId, int quantityDelta, String reason, String referenceType, String referenceId, String actor, java.time.Instant createdAt) {}
    public record AdjustmentRequest(Long productId, Integer quantityDelta, String reason) {}

    private final InventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final ProductRepository products;
    private final AuditService audit;
    private final LowStockAlerter alerter;
    private final Clock clock;

    public InventoryService(InventoryRepository inventory, InventoryMovementRepository movements, ProductRepository products,
                            AuditService audit, LowStockAlerter alerter, Clock clock) {
        this.inventory = inventory;
        this.movements = movements;
        this.products = products;
        this.audit = audit;
        this.alerter = alerter;
        this.clock = clock;
    }

    /** Creates the inventory row for a new product. */
    @Transactional
    public Inventory initialise(Long productId, int initial, int threshold, String actor) {
        Inventory i = new Inventory();
        i.setProductId(productId);
        i.setOnHand(Math.max(initial, 0));
        i.setLowStockThreshold(Math.max(threshold, 0));
        i = inventory.save(i);
        if (initial > 0) record(productId, initial, "Initial stock", "PRODUCT", String.valueOf(productId), actor);
        return i;
    }

    @Transactional
    public Row adjust(AdjustmentRequest req) {
        if (req.productId() == null) throw new ValidationFailedException("productId", "Product is required.");
        if (req.quantityDelta() == null || req.quantityDelta() == 0) throw new ValidationFailedException("quantityDelta", "Enter a non-zero quantity change.");
        if (Math.abs(req.quantityDelta()) > 100_000) throw new ValidationFailedException("quantityDelta", "Quantity change is too large.");
        if (req.reason() == null || req.reason().isBlank() || req.reason().length() > 250) throw new ValidationFailedException("reason", "A reason (max 250 characters) is required.");
        Inventory i = lock(req.productId());
        int availableBefore = i.available();
        int newOnHand = i.getOnHand() + req.quantityDelta();
        if (newOnHand < i.getReserved()) {
            throw new BusinessRuleException("Stock cannot go below the reserved quantity (" + i.getReserved() + ").");
        }
        i.setOnHand(newOnHand);
        record(req.productId(), req.quantityDelta(), req.reason().trim(), "ADJUSTMENT", null, CurrentUser.label());
        audit.record("STOCK_ADJUST", "PRODUCT", req.productId(), "delta " + req.quantityDelta() + " reason: " + req.reason());
        alerter.afterChange(i, availableBefore);
        return row(i, products.findById(req.productId()).orElseThrow());
    }

    /** Reserve for an online order awaiting payment. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long productId, int qty, String ref) {
        Inventory i = lock(productId);
        int availableBefore = i.available();
        if (availableBefore < qty) throw new InsufficientStockException("Not enough stock for " + title(productId) + ".");
        i.setReserved(i.getReserved() + qty);
        record(productId, 0, "Reserved " + qty + " for order", "ORDER", ref, "system");
        alerter.afterChange(i, availableBefore);
    }

    /** Immediate decrement for COD orders. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sell(Long productId, int qty, String ref) {
        Inventory i = lock(productId);
        int availableBefore = i.available();
        if (availableBefore < qty) throw new InsufficientStockException("Not enough stock for " + title(productId) + ".");
        i.setOnHand(i.getOnHand() - qty);
        record(productId, -qty, "Sale", "ORDER", ref, "system");
        alerter.afterChange(i, availableBefore);
    }

    /** Payment verified: turn the reservation into a real decrement. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commitReservation(Long productId, int qty, String ref) {
        Inventory i = lock(productId);
        i.setReserved(Math.max(0, i.getReserved() - qty));
        i.setOnHand(Math.max(0, i.getOnHand() - qty));
        record(productId, -qty, "Sale (payment verified)", "ORDER", ref, "system");
    }

    /** Payment failed / order cancelled before fulfilment: release the reservation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservation(Long productId, int qty, String ref) {
        Inventory i = lock(productId);
        i.setReserved(Math.max(0, i.getReserved() - qty));
        record(productId, 0, "Reservation released", "ORDER", ref, "system");
    }

    /** Cancelled after a decrement: put the units back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(Long productId, int qty, String ref) {
        Inventory i = lock(productId);
        i.setOnHand(i.getOnHand() + qty);
        record(productId, qty, "Order cancelled - restocked", "ORDER", ref, "system");
    }

    @Transactional(readOnly = true)
    public PageResponse<Row> list(String q, boolean lowOnly, int page, int size) {
        var p = inventory.search(Like.contains(q), lowOnly, PageRequest.of(Math.max(page, 0), clamp(size)));
        Map<Long, Product> byId = products.findAllById(p.getContent().stream().map(Inventory::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return PageResponse.of(p, i -> row(i, byId.get(i.getProductId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementRow> history(Long productId, int page, int size) {
        products.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product"));
        return PageResponse.of(movements.findByProductIdOrderByIdDesc(productId, PageRequest.of(Math.max(page, 0), clamp(size))),
                m -> new MovementRow(m.getId(), m.getQuantityDelta(), m.getReason(), m.getReferenceType(), m.getReferenceId(), m.getActor(), m.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Inventory get(Long productId) {
        return inventory.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Inventory"));
    }

    @Transactional(readOnly = true)
    public long lowStockCount() {
        return inventory.countLowStock();
    }

    private Inventory lock(Long productId) {
        return inventory.findForUpdate(productId).orElseThrow(() -> new ResourceNotFoundException("Inventory"));
    }

    private String title(Long productId) {
        return products.findById(productId).map(Product::getTitle).orElse("item");
    }

    private void record(Long productId, int delta, String reason, String refType, String refId, String actor) {
        InventoryMovement m = new InventoryMovement();
        m.setProductId(productId);
        m.setQuantityDelta(delta);
        m.setReason(reason);
        m.setReferenceType(refType);
        m.setReferenceId(refId);
        m.setActor(actor);
        m.setCreatedAt(clock.instant());
        movements.save(m);
    }

    private static Row row(Inventory i, Product p) {
        return new Row(i.getProductId(), p.getSku(), p.getTitle(), i.getOnHand(), i.getReserved(), i.available(),
                i.getLowStockThreshold(), i.available() <= i.getLowStockThreshold());
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
