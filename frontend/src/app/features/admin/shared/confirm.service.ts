import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  /** Name of the affected record, shown prominently for destructive actions. */
  subject?: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
}

interface Pending extends ConfirmRequest {
  resolve: (value: boolean) => void;
}

/** Promise-based confirmation; rendered by <admin-confirm-dialog> inside the admin layout. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<Pending | null>(null);

  ask(request: ConfirmRequest): Promise<boolean> {
    return new Promise<boolean>((resolve) => this.pending.set({ ...request, resolve }));
  }

  answer(value: boolean): void {
    const current = this.pending();
    this.pending.set(null);
    current?.resolve(value);
  }
}
