import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'admin-pager',
  standalone: true,
  templateUrl: './pager.component.html',
  styleUrl: './pager.component.css',
})
export class PagerComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input(0);
  readonly pageChange = output<number>();
  protected readonly hasPrev = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());
}
