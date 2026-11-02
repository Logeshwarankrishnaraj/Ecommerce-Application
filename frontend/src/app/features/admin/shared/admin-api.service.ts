import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../shared/models/api.models';
import {
  AdminBlock, AdminCategory, AdminOrderDetail, AdminOrderRow, AdminProduct, AdminProductRow, AdminUserRow,
  AnalyticsResponse, AttributeDefinition, AuditRow, CategoryUpsert, ContentPage, DashboardSummary, ErrorRow,
  ImportJob, ImportMode, InventoryMovement, InventoryRow, LookupGroup, LookupValue, ProductUpsert, ShipmentInput,
  ShopProfile, SiteLink, SiteSetting,
} from '../models/admin.models';

export type QueryParams = Record<string, string | number | boolean | null | undefined>;

/** Builds HttpParams skipping empty values. */
export function toParams(query: QueryParams = {}): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

/** Single HTTP gateway for all admin endpoints (/api/v1/admin/**). */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/admin';

  // dashboard
  dashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/dashboard/summary`);
  }

  // products
  listProducts(q: QueryParams): Observable<Page<AdminProductRow>> {
    return this.http.get<Page<AdminProductRow>>(`${this.base}/products`, { params: toParams(q) });
  }
  getProduct(id: number): Observable<AdminProduct> {
    return this.http.get<AdminProduct>(`${this.base}/products/${id}`);
  }
  createProduct(body: ProductUpsert): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(`${this.base}/products`, body);
  }
  updateProduct(id: number, body: ProductUpsert): Observable<AdminProduct> {
    return this.http.patch<AdminProduct>(`${this.base}/products/${id}`, body);
  }
  productLifecycle(id: number, action: 'publish' | 'unpublish' | 'archive'): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(`${this.base}/products/${id}/${action}`, {});
  }
  uploadImage(id: number, file: File, altText: string): Observable<AdminProduct> {
    const form = new FormData();
    form.append('file', file);
    form.append('altText', altText);
    return this.http.post<AdminProduct>(`${this.base}/products/${id}/images`, form);
  }
  reorderImages(id: number, imageIds: number[]): Observable<AdminProduct> {
    return this.http.patch<AdminProduct>(`${this.base}/products/${id}/images/order`, { imageIds });
  }
  updateImage(id: number, imageId: number, body: { altText?: string; primary?: boolean }): Observable<AdminProduct> {
    return this.http.patch<AdminProduct>(`${this.base}/products/${id}/images/${imageId}`, body);
  }
  deleteImage(id: number, imageId: number): Observable<AdminProduct> {
    return this.http.delete<AdminProduct>(`${this.base}/products/${id}/images/${imageId}`);
  }

  // categories
  listCategories(): Observable<AdminCategory[]> {
    return this.http.get<AdminCategory[]>(`${this.base}/categories`);
  }
  createCategory(body: CategoryUpsert): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(`${this.base}/categories`, body);
  }
  updateCategory(id: number, body: CategoryUpsert): Observable<AdminCategory> {
    return this.http.patch<AdminCategory>(`${this.base}/categories/${id}`, body);
  }
  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/categories/${id}`);
  }

  // lookups
  listLookupGroups(): Observable<LookupGroup[]> {
    return this.http.get<LookupGroup[]>(`${this.base}/lookups`);
  }
  listLookupValues(groupCode: string): Observable<LookupValue[]> {
    return this.http.get<LookupValue[]>(`${this.base}/lookups/${encodeURIComponent(groupCode)}`);
  }
  createLookupValue(groupCode: string, body: { valueCode: string; displayLabel: string; sortOrder: number }): Observable<LookupValue> {
    return this.http.post<LookupValue>(`${this.base}/lookups/${encodeURIComponent(groupCode)}/values`, body);
  }
  updateLookupValue(id: number, body: Partial<Pick<LookupValue, 'displayLabel' | 'active' | 'sortOrder'>>): Observable<LookupValue> {
    return this.http.patch<LookupValue>(`${this.base}/lookups/values/${id}`, body);
  }
  deleteLookupValue(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/lookups/values/${id}`);
  }
  attributeDefinitions(): Observable<AttributeDefinition[]> {
    return this.http.get<AttributeDefinition[]>(`${this.base}/attribute-definitions`);
  }

  // inventory
  listInventory(q: QueryParams): Observable<Page<InventoryRow>> {
    return this.http.get<Page<InventoryRow>>(`${this.base}/inventory`, { params: toParams(q) });
  }
  adjustInventory(body: { productId: number; quantityDelta: number; reason: string }): Observable<InventoryRow> {
    return this.http.post<InventoryRow>(`${this.base}/inventory/adjustments`, body);
  }
  inventoryMovements(productId: number, q: QueryParams): Observable<Page<InventoryMovement>> {
    return this.http.get<Page<InventoryMovement>>(`${this.base}/inventory/${productId}/movements`, { params: toParams(q) });
  }

  // imports
  importTemplateUrl(): string {
    return `${this.base}/imports/products/template`;
  }
  importErrorsUrl(jobId: number): string {
    return `${this.base}/imports/${jobId}/errors.csv`;
  }
  downloadText(url: string): Observable<Blob> {
    return this.http.get(url, { responseType: 'blob' });
  }
  previewImport(file: File, images: File | null, mode: ImportMode, allowPartial: boolean): Observable<ImportJob> {
    const form = new FormData();
    form.append('file', file);
    if (images) {
      form.append('images', images);
    }
    form.append('mode', mode);
    form.append('allowPartial', String(allowPartial));
    return this.http.post<ImportJob>(`${this.base}/imports/products/preview`, form);
  }
  commitImport(jobId: number): Observable<ImportJob> {
    return this.http.post<ImportJob>(`${this.base}/imports/products/${jobId}/commit`, {});
  }
  getImport(jobId: number): Observable<ImportJob> {
    return this.http.get<ImportJob>(`${this.base}/imports/${jobId}`);
  }

  // orders
  listOrders(q: QueryParams): Observable<Page<AdminOrderRow>> {
    return this.http.get<Page<AdminOrderRow>>(`${this.base}/orders`, { params: toParams(q) });
  }
  /** CSV of the orders matching the filters (same query params as listOrders; page/size are ignored by the server). */
  exportOrders(q: QueryParams): Observable<Blob> {
    return this.http.get(`${this.base}/orders/export`, { params: toParams(q), responseType: 'blob' });
  }
  getOrder(id: number): Observable<AdminOrderDetail> {
    return this.http.get<AdminOrderDetail>(`${this.base}/orders/${id}`);
  }
  changeOrderStatus(id: number, status: string, note?: string): Observable<AdminOrderDetail> {
    return this.http.post<AdminOrderDetail>(`${this.base}/orders/${id}/status`, { status, note: note || null });
  }
  createShipment(orderId: number, body: ShipmentInput): Observable<AdminOrderDetail> {
    return this.http.post<AdminOrderDetail>(`${this.base}/orders/${orderId}/shipments`, body);
  }
  updateShipment(shipmentId: number, body: ShipmentInput): Observable<AdminOrderDetail> {
    return this.http.patch<AdminOrderDetail>(`${this.base}/shipments/${shipmentId}`, body);
  }
  savePackingNotes(orderId: number, packingNotes: string): Observable<AdminOrderDetail> {
    return this.http.patch<AdminOrderDetail>(`${this.base}/orders/${orderId}/packing-notes`, { packingNotes });
  }

  // analytics
  analytics(q: QueryParams): Observable<AnalyticsResponse> {
    return this.http.get<AnalyticsResponse>(`${this.base}/analytics/products`, { params: toParams(q) });
  }

  // shop + settings
  getShopProfile(): Observable<ShopProfile> {
    return this.http.get<ShopProfile>(`${this.base}/shop-profile`);
  }
  saveShopProfile(body: ShopProfile): Observable<ShopProfile> {
    return this.http.put<ShopProfile>(`${this.base}/shop-profile`, body);
  }
  listSettings(): Observable<SiteSetting[]> {
    return this.http.get<SiteSetting[]>(`${this.base}/site-settings`);
  }
  saveSettings(values: Record<string, string>): Observable<SiteSetting[]> {
    return this.http.patch<SiteSetting[]>(`${this.base}/site-settings`, { values });
  }
  listSiteLinks(): Observable<SiteLink[]> {
    return this.http.get<SiteLink[]>(`${this.base}/site-links`);
  }
  saveSiteLinks(links: SiteLink[]): Observable<SiteLink[]> {
    return this.http.put<SiteLink[]>(`${this.base}/site-links`, links);
  }

  // content
  listContentPages(): Observable<ContentPage[]> {
    return this.http.get<ContentPage[]>(`${this.base}/content-pages`);
  }
  createContentPage(body: { slug: string; title: string; published: boolean }): Observable<ContentPage> {
    return this.http.post<ContentPage>(`${this.base}/content-pages`, body);
  }
  updateContentPage(id: number, body: { title: string; published: boolean; blocks: AdminBlock[] }): Observable<ContentPage> {
    return this.http.patch<ContentPage>(`${this.base}/content-pages/${id}`, body);
  }

  // errors / audit / users
  listErrors(q: QueryParams): Observable<Page<ErrorRow>> {
    return this.http.get<Page<ErrorRow>>(`${this.base}/errors`, { params: toParams(q) });
  }
  getError(id: number): Observable<ErrorRow> {
    return this.http.get<ErrorRow>(`${this.base}/errors/${id}`);
  }
  acknowledgeError(id: number): Observable<ErrorRow> {
    return this.http.post<ErrorRow>(`${this.base}/errors/${id}/acknowledge`, {});
  }
  listAudit(q: QueryParams): Observable<Page<AuditRow>> {
    return this.http.get<Page<AuditRow>>(`${this.base}/audit-logs`, { params: toParams(q) });
  }
  deleteAudit(body: { ids?: number[]; all?: boolean }): Observable<{ deleted: number }> {
    return this.http.post<{ deleted: number }>(`${this.base}/audit-logs/delete`, { ids: body.ids ?? [], all: !!body.all });
  }
  listUsers(q: QueryParams): Observable<Page<AdminUserRow>> {
    return this.http.get<Page<AdminUserRow>>(`${this.base}/users`, { params: toParams(q) });
  }
  setUserStatus(id: number, status: 'ACTIVE' | 'DISABLED'): Observable<AdminUserRow> {
    return this.http.post<AdminUserRow>(`${this.base}/users/${id}/status`, { status });
  }
  setUserRole(id: number, role: string, grant: boolean): Observable<AdminUserRow> {
    return this.http.post<AdminUserRow>(`${this.base}/users/${id}/roles`, { role, grant });
  }
}
