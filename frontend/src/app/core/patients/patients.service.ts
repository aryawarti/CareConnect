import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope, PagedEnvelope, Patient } from './patient.models';

@Injectable({ providedIn: 'root' })
export class PatientsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/patients';

  search(q: string, page: number, size: number): Observable<PagedEnvelope<Patient>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<PagedEnvelope<Patient>>(this.base, { params });
  }

  get(id: string): Observable<Patient> {
    return this.http.get<Envelope<Patient>>(`${this.base}/${id}`).pipe(map(r => r.data));
  }

  create(body: Partial<Patient>): Observable<Patient> {
    return this.http.post<Envelope<Patient>>(this.base, body).pipe(map(r => r.data));
  }

  update(id: string, body: Partial<Patient>): Observable<Patient> {
    return this.http.put<Envelope<Patient>>(`${this.base}/${id}`, body).pipe(map(r => r.data));
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  createMyProfile(body: Partial<Patient>): Observable<Patient> {
    return this.http.post<Envelope<Patient>>(`${this.base}/me`, body).pipe(map(r => r.data));
  }

  myProfile(): Observable<Patient> {
    return this.http.get<Envelope<Patient>>(`${this.base}/me`).pipe(map(r => r.data));
  }

  updateMyContact(body: Partial<Patient>): Observable<Patient> {
    return this.http.put<Envelope<Patient>>(`${this.base}/me`, body).pipe(map(r => r.data));
  }
}
