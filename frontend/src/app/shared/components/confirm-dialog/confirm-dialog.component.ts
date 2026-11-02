import { Component, input, output } from '@angular/core';

/** Modal confirmation for destructive actions; shows the affected record. */
@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.css',
})
export class ConfirmDialogComponent {
  readonly open = input(false);
  readonly title = input('Please confirm');
  readonly message = input('');
  readonly subject = input<string | null>(null);
  readonly confirmLabel = input('Confirm');
  readonly busy = input(false);
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
