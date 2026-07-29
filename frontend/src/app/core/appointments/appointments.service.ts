import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope, PagedEnvelope } from '../patients/patient.models';
import { Appointment, FreeSlot } from './appointment.models';

@Injectable({ providedIn: 'root' })
export class AppointmentsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/appointments';

  available(doctorId: string, date: string): Observable<FreeSlot[]> {
    const params = new HttpParams().set('doctorId', doctorId).set('date', date);
    return this.http.get<Envelope<FreeSlot[]>>(`${this.base}/available`, { params })
      .pipe(map(r => r.data));
  }

  book(doctorId: string, startAt: string, reason: string, patientId?: string): Observable<Appointment> {
    return this.http.post<Envelope<Appointment>>(this.base, { doctorId, startAt, reason, patientId })
      .pipe(map(r => r.data));
  }

  mine(page = 0, size = 20): Observable<PagedEnvelope<Appointment>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<Appointment>>(`${this.base}/me`, { params });
  }

  /** Clinic-wide day view (staff dashboard). */
  clinicDay(date: string): Observable<Appointment[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<Envelope<Appointment[]>>(`${this.base}/day`, { params })
      .pipe(map(r => r.data));
  }

  doctorDay(doctorId: string, date: string): Observable<Appointment[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<Envelope<Appointment[]>>(`${this.base}/doctor/${doctorId}`, { params })
      .pipe(map(r => r.data));
  }

  /** Requests waiting for the signed-in doctor to accept or decline. */
  doctorRequests(): Observable<Appointment[]> {
    return this.http.get<Envelope<Appointment[]>>(`${this.base}/doctor/requests`)
      .pipe(map(r => r.data));
  }

  /** Doctor's own decision on a request. */
  decide(id: string, decision: 'acceptance' | 'decline'): Observable<Appointment> {
    return this.http.post<Envelope<Appointment>>(`${this.base}/${id}/${decision}`, {})
      .pipe(map(r => r.data));
  }

  transition(id: string, action: 'confirmation' | 'completion' | 'no-show' | 'cancellation'):
      Observable<Appointment> {
    return this.http.post<Envelope<Appointment>>(`${this.base}/${id}/${action}`, {})
      .pipe(map(r => r.data));
  }
}
