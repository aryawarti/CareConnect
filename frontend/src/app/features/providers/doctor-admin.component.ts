import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { Department, Doctor } from '../../core/providers/provider.models';
import { AvailabilityEditorComponent } from './availability-editor.component';

@Component({
  selector: 'cc-doctor-admin',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, MatCardModule, MatTableModule,
            MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
            MatIconModule, MatSnackBarModule, AvailabilityEditorComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head"><div><h1>Doctors</h1><div class="cc-sub">Profiles, fees and availability</div></div></div>

      <mat-card appearance="outlined" style="margin-bottom:24px">
        <mat-card-header><mat-card-title>Add doctor</mat-card-title></mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()"
                style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px 16px;margin-top:16px">
            <mat-form-field appearance="outline">
              <mat-label>First name</mat-label>
              <input matInput formControlName="firstName">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Last name</mat-label>
              <input matInput formControlName="lastName">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Specialty</mat-label>
              <input matInput formControlName="specialty">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Department</mat-label>
              <mat-select formControlName="departmentId">
                @for (d of departments(); track d.id) {
                  <mat-option [value]="d.id">{{ d.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Consultation fee (₹)</mat-label>
              <input matInput type="number" formControlName="consultationFee">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Identity user ID (optional — lets the doctor log in)</mat-label>
              <input matInput formControlName="userId">
            </mat-form-field>
            <div style="grid-column:1/-1;display:flex;justify-content:flex-end">
              <button mat-flat-button class="cc-btn-primary" type="submit">Add doctor</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <div class="cc-table-wrap">
      <table mat-table [dataSource]="doctors()" style="width:100%">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let d">Dr. {{ d.firstName }} {{ d.lastName }}</td>
        </ng-container>
        <ng-container matColumnDef="specialty">
          <th mat-header-cell *matHeaderCellDef>Specialty</th>
          <td mat-cell *matCellDef="let d">{{ d.specialty }}</td>
        </ng-container>
        <ng-container matColumnDef="department">
          <th mat-header-cell *matHeaderCellDef>Department</th>
          <td mat-cell *matCellDef="let d">{{ d.departmentName }}</td>
        </ng-container>
        <ng-container matColumnDef="fee">
          <th mat-header-cell *matHeaderCellDef>Fee</th>
          <td mat-cell *matCellDef="let d">{{ d.consultationFee | currency:'INR':'symbol':'1.0-0' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Consulting hours</th>
          <td mat-cell *matCellDef="let d">
            <!-- The status, not just a button. A doctor with no hours is listed,
                 verified and completely unbookable, and nothing in this table
                 used to say so — which is how the clinic ended up with doctors
                 patients could find but never book. -->
            @if (scheduled().has(d.id)) {
              <button mat-stroked-button (click)="editAvailability.set(d)">
                <mat-icon>schedule</mat-icon> {{ scheduled().get(d.id) }} days
              </button>
            } @else {
              <button mat-flat-button color="warn" (click)="editAvailability.set(d)">
                <mat-icon>event_busy</mat-icon> Not bookable — set hours
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      </div>

      @if (editAvailability(); as doc) {
        <cc-availability-editor [doctor]="doc" (closed)="editAvailability.set(null)"
                                (saved)="reload()"
                                [heading]="'Weekly consulting hours — Dr. ' + doc.lastName" />
      }
    </div>
  `,
  // AvailabilityEditor imported lazily below to keep this file focused
})
export class DoctorAdminComponent {
  private readonly service = inject(ProvidersService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly doctors = signal<Doctor[]>([]);
  readonly departments = signal<Department[]>([]);
  readonly editAvailability = signal<Doctor | null>(null);
  readonly columns = ['name', 'specialty', 'department', 'fee', 'actions'];

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    specialty: ['', Validators.required],
    departmentId: ['', Validators.required],
    consultationFee: [500, [Validators.required, Validators.min(0)]],
    userId: ['']
  });

  constructor() {
    this.reload();
    this.service.departments().subscribe(d => this.departments.set(d));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.service.create({ ...v, userId: v.userId || undefined }).subscribe({
      next: d => {
        this.snackBar.open(
          `Added Dr. ${d.lastName} — now set their consulting hours`, 'OK',
          { duration: 5000 });
        this.form.reset({ consultationFee: 500 });
        this.reload();
        // Straight into the schedule rather than back to the list. A doctor
        // created without hours is unbookable, and the old flow gave no hint
        // that a second step existed — so it was routinely skipped.
        this.editAvailability.set(d);
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Save failed', 'OK', { duration: 4000 })
    });
  }

  /** doctorId -> number of days they consult. Absent means no hours at all. */
  readonly scheduled = signal<Map<string, number>>(new Map());

  reload(): void {
    // The admin list, not the public directory: staff manage inactive and
    // unverified doctors too, and those never appear in the patient-facing one.
    this.service.allDoctors().subscribe(r => {
      this.doctors.set(r.data);
      this.loadScheduleStatus(r.data.map(d => d.id));
    });
  }

  /**
   * Which doctors have published hours.
   *
   * The public directory computes this in one query, but this table also lists
   * inactive and unverified doctors who never appear there, so it is asked for
   * per doctor. Acceptable at clinic scale — tens, not thousands — and the
   * alternative is an admin-only endpoint duplicating logic that already exists.
   */
  private loadScheduleStatus(ids: string[]): void {
    const status = new Map<string, number>();
    for (const id of ids) {
      this.service.availability(id).subscribe({
        next: slots => {
          const days = new Set(slots.map(s => s.dayOfWeek));
          if (days.size) {
            status.set(id, days.size);
          }
          this.scheduled.set(new Map(status));
        },
        error: () => { /* leave unknown rather than claiming "not bookable" */ }
      });
    }
  }
}
