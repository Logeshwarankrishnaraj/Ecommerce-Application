import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SiteConfigService } from '../../../core/site-config.service';
import { AdminBlock, AdminCategory, BLOCK_TYPES, ContentPage, PRODUCT_RULES, SiteLink } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { StateComponent } from '../shared/state.component';
import { InfoService } from '../../../core/info/info.service';

/** Edits header/footer links and CMS pages made of typed blocks (plain text only; the server rejects markup). */
@Component({
  selector: 'admin-site-builder',
  standalone: true,
  imports: [FormsModule, StateComponent],
  templateUrl: './site-builder.component.html',
  styleUrl: './site-builder.component.css',
})
export class SiteBuilderComponent implements OnInit {
  private readonly popup = inject(InfoService);
  private readonly api = inject(AdminApiService);
  private readonly notice = inject(NoticeService);
  private readonly site = inject(SiteConfigService);
  protected readonly blockTypes = BLOCK_TYPES;
  protected readonly rules = PRODUCT_RULES;
  protected readonly links = signal<SiteLink[]>([]);
  protected readonly pages = signal<ContentPage[]>([]);
  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly pageId = signal<number | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly linksError = signal<string | null>(null);
  protected readonly pageError = signal<string | null>(null);
  protected newTitle = '';

  ngOnInit(): void { this.load(); }
  protected current(): ContentPage | undefined { return this.pages().find((p) => p.pageId === this.pageId()); }
  protected selectPage(id: number): void { this.pageId.set(id); this.pageError.set(null); }

  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listSiteLinks().subscribe({
      next: (l) => {
        this.links.set(l);
        this.api.listCategories().subscribe({ next: (c) => this.categories.set(c), error: () => undefined });
        this.api.listContentPages().subscribe({
          next: (p) => { this.pages.set(p); if (this.pageId() === null && p.length) this.pageId.set((p.find((x) => x.slug === 'home') ?? p[0]).pageId); this.loading.set(false); },
          error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
        });
      },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }

  protected addLink(): void { this.links.update((l) => [...l, { placement: 'HEADER', label: '', url: '/', sortOrder: l.length + 1, active: true }]); }
  protected removeLink(i: number): void { this.links.update((l) => l.filter((_, x) => x !== i)); }
  protected saveLinks(): void {
    this.busy.set(true); this.linksError.set(null);
    this.api.saveSiteLinks(this.links()).subscribe({
      next: (l) => { this.busy.set(false); this.links.set(l); this.notice.success('Links saved.'); this.site.load().subscribe(); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }

  protected createPage(): void {
    const title = this.newTitle.trim();
    if (!title) { this.notice.error('Enter a page title.'); return; }
    this.api.createContentPage({ slug: '', title, published: false }).subscribe({
      next: (p) => { this.newTitle = ''; this.pages.update((l) => [...l, p]); this.pageId.set(p.pageId); this.notice.success('Page created (unpublished).'); },
      error: (e) => this.notice.error(describeError(toApiError(e))),
    });
  }

  protected addBlock(): void {
    const p = this.current(); if (!p) return;
    p.blocks = [...p.blocks, { type: 'TEXT_IMAGE', title: '', subtitle: '', body: '', imageUrl: '', linkUrl: '', ctaLabel: '', productRule: null, categoryId: null, itemLimit: null, sortOrder: p.blocks.length + 1, published: true } as AdminBlock];
    this.pages.update((l) => [...l]);
  }
  protected removeBlock(i: number): void { const p = this.current(); if (!p) return; p.blocks = p.blocks.filter((_, x) => x !== i); this.pages.update((l) => [...l]); }
  protected move(i: number, d: number): void {
    const p = this.current(); if (!p) return;
    const b = [...p.blocks]; const j = i + d;
    if (j < 0 || j >= b.length) return;
    [b[i], b[j]] = [b[j], b[i]];
    p.blocks = b; this.pages.update((l) => [...l]);
  }

  protected savePage(): void {
    const p = this.current(); if (!p) return;
    this.busy.set(true); this.pageError.set(null);
    const blocks = p.blocks.map((b, i) => ({ ...b, sortOrder: i + 1, imageUrl: b.imageUrl || '', linkUrl: b.linkUrl || '',
      productRule: b.type === 'FEATURED_PRODUCTS' ? b.productRule ?? 'NEWEST' : null }));
    this.api.updateContentPage(p.pageId, { title: p.title, published: p.published, blocks }).subscribe({
      next: (u) => { this.busy.set(false); this.pages.update((l) => l.map((x) => (x.pageId === u.pageId ? u : x))); this.notice.success('Page saved.'); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }
}
