import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { ConfirmService } from './confirm.service';

@Component({
  selector: 'admin-confirm-dialog',
  standalone: true,
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.css',
})
export class ConfirmDialogComponent {
  protected readonly confirm = inject(ConfirmService);
  private readonly okButton = viewChild<ElementRef<HTMLButtonElement>>('okButton');

  constructor() {
    effect(() => {
      if (this.confirm.pending()) {
        queueMicrotask(() => this.okButton()?.nativeElement.focus());
      }
    });
  }
}
