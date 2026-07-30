import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope, PagedEnvelope } from '../patients/patient.models';
import { AccessLogEntry, Encounter } from './record.models';

@Injectable({ providedIn: 'root' })
export class RecordsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/records';

  myHistory(page = 0, size = 20): Observable<PagedEnvelope<Encounter>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<Encounter>>(`${this.base}/me`, { params });
  }

  doctorEncounters(page = 0, size = 20): Observable<PagedEnvelope<Encounter>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<Encounter>>(`${this.base}/doctor/me`, { params });
  }

  patientHistory(patientId: string): Observable<PagedEnvelope<Encounter>> {
    return this.http.get<PagedEnvelope<Encounter>>(`${this.base}/patient/${patientId}`);
  }

  // ---- chart access trail ---------------------------------------------------

  /** Who has opened my records. */
  myAccessLog(): Observable<AccessLogEntry[]> {
    return this.http.get<PagedEnvelope<AccessLogEntry>>(`${this.base}/access-log/me`)
      .pipe(map(r => r.data));
  }

  /** Audit view: every read of one patient's chart (ADMIN/STAFF). */
  patientAccessLog(patientId: string): Observable<AccessLogEntry[]> {
    return this.http
      .get<PagedEnvelope<AccessLogEntry>>(`${this.base}/access-log/patient/${patientId}`)
      .pipe(map(r => r.data));
  }

  get(id: string): Observable<Encounter> {
    return this.http.get<Envelope<Encounter>>(`${this.base}/${id}`).pipe(map(r => r.data));
  }

  updateContent(id: string, chiefComplaint: string, notes: string): Observable<Encounter> {
    return this.http.put<Envelope<Encounter>>(`${this.base}/${id}`, { chiefComplaint, notes })
      .pipe(map(r => r.data));
  }

  addDiagnosis(id: string, code: string, description: string): Observable<Encounter> {
    return this.http.post<Envelope<Encounter>>(`${this.base}/${id}/diagnoses`, { code, description })
      .pipe(map(r => r.data));
  }

  addPrescription(id: string, body: {
    medication: string; dosage: string; frequency: string;
    durationDays: number; instructions: string;
  }): Observable<Encounter> {
    return this.http.post<Envelope<Encounter>>(`${this.base}/${id}/prescriptions`, body)
      .pipe(map(r => r.data));
  }

  sign(id: string): Observable<Encounter> {
    return this.http.post<Envelope<Encounter>>(`${this.base}/${id}/signature`, {})
      .pipe(map(r => r.data));
  }

  amend(id: string, notes: string, reason: string): Observable<Encounter> {
    return this.http.post<Envelope<Encounter>>(`${this.base}/${id}/amendments`, { notes, reason })
      .pipe(map(r => r.data));
  }
}
