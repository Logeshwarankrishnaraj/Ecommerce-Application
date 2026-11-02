import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Block } from '../../../shared/models/storefront.models';

@Injectable({ providedIn: 'root' })
export class StorefrontApiService {
  private readonly http = inject(HttpClient);

  home(): Observable<{ blocks: Block[] }> {
    return this.http.get<{ blocks: Block[] }>('/api/v1/storefront/home');
  }

  page(slug: string): Observable<{ slug: string; title: string; blocks: Block[] }> {
    return this.http.get<{ slug: string; title: string; blocks: Block[] }>(
      `/api/v1/storefront/pages/${encodeURIComponent(slug)}`,
    );
  }
}
