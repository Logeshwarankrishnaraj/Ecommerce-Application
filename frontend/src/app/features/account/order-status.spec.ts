import { statusClass, statusLabel } from './order-status';

describe('order status helpers', () => {
  it('humanises status codes', () => expect(statusLabel('PENDING_PAYMENT')).toBe('Pending payment'));
  it('handles empty values', () => expect(statusLabel(null)).toBe(''));
  it('maps outcomes to a visual class', () => {
    expect(statusClass('DELIVERED')).toBe('good');
    expect(statusClass('CANCELLED')).toBe('bad');
    expect(statusClass('PACKING')).toBe('warn');
  });
});
