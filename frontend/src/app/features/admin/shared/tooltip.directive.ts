import { Directive, ElementRef, HostListener, Renderer2, inject, input } from '@angular/core';

/**
 * Accessible tooltip: sets aria-label (when the host has no text) and shows a bubble on
 * hover AND keyboard focus. The label is never the only source of essential information.
 */
@Directive({ selector: '[adminTooltip]', standalone: true })
export class TooltipDirective {
  readonly adminTooltip = input.required<string>();
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private bubble: HTMLElement | null = null;

  @HostListener('mouseenter')
  @HostListener('focus')
  show(): void {
    if (this.bubble) return;
    const host = this.el.nativeElement;
    if (!host.getAttribute('aria-label') && !(host.textContent ?? '').trim()) {
      this.renderer.setAttribute(host, 'aria-label', this.adminTooltip());
    }
    const bubble = this.renderer.createElement('span') as HTMLElement;
    this.renderer.setAttribute(bubble, 'role', 'tooltip');
    this.renderer.addClass(bubble, 'admin-tooltip');
    bubble.textContent = this.adminTooltip();
    const style = bubble.style;
    style.position = 'absolute';
    style.zIndex = '1000';
    style.background = 'var(--c-text)';
    style.color = 'var(--c-bg)';
    style.padding = '4px 8px';
    style.borderRadius = '4px';
    style.fontSize = '12px';
    style.whiteSpace = 'nowrap';
    style.pointerEvents = 'none';
    const rect = host.getBoundingClientRect();
    style.left = `${rect.left + window.scrollX}px`;
    style.top = `${rect.bottom + window.scrollY + 4}px`;
    this.renderer.appendChild(document.body, bubble);
    this.bubble = bubble;
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  @HostListener('keydown.escape')
  hide(): void {
    if (this.bubble) {
      this.renderer.removeChild(document.body, this.bubble);
      this.bubble = null;
    }
  }
}
