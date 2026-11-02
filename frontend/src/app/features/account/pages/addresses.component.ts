import { AccountNavComponent } from '../../../shared/components/account-nav/account-nav.component';
import { Component, inject, signal } from '@angular/core';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { Address } from '../../../shared/models/storefront.models';
import { errorMessage } from '../../../shared/util/api-error.util';
import { AddressFormComponent } from '../components/address-form.component';
import { AccountApiService } from '../services/account-api.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-addresses',
  imports: [AccountNavComponent, AddressFormComponent, EmptyStateComponent, LoadingStateComponent],
  templateUrl: './addresses.component.html',
  styleUrl: './addresses.component.css',
})
export class AddressesComponent {
  private readonly popup = inject(InfoService);
  private readonly api = inject(AccountApiService);
  readonly list = signal<Address[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly editing = signal<Address | 'new' | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.addresses().subscribe({
      next: (l) => { this.list.set(l); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  saved(): void { this.editing.set(null); this.load(); }

  remove(a: Address): void {
    if (!a.addressId || !confirm(`Delete the address for ${a.recipientName}?`)) return;
    this.error.set(null);
    this.api.deleteAddress(a.addressId).subscribe({ next: () => this.load(), error: (e) => this.popup.error(errorMessage(e)) });
  }
}
