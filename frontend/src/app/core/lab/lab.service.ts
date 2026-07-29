import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope } from '../patients/patient.models';
import {
  CatalogueTest, CreateOrderRequest, EnterResultsRequest, LabOrder
} from './lab.models';

/**
 * Client for laboratory-service (`/api/lab`). Every mutating call returns the
 * refreshed order so the caller can rebind without a second fetch — the server
 * always responds with the full detail after a transition.
 */
@Injectable({ providedIn: 'root' })
export class LabService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/lab';

  catalogue(q = ''): Observable<CatalogueTest[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Envelope<CatalogueTest[]>>(`${this.base}/catalogue`, { params })
      .pipe(map(r => r.data));
  }

  /** Doctor: place an order from the encounter editor. */
  order(request: CreateOrderRequest): Observable<LabOrder> {
    return this.http.post<Envelope<LabOrder>>(`${this.base}/orders`, request).pipe(map(r => r.data));
  }

  /** Technician: everything not yet released. */
  worklist(): Observable<LabOrder[]> {
    return this.http.get<Envelope<LabOrder[]>>(`${this.base}/orders`).pipe(map(r => r.data));
  }

  detail(id: string): Observable<LabOrder> {
    return this.http.get<Envelope<LabOrder>>(`${this.base}/orders/${id}`).pipe(map(r => r.data));
  }

  collect(id: string, specimenType: string): Observable<LabOrder> {
    return this.http.post<Envelope<LabOrder>>(`${this.base}/orders/${id}/collection`, { specimenType })
      .pipe(map(r => r.data));
  }

  beginProcessing(id: string): Observable<LabOrder> {
    return this.http.post<Envelope<LabOrder>>(`${this.base}/orders/${id}/processing`, {})
      .pipe(map(r => r.data));
  }

  reject(id: string, reason: string): Observable<LabOrder> {
    return this.http.post<Envelope<LabOrder>>(`${this.base}/orders/${id}/rejection`, { reason })
      .pipe(map(r => r.data));
  }

  enterResults(id: string, request: EnterResultsRequest): Observable<LabOrder> {
    return this.http.post<Envelope<LabOrder>>(`${this.base}/orders/${id}/results`, request)
      .pipe(map(r => r.data));
  }

  /** Senior verification releases the report to the patient. */
  verify(id: string): Observable<LabOrder> {
    return this.http.post<Envelope<LabOrder>>(`${this.base}/orders/${id}/verification`, {})
      .pipe(map(r => r.data));
  }

  /** Patient: released reports only. */
  myReports(): Observable<LabOrder[]> {
    return this.http.get<Envelope<LabOrder[]>>(`${this.base}/me`).pipe(map(r => r.data));
  }

  forPatient(patientId: string): Observable<LabOrder[]> {
    return this.http.get<Envelope<LabOrder[]>>(`${this.base}/patient/${patientId}`).pipe(map(r => r.data));
  }
}
