import { ImageRef } from './api.models';

export interface ThemeTokens {
  bg: string;
  surface: string;
  text: string;
  accent: string;
  font: 'SERIF' | 'SANS' | 'MIXED' | string;
}

export interface ShopInfo {
  displayName: string;
  tagline: string | null;
  logoUrl: string | null;
  supportEmail: string | null;
  supportPhone: string | null;
  address: string | null;
}

export interface NavLink {
  label: string;
  url: string;
}

export interface SiteConfig {
  theme: ThemeTokens;
  shop: ShopInfo;
  headerLinks: NavLink[];
  footerLinks: NavLink[];
  currency: string;
  paymentMethods: string[];
  mockPayments: boolean;
  imageRatio: string;
  cartMaxQty: number;
}

export interface ProductCard {
  productId: number;
  slug: string;
  sku: string;
  title: string;
  price: number;
  salePrice: number | null;
  effectivePrice: number;
  currency: string;
  primaryImage: ImageRef | null;
  inStock: boolean;
  categoryName: string | null;
}

export interface ProductAttribute {
  code: string;
  label: string;
  valueCode: string;
  valueLabel: string;
}

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';

export interface ProductDetail extends ProductCard {
  description: string | null;
  categoryId: number | null;
  categorySlug: string | null;
  images: ImageRef[];
  attributes: ProductAttribute[];
  stockStatus: StockStatus;
  availableQuantity: number;
  seoTitle: string | null;
  seoDescription: string | null;
}

export interface CategoryNode {
  categoryId: number;
  name: string;
  slug: string;
  description: string | null;
  children: CategoryNode[];
}

export interface Block {
  blockId: number;
  type: 'HERO' | 'CATEGORY_TILES' | 'FEATURED_PRODUCTS' | 'PROMO_STRIP' | 'TEXT_IMAGE' | string;
  title: string | null;
  subtitle: string | null;
  body: string | null;
  imageUrl: string | null;
  linkUrl: string | null;
  ctaLabel: string | null;
  products?: ProductCard[];
  categories?: CategoryNode[];
}

export interface FilterValue {
  valueCode: string;
  displayLabel: string;
}

export interface FilterAttribute {
  code: string;
  label: string;
  values: FilterValue[];
}

export interface FiltersResponse {
  attributes: FilterAttribute[];
  priceRange: { min: number; max: number };
}

export interface ProductQuery {
  q?: string;
  category?: string;
  minPrice?: number | null;
  maxPrice?: number | null;
  sort?: string;
  inStock?: boolean;
  attr?: string[];
  page?: number;
  size?: number;
}

// ---- cart / checkout / orders ----
export interface CartItem {
  cartItemId: number;
  productId: number;
  slug: string;
  title: string;
  sku: string;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  available: boolean;
  availableQuantity: number;
  issue: string | null;
}

export interface Cart {
  items: CartItem[];
  subtotal: number;
  itemCount: number;
  hasIssues: boolean;
  currency: string;
}

export interface WishlistItem {
  productId: number;
  addedAt: string;
  product: ProductCard;
}

export interface Wishlist {
  items: WishlistItem[];
}

export interface QuoteItem {
  productId: number;
  sku: string;
  title: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  imageUrl: string | null;
}

export interface QuoteIssue {
  productId: number;
  code: string;
  message: string;
}

export interface Quote {
  items: QuoteItem[];
  subtotal: number;
  shippingAmount: number;
  taxAmount: number;
  discountAmount: number;
  total: number;
  currency: string;
  issues: QuoteIssue[];
  canCheckout: boolean;
}

export type PaymentMethod = 'COD' | 'ONLINE';

export interface PlaceOrderRequest {
  addressId: number;
  paymentMethod: PaymentMethod;
  expectedTotal: number;
  acceptedTerms: boolean;
}

export interface OrderAddress {
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  locality: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface OrderItemView {
  orderItemId: number;
  productId: number | null;
  sku: string;
  title: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  imageUrl: string | null;
}

export interface ShipmentView {
  shipmentId: number;
  carrier: string | null;
  trackingNumber: string | null;
  status: string;
  shippedAt: string | null;
  deliveredAt: string | null;
  trackingNote: string | null;
}

export interface OrderDetail {
  orderId: number;
  orderNumber: string;
  status: string;
  paymentMethod: string;
  paymentStatus: string;
  subtotal: number;
  shippingAmount: number;
  taxAmount: number;
  discountAmount: number;
  total: number;
  currency: string;
  createdAt: string;
  shippingAddress: OrderAddress;
  items: OrderItemView[];
  shipments: ShipmentView[];
  statusHistory: { status: string; at: string }[];
  cancellable: boolean;
}

export interface PlacedOrder extends OrderDetail {
  payment: {
    paymentId: number;
    status: string;
    provider: string;
    mock: boolean;
    requiresAction: boolean;
  };
}

export interface PaymentVerification {
  paymentId: number;
  status: 'INITIATED' | 'PAID' | 'FAILED' | 'CANCELLED' | string;
  orderId: number;
  orderNumber: string;
  orderStatus: string;
}

export interface OrderSummary {
  orderId: number;
  orderNumber: string;
  status: string;
  paymentMethod: string;
  paymentStatus: string;
  total: number;
  currency: string;
  createdAt: string;
  itemCount: number;
}

export interface Address {
  addressId?: number;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  locality: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

export type MockOutcome = 'SUCCESS' | 'FAILURE' | 'CANCEL';
