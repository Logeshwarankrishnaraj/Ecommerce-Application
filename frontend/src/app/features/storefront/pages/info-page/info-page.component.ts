import { Component, effect, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../../shared/components/loading-state/loading-state.component';
import { Block } from '../../../../shared/models/storefront.models';
import { toApiError, errorMessage } from '../../../../shared/util/api-error.util';
import { BlockViewComponent } from '../../components/block-view/block-view.component';
import { StorefrontApiService } from '../../services/storefront-api.service';

/** Informational pages (About, Shipping, Returns, Privacy, Terms…) from admin-managed content blocks. */
@Component({
  selector: 'app-info-page',
  imports: [BlockViewComponent, LoadingStateComponent, EmptyStateComponent],
  templateUrl: './info-page.component.html',
  styleUrl: './info-page.component.css',
})
export class InfoPageComponent {
  readonly slug = input.required<string>();
  private readonly api = inject(StorefrontApiService);
  private readonly titleService = inject(Title);

  readonly title = signal('');
  readonly blocks = signal<Block[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notFound = signal(false);

  constructor() {
    effect(() => this.load(this.slug()));
  }

  load(slug: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.notFound.set(false);
    this.api.page(slug).subscribe({
      next: (p) => {
        this.title.set(p.title);
        this.blocks.set(p.blocks);
        this.titleService.setTitle(p.title);
        this.loading.set(false);
      },
      error: (e) => {
        if (toApiError(e).status === 404) this.notFound.set(true);
        else this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  retry(): void {
    this.load(this.slug());
  }
}
