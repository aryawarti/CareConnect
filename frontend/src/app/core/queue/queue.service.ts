import { Injectable, NgZone, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Envelope } from '../patients/patient.models';

export type QueueStatus = 'WAITING' | 'CALLED' | 'IN_CONSULTATION' | 'COMPLETED' | 'SKIPPED' | 'LEFT';
export type QueuePriority = 'EMERGENCY' | 'URGENT' | 'NORMAL';

export interface QueueEntry {
  id: string;
  appointmentId: string | null;
  patientId: string;
  doctorId: string;
  patientName: string;
  doctorName: string;
  tokenNumber: string;
  priority: QueuePriority;
  status: QueueStatus;
  complaint: string | null;
  checkedInAt: string;
  calledAt: string | null;
  startedAt: string | null;
  waitedMinutes: number;
  callAttempts: number;
  position: number | null;
  estimatedWaitMinutes: number | null;
}

export interface QueueSnapshot {
  doctorId: string;
  doctorName: string;
  waiting: number;
  averageConsultationMinutes: number;
  nowServing: QueueEntry | null;
  entries: QueueEntry[];
  generatedAt: string;
}

export interface MyQueueStatus {
  inQueue: boolean;
  entry: QueueEntry | null;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class QueueService {
  private readonly http = inject(HttpClient);
  private readonly zone = inject(NgZone);
  private readonly base = '/api/queue';

  /**
   * Live queue over Server-Sent Events. EventSource lives outside Angular's
   * zone, so every message is bounced back in with zone.run() — otherwise the
   * UI updates the model and never repaints.
   */
  stream(doctorId: string): Observable<QueueSnapshot> {
    return new Observable<QueueSnapshot>(subscriber => {
      const source = new EventSource(`${this.base}/stream/${doctorId}`);

      source.addEventListener('queue', event => {
        const snapshot = JSON.parse((event as MessageEvent).data) as QueueSnapshot;
        this.zone.run(() => subscriber.next(snapshot));
      });

      source.onerror = () => {
        // The browser reconnects on its own; surface nothing to the user.
      };

      return () => source.close();
    });
  }

  board(doctorId: string): Observable<QueueSnapshot> {
    return this.http.get<Envelope<QueueSnapshot>>(`${this.base}/board/${doctorId}`)
      .pipe(map(r => r.data));
  }

  myStatus(): Observable<MyQueueStatus> {
    return this.http.get<Envelope<MyQueueStatus>>(`${this.base}/me`).pipe(map(r => r.data));
  }

  checkIn(body: { appointmentId?: string; patientId?: string; doctorId?: string;
                  complaint?: string; priority?: QueuePriority }): Observable<QueueEntry> {
    return this.http.post<Envelope<QueueEntry>>(`${this.base}/check-in`, body)
      .pipe(map(r => r.data));
  }

  walkIn(body: { patientId: string; doctorId: string; patientName: string;
                 doctorName: string; complaint?: string; priority?: QueuePriority }):
      Observable<QueueEntry> {
    return this.http.post<Envelope<QueueEntry>>(`${this.base}/walk-in`, body)
      .pipe(map(r => r.data));
  }

  callNext(doctorId: string): Observable<QueueEntry> {
    return this.http.post<Envelope<QueueEntry>>(`${this.base}/doctor/${doctorId}/call-next`, {})
      .pipe(map(r => r.data));
  }

  action(entryId: string, action: 'recall' | 'start' | 'complete' | 'left' | 'requeue'):
      Observable<QueueEntry> {
    return this.http.post<Envelope<QueueEntry>>(`${this.base}/${entryId}/${action}`, {})
      .pipe(map(r => r.data));
  }

  clinicLive(): Observable<QueueEntry[]> {
    return this.http.get<Envelope<QueueEntry[]>>(`${this.base}/live`).pipe(map(r => r.data));
  }
}
