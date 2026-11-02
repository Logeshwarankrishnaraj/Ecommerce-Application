import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountMe, Page } from '../../../shared/models/api.models';
import { Address, OrderDetail, OrderSummary } from '../../../shared/models/storefront.models';

@Injectable({ providedIn: 'root' })
export class AccountApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/account';

  updateProfile(body: { fullName?: string; mobileNumber?: string }): Observable<AccountMe> {
    return this.http.patch<AccountMe>(`${this.base}/me`, body);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/change-password`, { currentPassword, newPassword });
  }

  addresses(): Observable<Address[]> {
    return this.http.get<Address[]>(`${this.base}/addresses`);
  }

  createAddress(a: Address): Observable<Address> {
    return this.http.post<Address>(`${this.base}/addresses`, a);
  }

  updateAddress(id: number, a: Address): Observable<Address> {
    return this.http.patch<Address>(`${this.base}/addresses/${id}`, a);
  }

  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/addresses/${id}`);
  }

  orders(page = 0, size = 10): Observable<Page<OrderSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<OrderSummary>>(`${this.base}/orders`, { params });
  }

  order(orderId: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/orders/${orderId}`);
  }

  cancelOrder(orderId: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/orders/${orderId}/cancel`, {});
  }
}
