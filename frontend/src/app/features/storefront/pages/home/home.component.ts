import { Component, inject, signal } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../../shared/components/loading-state/loading-state.component';
import { Block } from '../../../../shared/models/storefront.models';
import { errorMessage } from '../../../../shared/util/api-error.util';
import { BlockViewComponent } from '../../components/block-view/block-view.component';
import { StorefrontApiService } from '../../services/storefront-api.service';

@Component({
  selector: 'app-home',
  imports: [BlockViewComponent, LoadingStateComponent, EmptyStateComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private readonly api = inject(StorefrontApiService);
  readonly blocks = signal<Block[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.home().subscribe({
      next: (h) => {
        this.blocks.set(h.blocks);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
