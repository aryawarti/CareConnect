import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, switchMap } from 'rxjs';
import { Envelope, PagedEnvelope } from '../patients/patient.models';
import { Doctor, Slot } from '../providers/provider.models';

export type StaffRole = 'ADMIN' | 'DOCTOR' | 'STAFF' | 'PATIENT';

export interface StaffUser {
  id: string;
  email: string;
  roles: StaffRole[];
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  createdAt: string;
}

export interface HireRequest {
  email: string;
  password: string;
  role: StaffRole;
  // Doctor-only professional details
  firstName?: string;
  lastName?: string;
  specialty?: string;
  departmentId?: string;
  consultationFee?: number;
  phone?: string;
  slots?: Slot[];
}

@Injectable({ providedIn: 'root' })
export class StaffService {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 50): Observable<PagedEnvelope<StaffUser>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedEnvelope<StaffUser>>('/api/users', { params });
  }

  /**
   * Hiring, as one action from the administrator's point of view.
   *
   * Under the hood it is three calls across two bounded contexts — identity
   * owns the login, provider owns the professional profile and schedule —
   * chained here rather than hidden behind a new backend endpoint. That keeps
   * each service's API honest about what it owns; the *workflow* of hiring is
   * a front-office concern, so the front office composes it.
   */
  hire(request: HireRequest): Observable<{ user: StaffUser; doctor: Doctor | null }> {
    const account$ = this.http.post<Envelope<StaffUser>>('/api/users', {
      email: request.email, password: request.password, roles: [request.role]
    }).pipe(map(r => r.data));

    if (request.role !== 'DOCTOR') {
      return account$.pipe(map(user => ({ user, doctor: null })));
    }

    return account$.pipe(
      switchMap(user =>
        this.http.post<Envelope<Doctor>>('/api/providers/doctors', {
          firstName: request.firstName, lastName: request.lastName,
          specialty: request.specialty, departmentId: request.departmentId,
          consultationFee: request.consultationFee, email: request.email,
          phone: request.phone, userId: user.id
        }).pipe(
          switchMap(created => {
            const doctor = created.data;
            if (!request.slots?.length) {
              return [{ user, doctor }];
            }
            return this.http.put<Envelope<Slot[]>>(
              `/api/providers/doctors/${doctor.id}/availability`, { slots: request.slots }
            ).pipe(map(() => ({ user, doctor })));
          })
        )
      )
    );
  }

  deactivate(userId: string): Observable<StaffUser> {
    return this.http.post<Envelope<StaffUser>>(`/api/users/${userId}/deactivate`, {})
      .pipe(map(r => r.data));
  }

  activate(userId: string): Observable<StaffUser> {
    return this.http.post<Envelope<StaffUser>>(`/api/users/${userId}/activate`, {})
      .pipe(map(r => r.data));
  }

  resetPassword(userId: string, newPassword: string): Observable<StaffUser> {
    return this.http.post<Envelope<StaffUser>>(`/api/users/${userId}/password`, { newPassword })
      .pipe(map(r => r.data));
  }
}
