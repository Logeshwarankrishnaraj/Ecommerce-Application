import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { AdminUserRow } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { PagerComponent } from '../shared/pager.component';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-users',
  standalone: true,
  imports: [FormsModule, DatePipe, StateComponent, PagerComponent],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css',
})
export class UsersComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(AdminApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notice = inject(NoticeService);
  protected readonly rows = signal<AdminUserRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected q = '';
  protected role = '';
  protected status = '';

  ngOnInit(): void { this.load(); }
  protected roleText(u: AdminUserRow): string { return u.roles.map((r) => r.replace('ROLE_', '').toLowerCase().replace('_', ' ')).join(', '); }
  protected search(): void { this.page.set(0); this.load(); }
  protected goTo(p: number): void { this.page.set(p); this.load(); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listUsers({ q: this.q, role: this.role, status: this.status, page: this.page(), size: 20 }).subscribe({
      next: (p) => { this.rows.set(p.content); this.totalPages.set(p.totalPages); this.total.set(p.totalElements); this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }
  protected async toggleStatus(u: AdminUserRow): Promise<void> {
    const to = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    const ok = await this.confirm.ask({ title: `${to === 'DISABLED' ? 'Disable' : 'Enable'} account?`, subject: `${u.fullName} (${u.username})`,
      message: to === 'DISABLED' ? 'They are signed out immediately and cannot sign in until re-enabled.' : 'They will be able to sign in again.', confirmLabel: to === 'DISABLED' ? 'Disable' : 'Enable', danger: to === 'DISABLED' });
    if (!ok) return;
    this.api.setUserStatus(u.userId, to).subscribe({ next: () => { this.notice.success('User updated.'); this.load(); }, error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
  protected async toggleAdmin(u: AdminUserRow): Promise<void> {
    const grant = !u.roles.includes('ROLE_ADMIN');
    const ok = await this.confirm.ask({ title: grant ? 'Grant admin access?' : 'Remove admin access?', subject: `${u.fullName} (${u.username})`,
      message: grant ? 'This gives access to the admin dashboard.' : 'They will lose access to the admin dashboard.', confirmLabel: grant ? 'Grant' : 'Remove', danger: !grant });
    if (!ok) return;
    this.api.setUserRole(u.userId, 'ROLE_ADMIN', grant).subscribe({ next: () => { this.notice.success('Roles updated.'); this.load(); }, error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
}
