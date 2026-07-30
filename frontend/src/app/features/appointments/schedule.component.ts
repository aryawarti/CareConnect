import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { AuthService } from '../../core/auth/auth.service';
import { Doctor } from '../../core/providers/provider.models';
import { Appointment } from '../../core/appointments/appointment.models';
import { deferredResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/** Day schedule for staff (any doctor) and doctors (defaults to themselves). */
@Component({
  selector: 'cc-schedule',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatTableModule, MatButtonModule, MatChipsModule, MatSnackBarModule,
            SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head"><div><h1>Day schedule</h1><div class="cc-sub">Confirm, complete, or cancel visits</div></div></div>
      <div style="display:flex;gap:16px;flex-wrap:wrap">
        <!-- Staff run the whole clinic and pick any doctor. A doctor only ever
             sees their own day, so there is nothing to choose: offering the
             picker would list colleagues whose schedules the API refuses. -->
        @if (isStaff) {
          <mat-form-field appearance="outline" style="min-width:280px">
            <mat-label>Doctor</mat-label>
            <mat-select [formControl]="doctorCtl">
              @for (d of doctors(); track d.id) {
                <mat-option [value]="d.id">Dr. {{ d.firstName }} {{ d.lastName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input matInput type="date" [formControl]="dateCtl">
        </mat-form-field>
      </div>

      @if (appointments.idle()) {
        <p class="cc-muted">Choose a doctor to see their day.</p>
      } @else if (appointments.loading()) {
        <cc-skeleton [count]="5" label="Loading the day's schedule…" />
      } @else if (appointments.failed()) {
        <cc-error [message]="appointments.error()!" (retry)="appointments.reload()" />
      } @else if (rows().length) {
      <div class="cc-table-wrap" [class.cc-stale]="appointments.refreshing()">
      <table mat-table [dataSource]="rows()" style="width:100%">
        <ng-container matColumnDef="time">
          <th mat-header-cell *matHeaderCellDef>Time</th>
          <td mat-cell *matCellDef="let a">{{ a.startAt | date:'h:mm a' }}</td>
        </ng-container>
        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let a">{{ a.patientName }}</td>
        </ng-container>
        <ng-container matColumnDef="reason">
          <th mat-header-cell *matHeaderCellDef>Reason</th>
          <td mat-cell *matCellDef="let a">{{ a.reason ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let a"><span class="cc-pill" [class]="a.status">{{ a.status }}</span></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let a">
            @if (a.status === 'REQUESTED' && isStaff) {
              <button mat-button (click)="act(a, 'confirmation')">Confirm</button>
            }
            @if (a.status === 'CONFIRMED') {
              <button mat-button (click)="act(a, 'completion')">Complete</button>
              <button mat-button (click)="act(a, 'no-show')">No-show</button>
            }
            @if ((a.status === 'REQUESTED' || a.status === 'CONFIRMED') && isStaff) {
              <button mat-button color="warn" (click)="act(a, 'cancellation')">Cancel</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      </div>
      } @else {
        <cc-empty icon="event_available" title="Nothing booked for this day"
                  text="Pick another date, or book a visit from the patient's record." />
      }
    </div>
  `
})
export class ScheduleComponent {
  private readonly providers = inject(ProvidersService);
  private readonly service = inject(AppointmentsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly columns = ['time', 'patient', 'reason', 'status', 'actions'];
  readonly doctors = signal<Doctor[]>([]);
  readonly isStaff = ['STAFF', 'ADMIN'].some(r => this.auth.user()?.roles.includes(r));

  readonly doctorCtl = new FormControl('', { nonNullable: true });
  readonly dateCtl = new FormControl(new Date().toISOString().slice(0, 10), { nonNullable: true });

  /** Deferred: idle until a doctor is resolved or chosen. */
  readonly appointments = deferredResource(() =>
    this.service.doctorDay(this.doctorCtl.value, this.dateCtl.value));

  constructor() {
    if (this.isStaff) {
      this.providers.directory('', 0, 100).subscribe(r => this.doctors.set(r.data));
    } else {
      // A doctor's own schedule, resolved from their account rather than chosen.
      this.providers.me().subscribe({
        next: doctor => this.doctorCtl.setValue(doctor.id),
        error: () => this.snackBar.open(
          'No doctor profile is linked to your account', 'OK', { duration: 4000 })
      });
    }
    this.doctorCtl.valueChanges.subscribe(() => this.reload());
    this.dateCtl.valueChanges.subscribe(() => this.reload());
  }

  /** Rows currently on screen, including stale ones during a refresh. */
  rows(): Appointment[] {
    return this.appointments.value() ?? [];
  }

  act(a: Appointment, action: 'confirmation' | 'completion' | 'no-show' | 'cancellation'): void {
    this.service.transition(a.id, action).subscribe({
      next: () => this.reload(),
      error: err => this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 })
    });
  }

  private reload(): void {
    if (this.doctorCtl.value && this.dateCtl.value) {
      this.appointments.reload();
    } else {
      this.appointments.reset();
    }
  }
}
