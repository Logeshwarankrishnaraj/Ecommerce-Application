import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { ThemeMode, ThemeService } from '../../../core/theme/theme.service';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-theme-toggle',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="tt-btn" aria-haspopup="menu" [attr.aria-expanded]="open()" aria-label="Colour theme"
            title="Colour theme" (click)="open.set(!open())">
      <app-icon [name]="theme.effective() === 'dark' ? 'moon' : 'sun'" />
    </button>
    @if (open()) {
      <div class="tt-menu" role="menu" aria-label="Colour theme">
        @for (o of options; track o.mode) {
          <button type="button" role="menuitemradio" [attr.aria-checked]="theme.mode() === o.mode"
                  [class.on]="theme.mode() === o.mode" (click)="pick(o.mode)">
            <app-icon [name]="o.icon" [size]="16" /> {{ o.label }}
            @if (theme.mode() === o.mode) { <app-icon name="check" [size]="14" class="ck" /> }
          </button>
        }
      </div>
    }
  `,
  styles: `
    :host { position: relative; display: inline-flex; }
    .tt-btn { display: inline-grid; place-items: center; width: 40px; height: 40px; border: 0; border-radius: 50%; background: none;
      color: var(--c-text); cursor: pointer; transition: background-color .15s; }
    .tt-btn:hover { background: var(--c-hover); }
    .tt-btn:focus-visible, .tt-menu button:focus-visible { outline: 2px solid var(--c-accent); outline-offset: 2px; }
    @media (pointer: coarse), (max-width: 899.98px) { .tt-btn { width: 44px; height: 44px; } }
    .tt-menu { position: absolute; right: 0; top: calc(100% + 6px); z-index: 80; min-width: 160px; padding: 6px; display: grid; gap: 2px;
      background: var(--c-surface); border: 1px solid var(--c-border); border-radius: 12px; box-shadow: var(--shadow-lg); }
    .tt-menu button { display: flex; align-items: center; gap: 10px; min-height: 40px; padding: 0 10px; border: 0; border-radius: 8px;
      background: none; color: var(--c-text); font: inherit; font-size: .9rem; text-align: left; cursor: pointer; }
    .tt-menu button:hover { background: var(--c-hover); }
    .tt-menu button.on { color: var(--c-accent); font-weight: 600; }
    .ck { margin-left: auto; }
  `,
})
export class ThemeToggleComponent {
  protected readonly theme = inject(ThemeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly open = signal(false);
  protected readonly options: { mode: ThemeMode; label: string; icon: string }[] = [
    { mode: 'light', label: 'Light', icon: 'sun' },
    { mode: 'dark', label: 'Dark', icon: 'moon' },
    { mode: 'system', label: 'System', icon: 'monitor' },
  ];

  protected pick(m: ThemeMode): void { this.theme.set(m); this.open.set(false); }

  @HostListener('document:click', ['$event']) onDoc(e: Event): void {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) this.open.set(false);
  }
  @HostListener('document:keydown.escape') onEsc(): void { this.open.set(false); }
}
