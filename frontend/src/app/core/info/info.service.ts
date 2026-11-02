import { Injectable, signal } from '@angular/core';

export type InfoKind = 'success' | 'error' | 'warn' | 'info';

export interface InfoMessage {
  id: number;
  kind: InfoKind;
  title: string;
  text: string;
  /** Auto-dismiss time in ms (also drives the countdown bar). */
  ttl: number;
}

const TITLES: Record<InfoKind, string> = { success: 'Success!', error: 'Oh no!', warn: 'Heads up', info: 'Note' };
const TTL: Record<InfoKind, number> = { success: 4000, error: 7000, warn: 6000, info: 5000 };
const MAX_VISIBLE = 4;

/**
 * Single place for user feedback: every save/submit/validation result (from the backend or the browser) is shown
 * through this service as a popup. Components call e.g. `info.error(message)` or `info.success('Saved.')`.
 */
@Injectable({ providedIn: 'root' })
export class InfoService {
  readonly messages = signal<InfoMessage[]>([]);
  private seq = 0;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  success(text: string, title?: string): void { this.push('success', text, title); }
  error(text: string, title?: string): void { this.push('error', text, title); }
  warn(text: string, title?: string): void { this.push('warn', text, title); }
  info(text: string, title?: string): void { this.push('info', text, title); }

  dismiss(id: number): void {
    const t = this.timers.get(id);
    if (t) clearTimeout(t);
    this.timers.delete(id);
    this.messages.update((list) => list.filter((m) => m.id !== id));
  }

  clear(): void {
    [...this.messages()].forEach((m) => this.dismiss(m.id));
  }

  private push(kind: InfoKind, rawText: string, title?: string): void {
    const text = (rawText ?? '').toString().trim();
    if (!text) return;
    // The same message shown again replaces the old popup (restarts its timer) instead of stacking copies.
    const dup = this.messages().find((m) => m.kind === kind && m.text === text);
    if (dup) this.dismiss(dup.id);
    const id = ++this.seq;
    const ttl = TTL[kind];
    this.messages.update((list) => [...list, { id, kind, title: title ?? TITLES[kind], text, ttl }].slice(-MAX_VISIBLE));
    this.timers.set(id, setTimeout(() => this.dismiss(id), ttl));
  }
}
