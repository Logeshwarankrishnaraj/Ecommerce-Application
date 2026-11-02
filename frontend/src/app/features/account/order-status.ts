/** Display helpers shared by customer and admin order views. */
export function statusLabel(s: string | null | undefined): string {
  return (s ?? '').toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
}

export function statusClass(s: string | null | undefined): string {
  switch (s) {
    case 'DELIVERED': case 'PAID': case 'COLLECTED': case 'PLACED': case 'CONFIRMED': return 'good';
    case 'CANCELLED': case 'FAILED': case 'REFUNDED': return 'bad';
    case 'PENDING_PAYMENT': case 'INITIATED': case 'PENDING_COLLECTION': case 'PACKING': case 'SHIPPED': return 'warn';
    default: return '';
  }
}
