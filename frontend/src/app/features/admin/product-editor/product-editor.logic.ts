import { AbstractControl, ValidationErrors } from '@angular/forms';
import { AttributeValueInput } from '../models/admin.models';

/** Client-side mirror of server rules (server remains authoritative). */
export const MAX_IMAGES = 5;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png'];

export function canAddImage(currentCount: number): boolean {
  return currentCount < MAX_IMAGES;
}

/** Returns an error message, or null when the file passes the pre-check. */
export function validateImageFile(file: { type: string; size: number; name?: string }, altText: string, currentCount: number): string | null {
  if (!canAddImage(currentCount)) {
    return `A product can have at most ${MAX_IMAGES} images. Delete one before adding another.`;
  }
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return 'Only JPEG and PNG images are allowed.';
  }
  if (file.size <= 0) {
    return 'The selected file is empty.';
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return `Image is too large (max ${MAX_IMAGE_BYTES / (1024 * 1024)} MB).`;
  }
  if (!altText || !altText.trim()) {
    return 'Alt text is required for every image.';
  }
  return null;
}

/** Sale price must satisfy 0 <= salePrice <= basePrice when present. */
export function salePriceValidator(group: AbstractControl): ValidationErrors | null {
  const base = group.get('basePrice')?.value as number | null;
  const sale = group.get('salePrice')?.value as number | null;
  if (sale === null || sale === undefined || (sale as unknown) === '') {
    return null;
  }
  if (sale < 0 || (base !== null && base !== undefined && sale > base)) {
    group.get('salePrice')?.setErrors({ ...(group.get('salePrice')?.errors ?? {}), salePrice: true });
    return { salePrice: true };
  }
  const c = group.get('salePrice');
  if (c?.errors?.['salePrice']) {
    const { salePrice: _removed, ...rest } = c.errors;
    c.setErrors(Object.keys(rest).length ? rest : null);
  }
  return null;
}

/** Moves an element within a copy of the list (for up/down buttons and drag & drop). */
export function moveItem<T>(list: readonly T[], from: number, to: number): T[] {
  const copy = [...list];
  if (from < 0 || from >= copy.length || to < 0 || to >= copy.length || from === to) {
    return copy;
  }
  const [item] = copy.splice(from, 1);
  copy.splice(to, 0, item);
  return copy;
}

/** Converts a code → value map into the API attribute list, dropping blanks. */
export function buildAttributes(values: Record<string, string>, lovCodes: Set<string>): AttributeValueInput[] {
  const out: AttributeValueInput[] = [];
  for (const [code, raw] of Object.entries(values)) {
    const value = (raw ?? '').trim();
    if (!value) continue;
    out.push(lovCodes.has(code) ? { code, valueCode: value } : { code, textValue: value });
  }
  return out;
}
