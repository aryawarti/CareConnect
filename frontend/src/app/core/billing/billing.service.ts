import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope, PagedEnvelope } from '../patients/patient.models';
import { Invoice } from './billing.models';

@Injectable({ providedIn: 'root' })
export class BillingService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/invoices';

  myInvoices(page = 0, size = 20): Observable<PagedEnvelope<Invoice>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<Invoice>>(`${this.base}/me`, { params });
  }

  byStatus(status: string, page = 0, size = 20): Observable<PagedEnvelope<Invoice>> {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<Invoice>>(this.base, { params });
  }

  /**
   * `reference` is the idempotency key: generated once per attempt so a
   * retry (or double-click) can never charge twice — the server rejects a
   * repeated reference with 409 rather than creating a second payment.
   */
  pay(id: string, amount: number): Observable<Invoice> {
    const reference = `web-${id}-${crypto.randomUUID()}`;
    return this.http.post<Envelope<Invoice>>(`${this.base}/${id}/payments`,
      { amount, method: 'SIMULATED', reference }).pipe(map(r => r.data));
  }

  voidInvoice(id: string, reason: string): Observable<Invoice> {
    return this.http.post<Envelope<Invoice>>(`${this.base}/${id}/void`, { reason })
      .pipe(map(r => r.data));
  }
}
