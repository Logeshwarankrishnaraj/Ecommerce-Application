import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AnalyticsResponse } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-analytics',
  standalone: true,
  imports: [FormsModule, StateComponent],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.css',
})
export class AnalyticsComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  protected readonly data = signal<AnalyticsResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected from = '';
  protected to = '';

  ngOnInit(): void { this.load(); }
  protected pct(v: number, d: AnalyticsResponse): number { const max = Math.max(1, ...d.daily.map((x) => x.views)); return Math.max(2, (v / max) * 100); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.analytics({ from: this.from, to: this.to, limit: 20 }).subscribe({
      next: (d) => { this.data.set(d); if (!this.from) { this.from = d.from; this.to = d.to; } this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }
}
