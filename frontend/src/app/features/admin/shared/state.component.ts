import { Component, input, output } from '@angular/core';

/** Shared loading / error / empty wrapper. Content is projected for the populated state. */
@Component({
  selector: 'admin-state',
  standalone: true,
  templateUrl: './state.component.html',
  styleUrl: './state.component.css',
})
export class StateComponent {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyText = input('Nothing to show yet.');
  readonly retry = output<void>();
}
