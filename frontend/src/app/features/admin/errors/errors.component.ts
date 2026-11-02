import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ErrorRow } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { PagerComponent } from '../shared/pager.component';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-errors',
  standalone: true,
  imports: [FormsModule, DatePipe, StateComponent, PagerComponent],
  templateUrl: './errors.component.html',
  styleUrl: './errors.component.css',
})
export class ErrorsComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly notice = inject(NoticeService);
  protected readonly rows = signal<ErrorRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected status = 'OPEN';
  protected severity = '';

  ngOnInit(): void { this.load(); }
  protected search(): void { this.page.set(0); this.load(); }
  protected goTo(p: number): void { this.page.set(p); this.load(); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listErrors({ status: this.status, severity: this.severity, page: this.page(), size: 20 }).subscribe({
      next: (p) => { this.rows.set(p.content); this.totalPages.set(p.totalPages); this.total.set(p.totalElements); this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }
  protected ack(r: ErrorRow): void {
    this.api.acknowledgeError(r.eventId).subscribe({ next: () => { this.notice.success('Acknowledged.'); this.load(); }, error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
}
