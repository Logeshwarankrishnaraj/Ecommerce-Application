import { Component, inject, signal } from '@angular/core';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { errorMessage } from '../../../shared/util/api-error.util';
import { AuthService } from '../../../core/auth/auth.service';
import { CartService } from '../services/cart.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, IconComponent, MoneyPipe, EmptyStateComponent, LoadingStateComponent],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.css',
})
export class CartComponent {
  private readonly popup = inject(InfoService);
  protected readonly cartSvc = inject(CartService);
  protected readonly auth = inject(AuthService);
  readonly error = signal<string | null>(null);

  constructor() {
    this.cartSvc.load().subscribe({ error: (e) => this.popup.error(errorMessage(e)) });
  }

  setQty(id: number, ev: Event): void {
    const n = Math.floor(Number((ev.target as HTMLInputElement).value));
    if (!Number.isFinite(n) || n < 1) { this.cartSvc.load().subscribe(); return; }
    this.error.set(null);
    this.cartSvc.updateQuantity(id, n).subscribe({ error: (e) => { this.popup.error(errorMessage(e)); this.cartSvc.load().subscribe(); } });
  }

  step(id: number, quantity: number, delta: number, max: number): void {
    const n = Math.min(Math.max(quantity + delta, 1), Math.max(max, 1));
    if (n === quantity) return;
    this.cartSvc.updateQuantity(id, n).subscribe({ error: (e) => { this.popup.error(errorMessage(e)); this.cartSvc.load().subscribe(); } });
  }

  remove(id: number): void {
    this.error.set(null);
    this.cartSvc.remove(id).subscribe({ error: (e) => this.popup.error(errorMessage(e)) });
  }
}
