import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { statusLabel } from '../../account/order-status';
import { AdminOrderDetail, SHIPMENT_STATUSES, Shipment } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-order-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, StateComponent],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.css',
})
export class OrderDetailAdminComponent {
  readonly id = input.required<string>();
  private readonly api = inject(AdminApiService);
  private readonly notice = inject(NoticeService);
  private readonly confirm = inject(ConfirmService);
  protected readonly o = signal<AdminOrderDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly label = statusLabel;
  protected readonly shipmentStatuses = SHIPMENT_STATUSES;
  protected note = '';
  protected notes = '';
  protected ns = { carrier: '', trackingNumber: '', trackingNote: '' };

  constructor() { effect(() => this.load(this.id())); }

  protected load(id: string): void {
    this.loading.set(true); this.error.set(null);
    this.api.getOrder(Number(id)).subscribe({ next: (d) => { this.apply(d); this.loading.set(false); }, error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); } });
  }
  private apply(d: AdminOrderDetail): void { this.o.set(d); this.notes = d.packingNotes ?? ''; }
  private done(msg: string): { next: (d: AdminOrderDetail) => void; error: (e: unknown) => void } {
    return { next: (d) => { this.busy.set(false); this.apply(d); this.note = ''; this.notice.success(msg); }, error: (e) => { this.busy.set(false); this.notice.error(describeError(toApiError(e))); } };
  }
  protected async transition(o: AdminOrderDetail, to: string): Promise<void> {
    const cancel = to === 'CANCELLED';
    const ok = await this.confirm.ask({ title: cancel ? 'Cancel this order?' : 'Update order status?', subject: o.orderNumber,
      message: cancel ? 'Stock is returned and any online payment is refunded. This cannot be undone.' : `Move the order to ${statusLabel(to)}.`, confirmLabel: cancel ? 'Cancel order' : 'Confirm', danger: cancel });
    if (!ok) return;
    this.busy.set(true);
    this.api.changeOrderStatus(o.orderId, to, this.note).subscribe(this.done('Order updated.'));
  }
  protected addShipment(): void {
    const o = this.o(); if (!o) return;
    if (!this.ns.carrier.trim() || !this.ns.trackingNumber.trim()) { this.notice.error('Carrier and tracking number are required.'); return; }
    this.busy.set(true);
    this.api.createShipment(o.orderId, { carrier: this.ns.carrier.trim(), trackingNumber: this.ns.trackingNumber.trim(), trackingNote: this.ns.trackingNote.trim() || null }).subscribe({
      ...this.done('Shipment added.'), next: (d) => { this.busy.set(false); this.apply(d); this.ns = { carrier: '', trackingNumber: '', trackingNote: '' }; this.notice.success('Shipment added.'); } });
  }
  protected edit(s: Shipment, field: 'carrier' | 'trackingNumber', ev: Event): void {
    const v = (ev.target as HTMLInputElement).value.trim();
    if (!v) { this.load(this.id()); return; }
    this.api.updateShipment(s.shipmentId, { [field]: v }).subscribe({ next: (d) => this.apply(d), error: (e) => { this.notice.error(describeError(toApiError(e))); this.load(this.id()); } });
  }
  protected setStatus(s: Shipment, ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.busy.set(true);
    this.api.updateShipment(s.shipmentId, { status: v }).subscribe({ next: (d) => { this.busy.set(false); this.apply(d); this.notice.success('Shipment updated.'); }, error: (e) => { this.busy.set(false); this.notice.error(describeError(toApiError(e))); this.load(this.id()); } });
  }
  protected saveNotes(): void {
    const o = this.o(); if (!o) return;
    this.busy.set(true);
    this.api.savePackingNotes(o.orderId, this.notes).subscribe(this.done('Notes saved.'));
  }
}
