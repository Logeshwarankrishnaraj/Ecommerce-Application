import { Component, input, output } from '@angular/core';

/** Used for both "nothing here" and retryable failures. */
@Component({
  selector: 'app-empty-state',
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.css',
})
export class EmptyStateComponent {
  readonly title = input('Nothing here yet');
  readonly message = input('');
  readonly kind = input<'empty' | 'error'>('empty');
  readonly actionLabel = input<string | null>(null);
  readonly action = output<void>();
}
