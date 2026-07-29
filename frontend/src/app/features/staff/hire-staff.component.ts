import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { StaffService, StaffRole } from '../../core/staff/staff.service';
import { Department, Slot } from '../../core/providers/provider.models';

/**
 * Hiring a staff member — one screen for what used to be three disconnected
 * API calls. The administrator picks a role, fills the details, and the system
 * creates the login, the professional profile and the working schedule
 * together, then shows the credentials to hand over.
 */
@Component({
  selector: 'cc-hire-staff',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatIconModule, MatCheckboxModule, MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:900px">
      <div class="cc-page-head">
        <div>
          <h1>Add a staff member</h1>
          <div class="cc-sub">Creates their login and their role in the hospital</div>
        </div>
      </div>

      @if (created(); as result) {
        <div class="cc-card" style="border-left:4px solid var(--cc-ok)">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-ok)">check_circle</mat-icon>
            <h3 style="flex:1">{{ result.name }} can now sign in</h3>
          </div>
          <p class="cc-muted">Hand these credentials over — they should change the password after first login.</p>
          <div class="cc-card cc-card-tight" style="background:var(--cc-canvas)">
            <div><span class="cc-faint">Email</span> &nbsp; <strong>{{ result.email }}</strong></div>
            <div style="margin-top:6px">
              <span class="cc-faint">Temporary password</span> &nbsp;
              <strong class="cc-mono">{{ result.password }}</strong>
            </div>
            <div style="margin-top:6px"><span class="cc-faint">Role</span> &nbsp;
              <span class="cc-pill">{{ result.role }}</span>
            </div>
          </div>
          <div class="cc-form-actions" style="margin-top:16px">
            <button mat-stroked-button (click)="reset()">Add another</button>
            <button mat-flat-button class="cc-btn-primary" (click)="goToDirectory()">
              Staff directory
            </button>
          </div>
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()">
          <!-- Role -->
          <div class="cc-card">
            <h3>1 · What is their role?</h3>
            <div class="cc-grid cc-grid-4" style="margin-top:14px">
              @for (option of roleOptions; track option.value) {
                <button type="button" class="role-card"
                        [class.selected]="form.controls.role.value === option.value"
                        (click)="form.controls.role.setValue(option.value)">
                  <mat-icon>{{ option.icon }}</mat-icon>
                  <div class="role-name">{{ option.label }}</div>
                  <div class="role-desc">{{ option.description }}</div>
                </button>
              }
            </div>
          </div>

          <!-- Account -->
          <div class="cc-card">
            <h3>2 · Login details</h3>
            <div class="cc-form-grid" style="margin-top:14px">
              <mat-form-field appearance="outline">
                <mat-label>Work email</mat-label>
                <input matInput type="email" formControlName="email"
                       placeholder="dr.sharma@hospital.org">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Temporary password</mat-label>
                <input matInput formControlName="password">
                <button mat-icon-button matSuffix type="button" (click)="generatePassword()"
                        title="Generate">
                  <mat-icon>casino</mat-icon>
                </button>
                <mat-hint>10+ chars, upper, lower and a digit</mat-hint>
              </mat-form-field>
            </div>
          </div>

          <!-- Doctor specifics -->
          @if (isDoctor()) {
            <div class="cc-card">
              <h3>3 · Professional details</h3>
              <div class="cc-form-grid" style="margin-top:14px">
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
                  <input matInput formControlName="specialty" placeholder="Cardiology">
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
                  <mat-label>Phone</mat-label>
                  <input matInput formControlName="phone">
                </mat-form-field>
              </div>
            </div>

            <div class="cc-card">
              <h3>4 · Working hours</h3>
              <p class="cc-muted" style="font-size:14px">
                Patients can only book inside these hours. You can fine-tune them later.
              </p>
              <div class="cc-row" style="margin-top:12px;gap:18px">
                <div>
                  <div class="cc-faint" style="margin-bottom:6px">Working days</div>
                  <div class="cc-row" style="gap:6px">
                    @for (day of days; track day.value) {
                      <button type="button" class="day-chip"
                              [class.on]="selectedDays().includes(day.value)"
                              (click)="toggleDay(day.value)">{{ day.short }}</button>
                    }
                  </div>
                </div>
                <mat-form-field appearance="outline" style="width:150px">
                  <mat-label>Morning from</mat-label>
                  <input matInput type="time" formControlName="morningFrom">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:150px">
                  <mat-label>Morning to</mat-label>
                  <input matInput type="time" formControlName="morningTo">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:150px">
                  <mat-label>Evening from</mat-label>
                  <input matInput type="time" formControlName="eveningFrom">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:150px">
                  <mat-label>Evening to</mat-label>
                  <input matInput type="time" formControlName="eveningTo">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:140px">
                  <mat-label>Slot length</mat-label>
                  <mat-select formControlName="slotMinutes">
                    @for (m of [10, 15, 20, 30, 45, 60]; track m) {
                      <mat-option [value]="m">{{ m }} minutes</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </div>
            </div>
          }

          <div class="cc-form-actions">
            <button mat-button type="button" (click)="goToDirectory()">Cancel</button>
            <button mat-flat-button class="cc-btn-primary" type="submit"
                    [disabled]="saving()" style="height:44px;padding:0 24px">
              <mat-icon>person_add</mat-icon>
              {{ isDoctor() ? 'Hire doctor' : 'Create account' }}
            </button>
          </div>
        </form>
      }
    </div>
  `,
  styles: [`
    .role-card {
      background: var(--cc-surface); border: 1.5px solid var(--cc-line);
      border-radius: 14px; padding: 16px 14px; cursor: pointer; text-align: left;
      font: inherit; transition: border-color .15s, background .15s;
    }
    .role-card:hover { border-color: var(--cc-primary); }
    .role-card.selected { border-color: var(--cc-primary); background: var(--cc-primary-light); }
    .role-card mat-icon { color: var(--cc-primary); }
    .role-name { font-weight: 600; margin-top: 8px; }
    .role-desc { font-size: 12px; color: var(--cc-ink-soft); margin-top: 3px; line-height: 1.4; }
    .day-chip {
      width: 40px; height: 36px; border-radius: 9px; cursor: pointer; font: inherit;
      font-weight: 600; background: var(--cc-surface); border: 1.5px solid var(--cc-line);
      color: var(--cc-ink-soft);
    }
    .day-chip.on {
      background: var(--cc-primary); border-color: var(--cc-primary); color: #fff;
    }
  `]
})
export class HireStaffComponent {
  private readonly fb = inject(FormBuilder);
  private readonly staff = inject(StaffService);
  private readonly providers = inject(ProvidersService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly departments = signal<Department[]>([]);
  readonly saving = signal(false);
  readonly selectedDays = signal<number[]>([1, 2, 3, 4, 5]);
  readonly created = signal<{ name: string; email: string; password: string; role: string } | null>(null);

  readonly roleOptions = [
    { value: 'DOCTOR' as StaffRole, icon: 'stethoscope', label: 'Doctor',
      description: 'Consults patients, writes prescriptions, signs charts' },
    { value: 'STAFF' as StaffRole, icon: 'support_agent', label: 'Front desk',
      description: 'Registers patients, manages the queue and payments' },
    { value: 'ADMIN' as StaffRole, icon: 'admin_panel_settings', label: 'Administrator',
      description: 'Hires staff, sets fees, sees hospital-wide analytics' },
    { value: 'PATIENT' as StaffRole, icon: 'personal_injury', label: 'Patient account',
      description: 'For registering a patient who cannot self-register' },
  ];

  readonly days = [
    { value: 1, short: 'Mon' }, { value: 2, short: 'Tue' }, { value: 3, short: 'Wed' },
    { value: 4, short: 'Thu' }, { value: 5, short: 'Fri' }, { value: 6, short: 'Sat' },
    { value: 7, short: 'Sun' },
  ];

  readonly form = this.fb.nonNullable.group({
    role: ['DOCTOR' as StaffRole, Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: [this.randomPassword(), [Validators.required, Validators.minLength(10)]],
    firstName: [''],
    lastName: [''],
    specialty: [''],
    departmentId: [''],
    consultationFee: [500],
    phone: [''],
    morningFrom: ['09:00'],
    morningTo: ['13:00'],
    eveningFrom: ['14:00'],
    eveningTo: ['17:00'],
    slotMinutes: [30],
  });

  readonly isDoctor = computed(() => this.form.controls.role.value === 'DOCTOR');

  constructor() {
    this.providers.departments().subscribe(d => {
      this.departments.set(d);
      if (d.length && !this.form.controls.departmentId.value) {
        this.form.controls.departmentId.setValue(d[0].id);
      }
    });
  }

  toggleDay(day: number): void {
    this.selectedDays.update(days =>
      days.includes(day) ? days.filter(d => d !== day) : [...days, day].sort());
  }

  generatePassword(): void {
    this.form.controls.password.setValue(this.randomPassword());
  }

  submit(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid || (this.isDoctor() && (!value.firstName || !value.lastName || !value.specialty))) {
      this.form.markAllAsTouched();
      this.snackBar.open('Please complete the required fields', 'OK', { duration: 3000 });
      return;
    }
    this.saving.set(true);

    const slots: Slot[] = this.isDoctor()
      ? this.selectedDays().flatMap(day => ([
          { dayOfWeek: day, startTime: `${value.morningFrom}:00`,
            endTime: `${value.morningTo}:00`, slotMinutes: value.slotMinutes },
          { dayOfWeek: day, startTime: `${value.eveningFrom}:00`,
            endTime: `${value.eveningTo}:00`, slotMinutes: value.slotMinutes },
        ]))
      : [];

    this.staff.hire({
      email: value.email, password: value.password, role: value.role,
      firstName: value.firstName, lastName: value.lastName, specialty: value.specialty,
      departmentId: value.departmentId, consultationFee: value.consultationFee,
      phone: value.phone, slots
    }).subscribe({
      next: result => {
        this.saving.set(false);
        this.created.set({
          name: result.doctor ? `Dr. ${result.doctor.firstName} ${result.doctor.lastName}`
                              : result.user.email,
          email: value.email, password: value.password, role: value.role
        });
      },
      error: err => {
        this.saving.set(false);
        this.snackBar.open(err?.error?.detail ?? 'Could not create the account', 'OK',
          { duration: 5000 });
      }
    });
  }

  reset(): void {
    this.created.set(null);
    this.form.patchValue({ email: '', password: this.randomPassword(), firstName: '',
                           lastName: '', specialty: '', phone: '' });
  }

  goToDirectory(): void {
    this.router.navigate(['/staff']);
  }

  private randomPassword(): string {
    const words = ['Care', 'Heal', 'Ward', 'Pulse', 'Medic', 'Clinic'];
    const word = words[Math.floor(Math.random() * words.length)];
    return `${word}${Math.floor(1000 + Math.random() * 9000)}x`;
  }
}
