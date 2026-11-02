import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ImportJob, ImportMode } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'admin-imports',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './imports.component.html',
  styleUrl: './imports.component.css',
})
export class ImportsComponent {
  private readonly popup = inject(InfoService);
  protected readonly api = inject(AdminApiService);
  private readonly notice = inject(NoticeService);
  protected readonly job = signal<ImportJob | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected csv: File | null = null;
  protected zip: File | null = null;
  protected mode: ImportMode = 'CREATE';
  protected allowPartial = false;

  protected pick(ev: Event): File | null { return (ev.target as HTMLInputElement).files?.[0] ?? null; }

  protected preview(): void {
    if (!this.csv) { this.popup.error('Choose a CSV file first.'); return; }
    this.busy.set(true); this.error.set(null); this.job.set(null);
    this.api.previewImport(this.csv, this.zip, this.mode, this.allowPartial).subscribe({
      next: (j) => { this.busy.set(false); this.job.set(j); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }

  protected commit(j: ImportJob): void {
    this.busy.set(true); this.error.set(null);
    this.api.commitImport(j.jobId).subscribe({
      next: (r) => { this.busy.set(false); this.job.set(r); this.notice.success(`${r.committedRows} product(s) imported.`); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }

  protected download(url: string, name: string): void {
    this.api.downloadText(url).subscribe({
      next: (blob) => {
        const href = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = href; a.download = name; a.click();
        URL.revokeObjectURL(href);
      },
      error: (e) => this.notice.error(describeError(toApiError(e))),
    });
  }
}
