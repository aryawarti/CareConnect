import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { Department, Doctor } from '../../core/providers/provider.models';

/**
 * A self-registered doctor's credentials submission, and the waiting room for
 * the hospital's decision. Shown automatically to any DOCTOR account that has
 * no profile yet — so a doctor who signs up is never left staring at an empty
 * dashboard wondering what to do.
 */
@Component({
  selector: 'cc-doctor-application',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatIconModule, MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:760px">
      @if (profile(); as doctor) {
        <!-- Already applied: show where the application stands -->
        <div class="cc-card" style="text-align:center;padding:34px">
          @if (doctor.verification === 'PENDING') {
            <mat-icon class="big" style="color:var(--cc-warn)">hourglass_top</mat-icon>
            <h2>Your application is under review</h2>
            <p class="cc-muted">
              The hospital administration is verifying your qualification and registration
              number. You'll be able to receive appointments as soon as they approve it.
            </p>
          } @else if (doctor.verification === 'REJECTED') {
            <mat-icon class="big" style="color:var(--cc-danger)">cancel</mat-icon>
            <h2>Application not approved</h2>
            <p class="cc-muted">{{ doctor.rejectionReason || 'Please contact the hospital administration.' }}</p>
          } @else {
            <mat-icon class="big" style="color:var(--cc-ok)">verified</mat-icon>
            <h2>You're verified</h2>
            <p class="cc-muted">Patients can now find and book you.</p>
            <button mat-flat-button class="cc-btn-primary" (click)="goToDashboard()">
              Go to my dashboard
            </button>
          }

          <div class="cc-divider"></div>
          <div class="cc-row" style="justify-content:center;gap:26px;text-align:left">
            <div>
              <div class="cc-faint">Name</div>
              <div style="font-weight:600">Dr. {{ doctor.firstName }} {{ doctor.lastName }}</div>
            </div>
            <div>
              <div class="cc-faint">Specialty</div>
              <div style="font-weight:600">{{ doctor.specialty }}</div>
            </div>
            <div>
              <div class="cc-faint">Department</div>
              <div style="font-weight:600">{{ doctor.departmentName }}</div>
            </div>
            <div>
              <div class="cc-faint">Registration</div>
              <div style="font-weight:600">{{ doctor.registrationNo || '—' }}</div>
            </div>
          </div>
        </div>
      } @else {
        <!-- First time: collect credentials -->
        <div class="cc-page-head">
          <div>
            <h1>Tell us about your practice</h1>
            <div class="cc-sub">
              The hospital verifies these details before patients can book you
            </div>
          </div>
        </div>

        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="cc-card">
            <h3>Your details</h3>
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
            </div>
          </div>

          <div class="cc-card">
            <h3>Credentials</h3>
            <p class="cc-muted" style="font-size:14px">
              These are what the administration checks. Be accurate.
            </p>
            <div class="cc-form-grid" style="margin-top:12px">
              <mat-form-field appearance="outline">
                <mat-label>Highest qualification</mat-label>
                <input matInput formControlName="qualification" placeholder="MD, Cardiology">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Medical registration number</mat-label>
                <input matInput formControlName="registrationNo" placeholder="MCI-123456">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Years of experience</mat-label>
                <input matInput type="number" formControlName="experienceYears">
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
            <mat-form-field appearance="outline" class="cc-full-width">
              <mat-label>About you (shown to patients)</mat-label>
              <textarea matInput rows="3" formControlName="bio"
                        placeholder="Special interests, languages spoken…"></textarea>
            </mat-form-field>
          </div>

          <div class="cc-form-actions">
            <button mat-flat-button class="cc-btn-primary" type="submit"
                    [disabled]="saving()" style="height:44px;padding:0 24px">
              <mat-icon>send</mat-icon> Submit for verification
            </button>
          </div>
        </form>
      }
    </div>
  `,
  styles: [`
    .big { font-size: 52px; width: 52px; height: 52px; margin-bottom: 8px; }
    h2 { margin-bottom: 6px; }
    p.cc-muted { max-width: 480px; margin: 0 auto 16px; line-height: 1.55; }
  `]
})
export class DoctorApplicationComponent {
  private readonly fb = inject(FormBuilder);
  private readonly providers = inject(ProvidersService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly departments = signal<Department[]>([]);
  readonly profile = signal<Doctor | null>(null);
  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    specialty: ['', Validators.required],
    departmentId: ['', Validators.required],
    qualification: ['', Validators.required],
    registrationNo: ['', Validators.required],
    experienceYears: [3],
    consultationFee: [500],
    phone: [''],
    bio: ['']
  });

  constructor() {
    this.providers.departments().subscribe(d => {
      this.departments.set(d);
      if (d.length && !this.form.controls.departmentId.value) {
        this.form.controls.departmentId.setValue(d[0].id);
      }
    });
    // Already applied? Then show the status instead of the form.
    this.providers.me().subscribe({
      next: doctor => this.profile.set(doctor),
      error: () => this.profile.set(null)
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.providers.apply(this.form.getRawValue()).subscribe({
      next: doctor => {
        this.saving.set(false);
        this.profile.set(doctor);
        this.snackBar.open('Application submitted for verification', 'OK', { duration: 4000 });
      },
      error: err => {
        this.saving.set(false);
        this.snackBar.open(err?.error?.detail ?? 'Could not submit the application', 'OK',
          { duration: 5000 });
      }
    });
  }

  goToDashboard(): void {
    this.router.navigate(['/']);
  }
}
