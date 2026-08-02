import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope, PagedEnvelope } from '../patients/patient.models';
import {
  Department, DirectoryEntry, Doctor, DoctorProfile, ScheduleException, Slot
} from './provider.models';

@Injectable({ providedIn: 'root' })
export class ProvidersService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/providers';

  directory(q: string, page: number, size: number,
            departmentId?: string): Observable<PagedEnvelope<DirectoryEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    if (departmentId) {
      params = params.set('departmentId', departmentId);
    }
    return this.http.get<PagedEnvelope<DirectoryEntry>>(`${this.base}/directory`, { params });
  }

  departments(): Observable<Department[]> {
    return this.http.get<Envelope<Department[]>>(`${this.base}/departments`).pipe(map(r => r.data));
  }

  /**
   * The full doctor record, for administration screens only.
   *
   * Not the public directory: this carries contact details, employment status
   * and verification state, which staff managing the clinic need and a patient
   * browsing for a cardiologist has no business receiving.
   */
  allDoctors(page = 0, size = 100): Observable<PagedEnvelope<Doctor>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<Doctor>>(`${this.base}/doctors`, { params });
  }

  /** Credentials, fee, weekly hours and time off in one call — see DoctorProfile. */
  profile(id: string): Observable<DoctorProfile> {
    return this.http.get<Envelope<DoctorProfile>>(`${this.base}/doctors/${id}/profile`)
      .pipe(map(r => r.data));
  }

  timeOff(doctorId: string): Observable<ScheduleException[]> {
    return this.http.get<Envelope<ScheduleException[]>>(`${this.base}/doctors/${doctorId}/exceptions`)
      .pipe(map(r => r.data));
  }

  addTimeOff(doctorId: string, date: string, reason: string): Observable<ScheduleException> {
    return this.http.post<Envelope<ScheduleException>>(
      `${this.base}/doctors/${doctorId}/exceptions`, { date, reason }).pipe(map(r => r.data));
  }

  removeTimeOff(doctorId: string, exceptionId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/doctors/${doctorId}/exceptions/${exceptionId}`);
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
