import { IconComponent } from '../../../shared/components/icon/icon.component';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ImageRef } from '../../../shared/models/api.models';
import {
  AdminCategory, AdminProduct, AttributeDefinition, LookupValue, ProductStatus, ProductUpsert,
} from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { applyFieldErrors, controlMessage, describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { StateComponent } from '../shared/state.component';
import { TooltipDirective } from '../shared/tooltip.directive';
import { InfoService } from '../../../core/info/info.service';
import {
  MAX_IMAGES, buildAttributes, canAddImage, moveItem, salePriceValidator, validateImageFile,
} from './product-editor.logic';

@Component({
  selector: 'admin-product-editor',
  standalone: true,
  imports: [IconComponent, ReactiveFormsModule, FormsModule, RouterLink, StateComponent, TooltipDirective, DatePipe],
  templateUrl: './product-editor.component.html',
  styleUrl: './product-editor.component.css',
})
export class ProductEditorComponent implements OnInit {
  private readonly popup = inject(InfoService);
  private readonly api = inject(AdminApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly confirm = inject(ConfirmService);
  private readonly notice = inject(NoticeService);

  protected readonly maxImages = MAX_IMAGES;
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly product = signal<AdminProduct | null>(null);
  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly definitions = signal<AttributeDefinition[]>([]);
  protected readonly lookupValues = signal<Record<string, LookupValue[]>>({});
  protected readonly attrValues = signal<Record<string, string>>({});
  protected readonly images = computed<ImageRef[]>(() => this.product()?.images ?? []);
  protected readonly isNew = computed(() => this.product() === null);

  // image manager state
  protected readonly imageBusy = signal(false);
  protected readonly imageError = signal<string | null>(null);
  protected pendingFile: File | null = null;
  protected pendingAlt = '';
  protected dragIndex: number | null = null;

  protected readonly form = new FormGroup(
    {
      sku: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
      title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(255)] }),
      slug: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)] }),
      description: new FormControl('', { nonNullable: true }),
      categoryId: new FormControl<number | null>(null, { validators: [Validators.required] }),
      basePrice: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
      salePrice: new FormControl<number | null>(null, { validators: [Validators.min(0)] }),
      currency: new FormControl('INR', { nonNullable: true, validators: [Validators.maxLength(3)] }),
      seoTitle: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
      seoDescription: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
      lowStockThreshold: new FormControl<number | null>(null, { validators: [Validators.min(0)] }),
      initialStock: new FormControl<number | null>(0, { validators: [Validators.min(0)] }),
      featured: new FormControl(false, { nonNullable: true }),
    },
    { validators: [salePriceValidator] },
  );

  protected msg(name: string, label: string): string | null {
    return controlMessage(this.form.get(name), label);
  }

  ngOnInit(): void { this.load(); }

  protected load(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.loading.set(true);
    this.loadError.set(null);
    const id = idParam && idParam !== 'new' ? Number(idParam) : null;
    forkJoin({
      categories: this.api.listCategories(),
      definitions: this.api.attributeDefinitions(),
    }).subscribe({
      next: ({ categories, definitions }) => {
        this.categories.set(categories);
        this.definitions.set(definitions);
        this.loadLookups(definitions);
        if (id !== null) {
          this.api.getProduct(id).subscribe({
            next: (p) => { this.applyProduct(p); this.loading.set(false); },
            error: (e) => { this.loadError.set(describeError(toApiError(e))); this.loading.set(false); },
          });
        } else {
          this.loading.set(false);
        }
      },
      error: (e) => { this.loadError.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }

  private loadLookups(defs: AttributeDefinition[]): void {
    const groups = [...new Set(defs.map((d) => d.lovGroupCode).filter((g): g is string => !!g))];
    for (const g of groups) {
      this.api.listLookupValues(g).subscribe({
        next: (vals) => this.lookupValues.update((m) => ({ ...m, [g]: vals })),
        error: () => undefined,
      });
    }
  }

  protected activeValues(group: string | null): LookupValue[] {
    if (!group) return [];
    const current = this.attrValues();
    return (this.lookupValues()[group] ?? []).filter(
      (v) => v.active || Object.values(current).includes(v.valueCode),
    );
  }

  protected setAttr(code: string, value: string): void {
    this.attrValues.update((m) => ({ ...m, [code]: value }));
  }

  private applyProduct(p: AdminProduct): void {
    this.product.set(p);
    this.form.patchValue({
      sku: p.sku, title: p.title, slug: p.slug ?? '', description: p.description ?? '', categoryId: p.categoryId,
      basePrice: p.basePrice, salePrice: p.salePrice, currency: p.currency ?? 'INR', seoTitle: p.seoTitle ?? '',
      seoDescription: p.seoDescription ?? '', lowStockThreshold: p.lowStockThreshold ?? null,
      featured: !!p.featured,
    });
    const map: Record<string, string> = {};
    for (const a of p.attributes ?? []) {
      map[a.code] = (a.valueCode ?? a.textValue ?? '') as string;
    }
    this.attrValues.set(map);
  }

  protected save(): void {
    this.formError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.saving()) return;
    const v = this.form.getRawValue();
    const lovCodes = new Set(this.definitions().filter((d) => !!d.lovGroupCode).map((d) => d.code));
    const body: ProductUpsert = {
      sku: v.sku.trim(), title: v.title.trim(), slug: v.slug.trim() || null, description: v.description,
      categoryId: v.categoryId, basePrice: v.basePrice, salePrice: v.salePrice ?? null, currency: v.currency || null,
      seoTitle: v.seoTitle || null, seoDescription: v.seoDescription || null, lowStockThreshold: v.lowStockThreshold, featured: v.featured,
      attributes: buildAttributes(this.attrValues(), lovCodes),
    };
    const existing = this.product();
    if (existing) {
      body.version = existing.version;
    } else {
      body.initialStock = v.initialStock ?? 0;
    }
    this.saving.set(true);
    const call = existing ? this.api.updateProduct(existing.productId, body) : this.api.createProduct(body);
    call.subscribe({
      next: (p) => {
        this.saving.set(false);
        this.notice.success(existing ? 'Product saved.' : 'Product created. You can now add images.');
        if (existing) {
          this.applyProduct(p);
        } else {
          void this.router.navigate(['/admin/products', p.productId]);
        }
      },
      error: (e) => {
        this.saving.set(false);
        const err = toApiError(e);
        const unmapped = applyFieldErrors(this.form, err);
        const conflict = err.status === 409 ? ' Reload the page to get the latest version before saving again.' : '';
        this.popup.error(describeError(err) + conflict + (unmapped.length ? ` — ${unmapped.join('; ')}` : ''));
      },
    });
  }

  protected async lifecycle(action: 'publish' | 'unpublish' | 'archive'): Promise<void> {
    const p = this.product();
    if (!p) return;
    const ok = await this.confirm.ask({
      title: `${action[0].toUpperCase()}${action.slice(1)} product?`, subject: `${p.sku} — ${p.title}`,
      message: action === 'publish' ? 'It will become visible in the public catalogue.' : 'It will no longer be visible in the public catalogue.',
      confirmLabel: action[0].toUpperCase() + action.slice(1), danger: action === 'archive',
    });
    if (!ok) return;
    this.api.productLifecycle(p.productId, action).subscribe({
      next: (u) => { this.applyProduct(u); this.notice.success(`Product ${action === 'publish' ? 'published' : action === 'unpublish' ? 'unpublished' : 'archived'}.`); },
      error: (e) => this.notice.error(describeError(toApiError(e))),
    });
  }

  protected statusClass(s: ProductStatus): string {
    return s === 'PUBLISHED' ? 'ok' : s === 'ARCHIVED' ? 'muted' : 'warn';
  }

  // ---------------- image manager ----------------
  protected onFileChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.pendingFile = input.files && input.files.length ? input.files[0] : null;
    this.imageError.set(null);
  }

  protected canUpload(): boolean {
    return !!this.pendingFile && !!this.pendingAlt.trim() && canAddImage(this.images().length) && !this.imageBusy();
  }

  protected uploadImage(fileInput: HTMLInputElement): void {
    const p = this.product();
    if (!p || !this.pendingFile) return;
    const problem = validateImageFile(this.pendingFile, this.pendingAlt, this.images().length);
    if (problem) { this.popup.error(problem); return; }
    this.imageBusy.set(true);
    this.api.uploadImage(p.productId, this.pendingFile, this.pendingAlt.trim()).subscribe({
      next: (u) => {
        this.product.set(u); this.imageBusy.set(false); this.pendingFile = null; this.pendingAlt = '';
        fileInput.value = ''; this.imageError.set(null); this.notice.success('Image uploaded.');
      },
      error: (e) => { this.imageBusy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }

  protected move(index: number, delta: number): void { this.reorder(index, index + delta); }

  protected reorder(from: number, to: number): void {
    const p = this.product();
    if (!p) return;
    const next = moveItem(this.images(), from, to);
    if (next.every((img, i) => img.imageId === this.images()[i].imageId)) return;
    this.imageBusy.set(true);
    this.api.reorderImages(p.productId, next.map((i) => i.imageId)).subscribe({
      next: (u) => { this.product.set(u); this.imageBusy.set(false); },
      error: (e) => { this.imageBusy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }

  protected dragStart(i: number): void { this.dragIndex = i; }
  protected dragOver(event: DragEvent): void { event.preventDefault(); }
  protected drop(i: number): void {
    if (this.dragIndex !== null) { this.reorder(this.dragIndex, i); }
    this.dragIndex = null;
  }

  protected makePrimary(img: ImageRef): void {
    const p = this.product();
    if (!p) return;
    this.api.updateImage(p.productId, img.imageId, { primary: true }).subscribe({
      next: (u) => this.product.set(u),
      error: (e) => this.popup.error(describeError(toApiError(e))),
    });
  }

  protected saveAlt(img: ImageRef, alt: string): void {
    const p = this.product();
    if (!p || alt === img.alt) return;
    if (!alt.trim()) { this.popup.error('Alt text is required for every image.'); return; }
    this.api.updateImage(p.productId, img.imageId, { altText: alt.trim() }).subscribe({
      next: (u) => { this.product.set(u); this.notice.success('Alt text saved.'); },
      error: (e) => this.popup.error(describeError(toApiError(e))),
    });
  }

  protected async deleteImage(img: ImageRef): Promise<void> {
    const p = this.product();
    if (!p) return;
    const ok = await this.confirm.ask({
      title: 'Delete image?', subject: img.alt || `Image #${img.imageId}`,
      message: 'The image is removed from the gallery. This cannot be undone.', confirmLabel: 'Delete', danger: true,
    });
    if (!ok) return;
    this.api.deleteImage(p.productId, img.imageId).subscribe({
      next: (u) => { this.product.set(u); this.notice.success('Image deleted.'); },
      error: (e) => this.popup.error(describeError(toApiError(e))),
    });
  }
}
