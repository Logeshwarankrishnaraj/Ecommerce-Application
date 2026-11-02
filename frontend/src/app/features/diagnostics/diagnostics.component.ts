import { HttpClient, HttpResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toApiError } from '../../shared/util/api-error.util';

interface Step {
  name: string;
  request: string;
  status: 'pending' | 'ok' | 'fail';
  detail: string;
  ms?: number;
}

/**
 * Connection check: shows, call by call, whether the browser reaches the backend (through the dev proxy) and
 * whether the backend reaches MySQL. Never prints passwords. Meant for local development.
 */
@Component({
  selector: 'app-diagnostics',
  imports: [FormsModule],
  templateUrl: './diagnostics.component.html',
  styleUrl: './diagnostics.component.css',
})
export class DiagnosticsComponent {
  private readonly http = inject(HttpClient);
  readonly origin = location.origin;
  readonly steps = signal<Step[]>([]);
  readonly running = signal(false);
  readonly verdict = signal('');
  readonly allOk = signal(false);
  user = '';
  pass = '';

  async run(): Promise<void> {
    this.running.set(true);
    this.verdict.set('');
    this.steps.set([]);
    // 1. CSRF cookie
    await this.call('Backend reachable + CSRF cookie', 'GET /api/v1/auth/csrf', () => this.http.get('/api/v1/auth/csrf', { observe: 'response' }), (r) =>
      document.cookie.includes('XSRF-TOKEN=') ? 'Backend answered and issued the XSRF-TOKEN cookie.' : 'Backend answered but no XSRF-TOKEN cookie was set.');
    // 2. Public data
    await this.call('Public storefront data', 'GET /api/v1/storefront/config', () => this.http.get<{ shop?: { displayName?: string } }>('/api/v1/storefront/config', { observe: 'response' }), (r) =>
      `Shop name: ${(r.body as { shop?: { displayName?: string } } | null)?.shop?.displayName ?? '(none)'}`);
    // 3. Database
    await this.call('Backend to MySQL', 'GET /api/v1/dev/ping', () => this.http.get<{ appUsers: number; roles: number; profiles: string[] }>('/api/v1/dev/ping', { observe: 'response' }), (r) => {
      const b = r.body as { appUsers: number; roles: number; profiles: string[] } | null;
      return `MySQL reachable. app_users=${b?.appUsers}, roles=${b?.roles}, profiles=${b?.profiles?.join(',')}`;
    });
    // 4. Optional login
    if (this.user && this.pass) {
      await this.call('Login (database lookup)', 'POST /api/v1/auth/login',
        () => this.http.post<{ roles?: string[]; email?: string }>('/api/v1/auth/login', { usernameOrEmail: this.user.trim(), password: this.pass }, { observe: 'response' }),
        (r) => `Signed in as ${(r.body as { email?: string } | null)?.email}, roles: ${((r.body as { roles?: string[] } | null)?.roles ?? []).join(', ')}`);
    }
    const list = this.steps();
    const good = list.length > 0 && list.every((s) => s.status === 'ok');
    this.allOk.set(good);
    this.verdict.set(good
      ? 'Frontend, backend and database are connected. Each call above should also appear as an "API ..." line in the backend console.'
      : 'At least one check failed. Read the Detail column; the usual causes are listed in the README under "Troubleshooting".');
    this.running.set(false);
  }

  private add(s: Step): void {
    this.steps.update((l) => [...l, s]);
  }

  private async call(name: string, request: string, make: () => import('rxjs').Observable<HttpResponse<unknown>>, okText: (r: HttpResponse<unknown>) => string): Promise<void> {
    const t0 = performance.now();
    try {
      const r = await new Promise<HttpResponse<unknown>>((resolve, reject) => make().subscribe({ next: resolve, error: reject }));
      this.add({ name, request, status: 'ok', detail: okText(r), ms: Math.round(performance.now() - t0) });
    } catch (e) {
      this.add({ name, request, status: 'fail', detail: this.explain(e), ms: Math.round(performance.now() - t0) });
    }
  }

  private explain(e: unknown): string {
    const a = toApiError(e);   // the interceptor already normalised the failure
    const server = a.message ? ` Server said: "${a.message}".` : '';
    switch (true) {
      case a.status === 0: return 'No response at all: the backend is not running on port 8080, or ng serve was started without the proxy (restart it).';
      case a.status === 404: return 'HTTP 404: no matching backend endpoint. Restart ng serve (proxy), and check the backend runs the latest code with the local profile.' + server;
      case a.status === 403: return 'HTTP 403: rejected (missing CSRF token, or the account lacks the required role).' + server;
      case a.status === 401: return 'HTTP 401: wrong username/password, or not signed in.' + server;
      case a.status === 429: return 'HTTP 429: too many attempts; wait a few minutes.' + server;
      case a.status >= 500: return `HTTP ${a.status}: either the backend is NOT running on port 8080 (the dev proxy answers 5xx when it cannot connect), or it crashed on this request (read the backend console).` + server;
      default: return `HTTP ${a.status}.` + server;
    }
  }
}
