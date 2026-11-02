import { exportFileName } from './order-export';

describe('exportFileName', () => {
  const day = new Date('2026-11-01T10:00:00Z');

  it('uses the date when no status filter is applied', () => {
    expect(exportFileName('', day)).toBe('orders-2026-11-01.csv');
  });

  it('adds a safe status segment', () => {
    expect(exportFileName('SHIPPED', day)).toBe('orders-shipped-2026-11-01.csv');
    expect(exportFileName('PENDING_PAYMENT', day)).toBe('orders-pending-payment-2026-11-01.csv');
  });

  it('never lets odd input into the file name', () => {
    expect(exportFileName('../x"y', day)).toBe('orders--x-y-2026-11-01.csv');
  });
});
