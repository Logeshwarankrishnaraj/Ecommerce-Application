import { Directive, ElementRef, HostListener, OnDestroy, Renderer2, inject, input } from '@angular/core';

let tooltipSeq = 0;

/**
 * Accessible tooltip: shows on hover AND keyboard focus, hides on blur/Escape.
 * Also gives the host an aria-label when it has none (icon-only buttons),
 * so the text is never the only carrier of essential information.
 */
@Directive({ selector: '[appTooltip]' })
export class TooltipDirective implements OnDestroy {
  readonly appTooltip = input.required<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private tip: HTMLElement | null = null;

  @HostListener('mouseenter')
  @HostListener('focusin')
  show(): void {
    const text = this.appTooltip();
    if (!text || this.tip) return;
    const el = this.host.nativeElement;
    if (!el.hasAttribute('aria-label') && !el.textContent?.trim()) {
      this.renderer.setAttribute(el, 'aria-label', text);
    }
    const tip = this.renderer.createElement('span') as HTMLElement;
    const id = `tt-${++tooltipSeq}`;
    this.renderer.setAttribute(tip, 'role', 'tooltip');
    this.renderer.setAttribute(tip, 'id', id);
    this.renderer.addClass(tip, 'app-tooltip');
    this.renderer.appendChild(tip, this.renderer.createText(text));
    this.renderer.setAttribute(el, 'aria-describedby', id);
    this.renderer.appendChild(el, tip);
    this.tip = tip;
  }

  @HostListener('mouseleave')
  @HostListener('focusout')
  @HostListener('keydown.escape')
  hide(): void {
    if (!this.tip) return;
    this.renderer.removeAttribute(this.host.nativeElement, 'aria-describedby');
    this.renderer.removeChild(this.host.nativeElement, this.tip);
    this.tip = null;
  }

  ngOnDestroy(): void {
    this.hide();
  }
}
