import { describe, expect, it } from 'vitest';
import { ProductCard, ProductDetail } from '../../../shared/models/storefront.models';
import {
  GuestLine,
  MAX_GUEST_LINES,
  addToGuestLines,
  applyFresh,
  guestToCart,
  parseGuestLines,
  planMerge,
  removeGuestLine,
  setGuestQuantity,
  toGuestLine,
} from './guest-cart';

function line(over: Partial<GuestLine> = {}): GuestLine {
  return {
    productId: 1, slug: 'ring', title: 'Gold ring', sku: 'R-1', imageUrl: '/media/abc_t.jpg', unitPrice: 1000, currency: 'INR',
    quantity: 1, availableQuantity: 5, issue: null, ...over,
  };
}

describe('addToGuestLines', () => {
  it('adds a new line and sums repeats for the same product', () => {
    const one = addToGuestLines([], line({ quantity: 2 }), 10);
    expect(one.error).toBeNull();
    const two = addToGuestLines(one.lines, line({ quantity: 1 }), 10);
    expect(two.lines).toHaveLength(1);
    expect(two.lines[0].quantity).toBe(3);
  });

  it('refuses more than the per-item maximum, with the server wording', () => {
    const r = addToGuestLines([line({ quantity: 9 })], line({ quantity: 2 }), 10);
    expect(r.error).toBe('You can add at most 10 of one item.');
    expect(r.lines[0].quantity).toBe(9);
  });

  it('refuses more than the stock and out-of-stock items', () => {
    expect(addToGuestLines([], line({ quantity: 4, availableQuantity: 3 }), 10).error).toBe('Only 3 left in stock.');
    expect(addToGuestLines([], line({ availableQuantity: 0 }), 10).error).toBe('This item is out of stock.');
  });

  it('rejects invalid quantities', () => {
    expect(addToGuestLines([], line({ quantity: 0 }), 10).error).not.toBeNull();
    expect(addToGuestLines([], line({ quantity: 1.5 }), 10).error).not.toBeNull();
    expect(addToGuestLines([], line({ quantity: 11 }), 10).error).not.toBeNull();
  });

  it('caps the number of different items', () => {
    const full = Array.from({ length: MAX_GUEST_LINES }, (_, i) => line({ productId: i + 1 }));
    expect(addToGuestLines(full, line({ productId: 999 }), 10).error).toContain('full');
    expect(addToGuestLines(full, line({ productId: 1 }), 10).error).toBeNull();   // an existing item can still grow
  });
});

describe('setGuestQuantity / removeGuestLine', () => {
  it('updates within limits and rejects the rest', () => {
    const lines = [line({ quantity: 1, availableQuantity: 4 })];
    expect(setGuestQuantity(lines, 1, 3, 10).lines[0].quantity).toBe(3);
    expect(setGuestQuantity(lines, 1, 5, 10).error).toBe('Only 4 left in stock.');
    expect(setGuestQuantity(lines, 1, 0, 10).error).not.toBeNull();
    expect(setGuestQuantity(lines, 99, 1, 10).error).not.toBeNull();
  });

  it('removes only the chosen product', () => {
    expect(removeGuestLine([line({ productId: 1 }), line({ productId: 2 })], 1).map((l) => l.productId)).toEqual([2]);
  });
});

describe('guestToCart', () => {
  it('totals only purchasable lines and counts all units', () => {
    const cart = guestToCart([line({ quantity: 2 }), line({ productId: 2, unitPrice: 50.5, quantity: 3, issue: 'Out of stock', availableQuantity: 0 })]);
    expect(cart.itemCount).toBe(5);
    expect(cart.subtotal).toBe(2000);
    expect(cart.hasIssues).toBe(true);
    expect(cart.items[0].cartItemId).toBe(1);
    expect(cart.items[1].lineTotal).toBe(151.5);
  });

  it('is empty for no lines', () => {
    expect(guestToCart([])).toEqual({ items: [], subtotal: 0, itemCount: 0, hasIssues: false, currency: 'INR' });
  });
});

describe('applyFresh', () => {
  const fresh = (over: Partial<ProductDetail>): ProductDetail =>
    ({ productId: 1, slug: 'ring', sku: 'R-1', title: 'Gold ring', effectivePrice: 900, currency: 'INR', images: [], primaryImage: null, stockStatus: 'IN_STOCK', availableQuantity: 5, ...over }) as ProductDetail;

  it('takes the current price and clears the issue when stock is fine', () => {
    const r = applyFresh(line({ quantity: 2, issue: 'Out of stock' }), fresh({}));
    expect(r.unitPrice).toBe(900);
    expect(r.issue).toBeNull();
    expect(r.quantity).toBe(2);
  });

  it('flags low and missing stock using the server wording', () => {
    expect(applyFresh(line({ quantity: 4 }), fresh({ availableQuantity: 2 })).issue).toBe('Only 2 left');
    expect(applyFresh(line(), fresh({ stockStatus: 'OUT_OF_STOCK', availableQuantity: 0 })).issue).toBe('Out of stock');
  });

  it('marks a deleted product unavailable', () => {
    const r = applyFresh(line(), null);
    expect(r.issue).toBe('No longer available');
    expect(r.availableQuantity).toBe(0);
  });
});

describe('planMerge', () => {
  it('sends the full quantity when the account cart is empty', () => {
    expect(planMerge([line({ quantity: 3 })], [], 10)).toEqual([{ productId: 1, quantity: 3 }]);
  });

  it('trims to the room left under the per-item maximum', () => {
    expect(planMerge([line({ quantity: 5 })], [{ productId: 1, quantity: 8 }], 10)).toEqual([{ productId: 1, quantity: 2 }]);
  });

  it('skips items already at the maximum', () => {
    expect(planMerge([line({ quantity: 2 })], [{ productId: 1, quantity: 10 }], 10)).toEqual([]);
  });
});

describe('parseGuestLines', () => {
  it('round-trips valid data', () => {
    const lines = [line({ quantity: 2 })];
    expect(parseGuestLines(JSON.stringify(lines))).toEqual(lines);
  });

  it('survives garbage, wrong types and tampering', () => {
    expect(parseGuestLines(null)).toEqual([]);
    expect(parseGuestLines('not json')).toEqual([]);
    expect(parseGuestLines('{"a":1}')).toEqual([]);
    expect(parseGuestLines(JSON.stringify([null, 5, 'x', { productId: -1 }, { productId: 1, quantity: 0, unitPrice: 1, slug: 's', title: 't' }]))).toEqual([]);
  });

  it('drops duplicates, clamps quantity and only trusts /media/ image paths', () => {
    const out = parseGuestLines(JSON.stringify([
      line({ quantity: 500, imageUrl: 'javascript:alert(1)' }),
      line({ quantity: 2 }),
    ]));
    expect(out).toHaveLength(1);
    expect(out[0].quantity).toBe(100);
    expect(out[0].imageUrl).toBeNull();
  });
});

describe('toGuestLine', () => {
  const card = { productId: 7, slug: 's', sku: 'S', title: 'T', price: 10, salePrice: null, effectivePrice: 10, currency: 'INR', primaryImage: null, inStock: true, categoryName: null } as ProductCard;

  it('assumes stock for in-stock cards and none for sold-out ones', () => {
    expect(toGuestLine(card, 1).availableQuantity).toBeGreaterThan(0);
    expect(toGuestLine({ ...card, inStock: false }, 1).availableQuantity).toBe(0);
  });

  it('uses the exact stock when given a product detail', () => {
    expect(toGuestLine({ ...card, availableQuantity: 3 } as ProductDetail, 1).availableQuantity).toBe(3);
  });
});
