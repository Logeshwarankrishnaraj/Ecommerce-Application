import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { Page } from '../../../shared/models/api.models';
import { CategoryNode, FiltersResponse, ProductCard } from '../../../shared/models/storefront.models';
import { errorMessage } from '../../../shared/util/api-error.util';
import { InfoService } from '../../../core/info/info.service';
import { CartService } from '../../cart/services/cart.service';
import { toGuestLine } from '../../cart/services/guest-cart';
import { WishlistActions } from '../../wishlist/services/wishlist-actions.service';
import { WishlistService } from '../../wishlist/services/wishlist.service';
import { ProductApiService } from '../services/product-api.service';

/** URL is the single source of truth for filters, so results are shareable and back-button safe. */
@Component({
  selector: 'app-product-list',
  imports: [FormsModule, RouterLink, ProductCardComponent, EmptyStateComponent],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.css',
})
export class ProductListComponent {
  private readonly api = inject(ProductApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly actions = inject(WishlistActions);
  private readonly cart = inject(CartService);
  private readonly popup = inject(InfoService);
  /** Product currently being added from its card (blocks double clicks). */
  protected readonly addingId = signal<number | null>(null);
  protected readonly wishlist = inject(WishlistService);
  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });

  readonly result = signal<Page<ProductCard> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly filters = signal<FiltersResponse | null>(null);
  readonly flatCategories = signal<(CategoryNode & { depth: number })[]>([]);
  /** Mobile filter drawer. */
  readonly filtersOpen = signal(false);
  protected readonly skeletons = Array.from({ length: 8 }, (_, i) => i);
  protected readonly sortOptions = [
    { value: 'newest', label: 'Newest first' }, { value: 'price_asc', label: 'Price: low to high' },
    { value: 'price_desc', label: 'Price: high to low' }, { value: 'title', label: 'Name A–Z' },
  ];
  minPrice: number | null = null;
  maxPrice: number | null = null;
  private tick = signal(0);

  readonly q = computed(() => this.params().get('q') ?? '');
  readonly category = computed(() => this.params().get('category') ?? '');
  readonly sort = computed(() => this.params().get('sort') ?? 'newest');
  readonly inStock = computed(() => this.params().get('inStock') === 'true');
  readonly page = computed(() => Math.max(0, Number(this.params().get('page') ?? 0) || 0));
  readonly attrs = computed(() => this.params().getAll('attr'));
  readonly categoryName = computed(() => this.flatCategories().find((c) => c.slug === this.category())?.name ?? null);
  readonly heading = computed(() => (this.q() ? `Results for “${this.q()}”` : this.categoryName() ?? 'Shop'));
  readonly minP = computed(() => (this.params().get('minPrice') ? Number(this.params().get('minPrice')) : null));
  readonly maxP = computed(() => (this.params().get('maxPrice') ? Number(this.params().get('maxPrice')) : null));

  /** Removable chips that summarise every active filter, so the user always sees why the list looks the way it does. */
  readonly chips = computed(() => {
    const out: { label: string; remove: () => void }[] = [];
    if (this.q()) out.push({ label: `Search: ${this.q()}`, remove: () => this.set({ q: null }) });
    if (this.categoryName()) out.push({ label: this.categoryName()!, remove: () => this.set({ category: null }) });
    const min = this.minP(), max = this.maxP();
    if (min || max) out.push({ label: min && max ? `${this.fmt(min)} – ${this.fmt(max)}` : min ? `From ${this.fmt(min)}` : `Up to ${this.fmt(max!)}`, remove: () => this.set({ minPrice: null, maxPrice: null }) });
    if (this.inStock()) out.push({ label: 'In stock', remove: () => this.set({ inStock: null }) });
    for (const a of this.attrs()) {
      const [code, value] = a.split(':');
      const attr = this.filters()?.attributes.find((x) => x.code === code);
      const label = attr?.values.find((v) => v.valueCode === value)?.displayLabel ?? value;
      out.push({ label: attr ? `${attr.label}: ${label}` : label, remove: () => this.toggleAttr(code, value) });
    }
    return out;
  });

  /** Page numbers with ellipses, e.g. 1 … 4 5 6 … 12. */
  readonly pageList = computed(() => {
    const total = this.result()?.totalPages ?? 0, cur = this.page();
    const nums = new Set([0, total - 1, cur - 1, cur, cur + 1].filter((n) => n >= 0 && n < total));
    const sorted = [...nums].sort((a, b) => a - b);
    const out: (number | '…')[] = [];
    sorted.forEach((n, i) => { if (i > 0 && n - sorted[i - 1] > 1) out.push('…'); out.push(n); });
    return out;
  });

  constructor() {
    this.api.filters().subscribe({ next: (f) => this.filters.set(f), error: () => undefined });
    this.api.categories().subscribe({ next: (c) => this.flatCategories.set(this.flatten(c)), error: () => undefined });
    effect(() => {
      const p = this.params();
      this.tick();
      this.minPrice = p.get('minPrice') ? Number(p.get('minPrice')) : null;
      this.maxPrice = p.get('maxPrice') ? Number(p.get('maxPrice')) : null;
      this.loading.set(true);
      this.error.set(null);
      this.api
        .list({
          q: this.q(), category: this.category(), sort: this.sort(), inStock: this.inStock(), attr: this.attrs(), page: this.page(),
          minPrice: this.minPrice, maxPrice: this.maxPrice, size: 12,
        })
        .subscribe({
          next: (r) => { this.result.set(r); this.loading.set(false); },
          error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
        });
    });
  }

  private flatten(nodes: CategoryNode[], out: (CategoryNode & { depth: number })[] = [], depth = 0): (CategoryNode & { depth: number })[] {
    for (const n of nodes) { out.push({ ...n, depth }); this.flatten(n.children ?? [], out, depth + 1); }
    return out;
  }

  private fmt(n: number): string {
    const cur = this.result()?.content[0]?.currency ?? 'INR';
    try { return new Intl.NumberFormat(undefined, { style: 'currency', currency: cur, maximumFractionDigits: 0 }).format(n); } catch { return String(n); }
  }

  reload(): void { this.tick.update((t) => t + 1); }

  set(patch: Record<string, string | number | null>): void {
    const resetPage = !('page' in patch);
    if ('page' in patch && typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...patch, ...(resetPage ? { page: null } : {}) },
      queryParamsHandling: 'merge',
    });
  }

  applyPrice(): void {
    this.set({ minPrice: this.minPrice && this.minPrice > 0 ? this.minPrice : null, maxPrice: this.maxPrice && this.maxPrice > 0 ? this.maxPrice : null });
  }

  hasAttr(code: string, value: string): boolean { return this.attrs().includes(`${code}:${value}`); }

  toggleAttr(code: string, value: string): void {
    const key = `${code}:${value}`;
    const next = this.hasAttr(code, value) ? this.attrs().filter((a) => a !== key) : [...this.attrs(), key];
    void this.router.navigate([], { relativeTo: this.route, queryParams: { attr: next.length ? next : null, page: null }, queryParamsHandling: 'merge' });
  }

  clearAll(): void { void this.router.navigate([], { relativeTo: this.route, queryParams: {} }); }

  toggleFilters(open?: boolean): void { this.filtersOpen.set(open ?? !this.filtersOpen()); }

  toggleWish(p: ProductCard): void { this.actions.toggle(p.productId); }

  quickAdd(p: ProductCard): void {
    if (this.addingId() !== null) return;
    this.addingId.set(p.productId);
    this.cart.add(p.productId, 1, toGuestLine(p, 1)).subscribe({
      next: () => { this.addingId.set(null); this.popup.success(`${p.title} was added to your cart.`); },
      error: (e) => { this.addingId.set(null); this.popup.error(errorMessage(e)); },
    });
  }
}
