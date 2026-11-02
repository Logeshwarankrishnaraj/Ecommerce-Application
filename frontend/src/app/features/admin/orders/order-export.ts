/** File name for the orders CSV download, e.g. orders-2026-11-01.csv or orders-shipped-2026-11-01.csv. */
export function exportFileName(status: string, now: Date = new Date()): string {
  const day = now.toISOString().slice(0, 10);
  const part = status ? `-${status.toLowerCase().replace(/[^a-z0-9]+/g, '-')}` : '';
  return `orders${part}-${day}.csv`;
}
