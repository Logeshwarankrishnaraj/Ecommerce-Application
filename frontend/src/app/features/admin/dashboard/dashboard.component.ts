import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardSummary } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-dashboard',
  standalone: true,
  imports: [StateComponent, RouterLink, DatePipe, DecimalPipe, KeyValuePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly totalOrders = computed(() =>
    Object.values(this.summary()?.orderCountsByStatus ?? {}).reduce((a, b) => a + b, 0));

  ngOnInit(): void { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.dashboard().subscribe({
      next: (s) => { this.summary.set(s); this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }
}
