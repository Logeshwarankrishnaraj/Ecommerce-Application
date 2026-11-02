import { Component, computed, input, signal } from '@angular/core';
import { ImageRef } from '../../models/api.models';

/** Gallery with consistent aspect ratio (from --img-ratio) and keyboard-accessible thumbnails. */
@Component({
  selector: 'app-image-gallery',
  templateUrl: './image-gallery.component.html',
  styleUrl: './image-gallery.component.css',
})
export class ImageGalleryComponent {
  readonly images = input<ImageRef[]>([]);
  readonly fallbackAlt = input('Product image');
  private readonly selectedIdx = signal(0);

  readonly current = computed<ImageRef | null>(() => {
    const list = this.images();
    return list.length ? list[Math.min(this.selectedIdx(), list.length - 1)] : null;
  });

  select(i: number): void {
    this.selectedIdx.set(i);
  }

  isSelected(i: number): boolean {
    return i === Math.min(this.selectedIdx(), Math.max(this.images().length - 1, 0));
  }
}
