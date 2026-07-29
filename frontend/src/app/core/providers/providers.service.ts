import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope, PagedEnvelope } from '../patients/patient.models';
import { Department, Doctor, Slot } from './provider.models';

@Injectable({ providedIn: 'root' })
export class ProvidersService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/providers';

  directory(q: string, page: number, size: number): Observable<PagedEnvelope<Doctor>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<PagedEnvelope<Doctor>>(`${this.base}/directory`, { params });
  }

  departments(): Observable<Department[]> {
    return this.http.get<Envelope<Department[]>>(`${this.base}/departments`).pipe(map(r => r.data));
  }

  /** The signed-in doctor's own profile. */
  me(): Observable<Doctor> {
    return this.http.get<Envelope<Doctor>>(`${this.base}/me`).pipe(map(r => r.data));
  }

  get(id: string): Observable<Doctor> {
    return this.http.get<Envelope<Doctor>>(`${this.base}/doctors/${id}`).pipe(map(r => r.data));
  }

  /** A self-registered doctor submitting credentials for verification. */
  apply(body: Record<string, unknown>): Observable<Doctor> {
    return this.http.post<Envelope<Doctor>>(`${this.base}/apply`, body).pipe(map(r => r.data));
  }

  /** Applications awaiting the administration's decision. */
  applications(): Observable<Doctor[]> {
    return this.http.get<Envelope<Doctor[]>>(`${this.base}/applications`).pipe(map(r => r.data));
  }

  approve(doctorId: string): Observable<Doctor> {
    return this.http.post<Envelope<Doctor>>(`${this.base}/doctors/${doctorId}/approval`, {})
      .pipe(map(r => r.data));
  }

  reject(doctorId: string, reason: string): Observable<Doctor> {
    return this.http.post<Envelope<Doctor>>(`${this.base}/doctors/${doctorId}/rejection`, { reason })
      .pipe(map(r => r.data));
  }

  create(body: Partial<Doctor> & { userId?: string }): Observable<Doctor> {
    return this.http.post<Envelope<Doctor>>(`${this.base}/doctors`, body).pipe(map(r => r.data));
  }

  update(id: string, body: Partial<Doctor>): Observable<Doctor> {
    return this.http.put<Envelope<Doctor>>(`${this.base}/doctors/${id}`, body).pipe(map(r => r.data));
  }

  availability(id: string): Observable<Slot[]> {
    return this.http.get<Envelope<Slot[]>>(`${this.base}/doctors/${id}/availability`)
      .pipe(map(r => r.data));
  }

  replaceAvailability(id: string, slots: Slot[]): Observable<Slot[]> {
    return this.http.put<Envelope<Slot[]>>(`${this.base}/doctors/${id}/availability`, { slots })
      .pipe(map(r => r.data));
  }
}
