import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../shared/models/api.models';
import {
  CategoryNode,
  FiltersResponse,
  ProductCard,
  ProductDetail,
  ProductQuery,
} from '../../../shared/models/storefront.models';

@Injectable({ providedIn: 'root' })
export class ProductApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  list(query: ProductQuery): Observable<Page<ProductCard>> {
    let params = new HttpParams();
    const set = (k: string, v: string | number | boolean | null | undefined) => {
      if (v !== null && v !== undefined && v !== '') params = params.set(k, String(v));
    };
    set('q', query.q);
    set('category', query.category);
    set('minPrice', query.minPrice);
    set('maxPrice', query.maxPrice);
    set('sort', query.sort);
    if (query.inStock) set('inStock', true);
    set('page', query.page ?? 0);
    set('size', query.size ?? 12);
    for (const a of query.attr ?? []) params = params.append('attr', a);
    return this.http.get<Page<ProductCard>>(`${this.base}/products`, { params });
  }

  filters(): Observable<FiltersResponse> {
    return this.http.get<FiltersResponse>(`${this.base}/products/filters`);
  }

  detail(slug: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${this.base}/products/${encodeURIComponent(slug)}`);
  }

  related(productId: number): Observable<ProductCard[]> {
    return this.http.get<ProductCard[]>(`${this.base}/products/${productId}/related`);
  }

  categories(): Observable<CategoryNode[]> {
    return this.http.get<CategoryNode[]>(`${this.base}/categories`);
  }

  recordView(productId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/products/${productId}/interactions/view`, {});
  }
}
