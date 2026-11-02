import { Cart, CartItem, ProductCard, ProductDetail } from '../../../shared/models/storefront.models';

/**
 * A signed-out visitor's cart. It lives in this browser only (localStorage) and is merged into the customer's real cart
 * at sign-in. Prices and stock stored here are for display; the server re-checks everything on the merge and at checkout.
 */
export interface GuestLine {
  productId: number;
  slug: string;
  title: string;
  sku: string;
  imageUrl: string | null;
  unitPrice: number;
  currency: string;
  quantity: number;
  availableQuantity: number;
  issue: string | null;
}

export const GUEST_CART_KEY = 'jw.guestCart.v1';
export const MAX_GUEST_LINES = 30;
const FALLBACK_AVAILABLE = 10;

export interface GuestResult {
  lines: GuestLine[];
  error: string | null;
}

/** Reads stored lines defensively: anything malformed or tampered with is dropped, never thrown. */
export function parseGuestLines(raw: string | null): GuestLine[] {
  if (!raw) return [];
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(data)) return [];
  const out: GuestLine[] = [];
  const seen = new Set<number>();
  for (const d of data) {
    if (!d || typeof d !== 'object') continue;
    const o = d as Record<string, unknown>;
    const productId = Number(o['productId']);
    const quantity = Math.floor(Number(o['quantity']));
    const unitPrice = Number(o['unitPrice']);
    if (!Number.isInteger(productId) || productId <= 0 || seen.has(productId)) continue;
    if (!Number.isFinite(quantity) || quantity < 1) continue;
    if (!Number.isFinite(unitPrice) || unitPrice < 0) continue;
    if (typeof o['slug'] !== 'string' || !o['slug'] || typeof o['title'] !== 'string') continue;
    const image = typeof o['imageUrl'] === 'string' && (o['imageUrl'] as string).startsWith('/media/') ? (o['imageUrl'] as string) : null;
    const available = Math.floor(Number(o['availableQuantity']));
    seen.add(productId);
    out.push({
      productId,
      slug: o['slug'] as string,
      title: (o['title'] as string).slice(0, 200),
      sku: typeof o['sku'] === 'string' ? (o['sku'] as string).slice(0, 80) : '',
      imageUrl: image,
      unitPrice,
      currency: typeof o['currency'] === 'string' && o['currency'] ? (o['currency'] as string).slice(0, 8) : 'INR',
      quantity: Math.min(quantity, 100),
      availableQuantity: Number.isFinite(available) && available >= 0 ? available : FALLBACK_AVAILABLE,
      issue: typeof o['issue'] === 'string' ? (o['issue'] as string).slice(0, 120) : null,
    });
    if (out.length >= MAX_GUEST_LINES) break;
  }
  return out;
}

/** Builds a cart line from a product card or detail. Cards do not know exact stock, so it is refreshed on the cart page. */
export function toGuestLine(p: ProductCard, quantity: number): GuestLine {
  const detail = p as Partial<ProductDetail>;
  const img = p.primaryImage;
  return {
    productId: p.productId,
    slug: p.slug,
    title: p.title,
    sku: p.sku,
    imageUrl: img ? img.thumbUrl || img.url : null,
    unitPrice: p.effectivePrice,
    currency: p.currency,
    quantity,
    availableQuantity: typeof detail.availableQuantity === 'number' ? detail.availableQuantity : p.inStock ? FALLBACK_AVAILABLE : 0,
    issue: null,
  };
}

/** Adds to the cart (same rules and wording as the server: per-item maximum and stock). */
export function addToGuestLines(lines: GuestLine[], incoming: GuestLine, maxQty: number): GuestResult {
  const fail = (error: string): GuestResult => ({ lines, error });
  if (!Number.isInteger(incoming.quantity) || incoming.quantity < 1 || incoming.quantity > maxQty) return fail(`Quantity must be between 1 and ${maxQty}.`);
  const existing = lines.find((l) => l.productId === incoming.productId);
  const target = (existing?.quantity ?? 0) + incoming.quantity;
  if (target > maxQty) return fail(`You can add at most ${maxQty} of one item.`);
  if (incoming.availableQuantity <= 0) return fail('This item is out of stock.');
  if (target > incoming.availableQuantity) return fail(`Only ${incoming.availableQuantity} left in stock.`);
  if (!existing && lines.length >= MAX_GUEST_LINES) return fail('Your cart is full. Sign in or remove an item to add more.');
  const merged: GuestLine = { ...incoming, quantity: target, issue: null };
  return { lines: existing ? lines.map((l) => (l.productId === incoming.productId ? merged : l)) : [...lines, merged], error: null };
}

export function setGuestQuantity(lines: GuestLine[], productId: number, quantity: number, maxQty: number): GuestResult {
  const line = lines.find((l) => l.productId === productId);
  if (!line) return { lines, error: 'That item is no longer in your cart.' };
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxQty) return { lines, error: `Quantity must be between 1 and ${maxQty}.` };
  if (line.availableQuantity <= 0) return { lines, error: 'This item is out of stock.' };
  if (quantity > line.availableQuantity) return { lines, error: `Only ${line.availableQuantity} left in stock.` };
  return { lines: lines.map((l) => (l.productId === productId ? { ...l, quantity, issue: null } : l)), error: null };
}

export function removeGuestLine(lines: GuestLine[], productId: number): GuestLine[] {
  return lines.filter((l) => l.productId !== productId);
}

/** Applies fresh public product data to a line. `null` means the product no longer exists. Wording matches the server cart. */
export function applyFresh(line: GuestLine, fresh: ProductDetail | null): GuestLine {
  if (!fresh) return { ...line, availableQuantity: 0, issue: 'No longer available' };
  const available = fresh.stockStatus === 'OUT_OF_STOCK' ? 0 : Math.max(fresh.availableQuantity, 0);
  const img = fresh.images?.[0] ?? fresh.primaryImage;
  const base: GuestLine = {
    ...line,
    slug: fresh.slug,
    title: fresh.title,
    sku: fresh.sku,
    unitPrice: fresh.effectivePrice,
    currency: fresh.currency,
    imageUrl: img ? img.thumbUrl || img.url : line.imageUrl,
    availableQuantity: available,
    issue: null,
  };
  if (available <= 0) return { ...base, issue: 'Out of stock' };
  if (available < line.quantity) return { ...base, issue: `Only ${available} left` };
  return base;
}

/** Shapes the guest cart like the server's cart so the cart page and header badge need no special cases. cartItemId = productId. */
export function guestToCart(lines: GuestLine[]): Cart {
  const round = (n: number) => Math.round(n * 100) / 100;
  const items: CartItem[] = lines.map((l) => ({
    cartItemId: l.productId,
    productId: l.productId,
    slug: l.slug,
    title: l.title,
    sku: l.sku,
    imageUrl: l.imageUrl,
    unitPrice: l.unitPrice,
    quantity: l.quantity,
    lineTotal: round(l.unitPrice * l.quantity),
    available: l.issue === null,
    availableQuantity: Math.min(l.availableQuantity, FALLBACK_AVAILABLE),
    issue: l.issue,
  }));
  return {
    items,
    subtotal: round(items.filter((i) => i.available).reduce((sum, i) => sum + i.lineTotal, 0)),
    itemCount: items.reduce((sum, i) => sum + i.quantity, 0),
    hasIssues: items.some((i) => !i.available),
    currency: lines[0]?.currency ?? 'INR',
  };
}

/**
 * What to send to the account cart at sign-in. The server adds to what is already there and rejects totals above the
 * per-item maximum, so each line is trimmed to the room left.
 */
export function planMerge(
  guest: GuestLine[],
  server: { productId: number; quantity: number }[],
  maxQty: number,
): { productId: number; quantity: number }[] {
  const have = new Map(server.map((s) => [s.productId, s.quantity]));
  const plan: { productId: number; quantity: number }[] = [];
  for (const g of guest) {
    const quantity = Math.min(g.quantity, maxQty - (have.get(g.productId) ?? 0));
    if (quantity >= 1) plan.push({ productId: g.productId, quantity });
  }
  return plan;
}

/** localStorage wrapper that never throws (private windows and blocked storage fall back to an in-memory cart). */
export const guestCartStorage = {
  read(): GuestLine[] {
    try {
      return parseGuestLines(localStorage.getItem(GUEST_CART_KEY));
    } catch {
      return [];
    }
  },
  write(lines: GuestLine[]): void {
    try {
      if (lines.length === 0) localStorage.removeItem(GUEST_CART_KEY);
      else localStorage.setItem(GUEST_CART_KEY, JSON.stringify(lines));
    } catch {
      /* storage unavailable: the cart still works for this tab */
    }
  },
};
