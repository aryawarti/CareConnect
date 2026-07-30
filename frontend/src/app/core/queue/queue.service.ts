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

/** Full picture, including names and complaints. Authenticated requests only. */
export interface QueueSnapshot {
  doctorId: string;
  doctorName: string;
  waiting: number;
  averageConsultationMinutes: number;
  nowServing: QueueEntry | null;
  entries: QueueEntry[];
  generatedAt: string;
}

/**
 * What the public lobby board and the SSE stream carry. Redacted server-side:
 * a token, a given name, a wait. No surname, no complaint, no patient id.
 */
export interface BoardEntry {
  id: string;
  tokenNumber: string;
  status: QueueStatus;
  priority: QueuePriority;
  givenName: string;
  position: number | null;
  estimatedWaitMinutes: number | null;
}

export interface BoardSnapshot {
  doctorId: string;
  doctorName: string;
  waiting: number;
  averageConsultationMinutes: number;
  nowServing: BoardEntry | null;
  entries: BoardEntry[];
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
   * Live queue over Server-Sent Events.
   *
   * The stream is unauthenticated — EventSource cannot send an Authorization
   * header — so it only ever carries the redacted board payload. Screens that
   * need names or complaints use this as a change signal and call console()
   * for the real data. EventSource also lives outside Angular's zone, so every
   * message is bounced back in with zone.run() or the UI never repaints.
   */
  stream(doctorId: string): Observable<BoardSnapshot> {
    return new Observable<BoardSnapshot>(subscriber => {
      const source = new EventSource(`${this.base}/stream/${doctorId}`);

      source.addEventListener('queue', event => {
        const snapshot = JSON.parse((event as MessageEvent).data) as BoardSnapshot;
        this.zone.run(() => subscriber.next(snapshot));
      });

      source.onerror = () => {
        // The browser reconnects on its own; surface nothing to the user.
      };

      return () => source.close();
    });
  }

  /** Public lobby board snapshot — same redacted shape as the stream. */
  board(doctorId: string): Observable<BoardSnapshot> {
    return this.http.get<Envelope<BoardSnapshot>>(`${this.base}/board/${doctorId}`)
      .pipe(map(r => r.data));
  }

  /** Full queue for a doctor's console. Authenticated; own queue or staff. */
  console(doctorId: string): Observable<QueueSnapshot> {
    return this.http.get<Envelope<QueueSnapshot>>(`${this.base}/console/${doctorId}`)
      .pipe(map(r => r.data));
  }

  myStatus(): Observable<MyQueueStatus> {
    return this.http.get<Envelope<MyQueueStatus>>(`${this.base}/me`).pipe(map(r => r.data));
  }

  /**
   * Join today's queue. `doctorId` is required — the server resolves the display
   * names from it. `patientId` is honoured only for staff callers; a patient is
   * always checked in as themselves, so sending it as a patient does nothing.
   */
  checkIn(body: { doctorId: string; appointmentId?: string; patientId?: string;
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
