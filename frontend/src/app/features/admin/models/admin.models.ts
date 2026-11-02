import { ImageRef } from '../../../shared/models/api.models';

export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface ErrorRow {
  eventId: number;
  correlationId: string;
  severity: string;
  occurredAt: string;
  lastOccurredAt: string;
  occurrenceCount: number;
  operation: string;
  route: string;
  errorType: string;
  summary: string;
  notificationState: string;
  remediationState: string;
}

export interface DashboardSummary {
  timezone: string;
  orderCountsByStatus: Record<string, number>;
  grossOrderValue: number;
  productCount: number;
  lowStockCount: number;
  openErrorCount: number;
  recentErrors: ErrorRow[];
  interactions: { from: string; to: string; views: number; wishlistAdds: number; cartAdds: number };
}

export interface AdminProductRow {
  productId: number;
  sku: string;
  title: string;
  slug: string;
  price: number;
  salePrice: number | null;
  status: ProductStatus;
  categoryName: string;
  onHand: number;
  reserved: number;
  available: number;
  primaryImageUrl: string | null;
  updatedAt: string;
}

export interface AttributeValueInput {
  code: string;
  valueCode?: string | null;
  textValue?: string | null;
}

export interface ProductUpsert {
  sku: string;
  title: string;
  slug?: string | null;
  description: string;
  categoryId: number | null;
  basePrice: number | null;
  salePrice: number | null;
  currency?: string | null;
  seoTitle?: string | null;
  seoDescription?: string | null;
  lowStockThreshold?: number | null;
  initialStock?: number | null;
  version?: number | null;
  featured?: boolean | null;
  attributes: AttributeValueInput[];
}

export interface AdminProduct extends ProductUpsert {
  productId: number;
  status: ProductStatus;
  images: ImageRef[];
  onHand: number;
  reserved: number;
  available: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCategory {
  categoryId: number;
  parentCategoryId: number | null;
  name: string;
  slug: string;
  description: string;
  active: boolean;
  sortOrder: number;
  productCount: number;
}

export interface CategoryUpsert {
  parentCategoryId: number | null;
  name: string;
  slug?: string | null;
  description: string;
  active: boolean;
  sortOrder: number;
}

export interface LookupGroup {
  groupCode: string;
  label: string;
  valueCount: number;
}

export interface LookupValue {
  lovId: number;
  valueCode: string;
  displayLabel: string;
  active: boolean;
  sortOrder: number;
}

export interface AttributeDefinition {
  code: string;
  label: string;
  lovGroupCode: string | null;
  filterable: boolean;
}

export interface InventoryRow {
  productId: number;
  sku: string;
  title: string;
  onHand: number;
  reserved: number;
  available: number;
  lowStockThreshold: number;
  low: boolean;
}

export interface InventoryMovement {
  movementId: number;
  quantityDelta: number;
  reason: string;
  referenceType: string | null;
  referenceId: string | null;
  actor: string;
  createdAt: string;
}

export type ImportMode = 'CREATE' | 'UPDATE';

export interface ImportRowError {
  column: string;
  message: string;
}

export interface ImportRow {
  rowNumber: number;
  sku: string;
  valid: boolean;
  result: string;
  errors: ImportRowError[];
}

export interface ImportJob {
  jobId: number;
  status: 'PREVIEWED' | 'COMMITTED' | 'FAILED';
  mode: ImportMode;
  allowPartial: boolean;
  filename: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  committedRows: number;
  createdAt: string;
  rows: ImportRow[];
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

export interface Shipment {
  shipmentId: number;
  carrier: string | null;
  trackingNumber: string | null;
  status: string;
  shippedAt: string | null;
  deliveredAt: string | null;
  trackingNote: string | null;
}

export interface AdminOrderRow {
  orderId: number;
  orderNumber: string;
  customerName: string;
  status: string;
  paymentMethod: string;
  paymentStatus: string;
  total: number;
  createdAt: string;
}

export interface AdminOrderDetail {
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
  items: {
    orderItemId: number;
    productId: number | null;
    sku: string;
    title: string;
    unitPrice: number;
    quantity: number;
    lineTotal: number;
    imageUrl: string | null;
  }[];
  shipments: Shipment[];
  statusHistory: { status: string; at: string }[];
  customer: { name: string; email: string; phone: string };
  allowedTransitions: string[];
  packingNotes: string | null;
}

export interface ShipmentInput {
  carrier?: string | null;
  trackingNumber?: string | null;
  status?: string | null;
  trackingNote?: string | null;
}

export interface AnalyticsTotals {
  views: number;
  wishlistAdds: number;
  cartAdds: number;
}

export interface AnalyticsResponse {
  from: string;
  to: string;
  timezone: string;
  totals: AnalyticsTotals;
  products: { productId: number; sku: string; title: string; views: number; wishlistAdds: number; cartAdds: number }[];
  daily: { date: string; views: number; wishlistAdds: number; cartAdds: number }[];
}

export interface ShopProfile {
  displayName: string;
  tagline: string;
  logoUrl: string;
  supportEmail: string;
  supportPhone: string;
  senderDisplayName: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  returnAddress: string;
  handlingTimeDays: number;
}

export interface SiteSetting {
  key: string;
  value: string;
  group: string;
  type: string;
  description: string;
  options: string[] | null;
}

export interface SiteLink {
  placement: 'HEADER' | 'FOOTER';
  label: string;
  url: string;
  sortOrder: number;
  active: boolean;
}

export type BlockType = 'HERO' | 'CATEGORY_TILES' | 'FEATURED_PRODUCTS' | 'PROMO_STRIP' | 'TEXT_IMAGE';
export type ProductRule = 'NEWEST' | 'FEATURED' | 'BEST_SELLERS' | 'CATEGORY';

export interface AdminBlock {
  blockId?: number | null;
  type: BlockType;
  title: string;
  subtitle: string;
  body: string;
  imageUrl: string;
  linkUrl: string;
  ctaLabel: string;
  productRule: ProductRule | null;
  categoryId: number | null;
  itemLimit: number | null;
  sortOrder: number;
  published: boolean;
}

export interface ContentPage {
  pageId: number;
  slug: string;
  title: string;
  published: boolean;
  blocks: AdminBlock[];
}

export interface ErrorDetail extends ErrorRow {
  [extra: string]: unknown;
}

export interface AuditRow {
  auditId: number;
  actor: string;
  action: string;
  targetType: string;
  targetId: string;
  summary: string;
  outcome: string;
  correlationId: string;
  createdAt: string;
}

export interface AdminUserRow {
  userId: number;
  fullName: string;
  username: string;
  email: string;
  status: string;
  roles: string[];
  createdAt: string;
}

export const ORDER_STATUSES = ['PENDING_PAYMENT', 'PLACED', 'CONFIRMED', 'PACKING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
export const SHIPMENT_STATUSES = ['PENDING', 'SHIPPED', 'DELIVERED'];
export const BLOCK_TYPES: BlockType[] = ['HERO', 'CATEGORY_TILES', 'FEATURED_PRODUCTS', 'PROMO_STRIP', 'TEXT_IMAGE'];
export const PRODUCT_RULES: ProductRule[] = ['NEWEST', 'FEATURED', 'BEST_SELLERS', 'CATEGORY'];
