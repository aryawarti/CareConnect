import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { Doctor } from '../../core/providers/provider.models';
import { EmptyStateComponent } from '../../shared/ui.components';

/**
 * The administration's gate on self-registered doctors. Nobody reaches
 * patients without a human here checking their qualification and medical
 * registration number — the one control that makes open doctor signup safe.
 */
@Component({
  selector: 'cc-doctor-approvals',
  standalone: true,
  imports: [CurrencyPipe, MatButtonModule, MatIconModule, MatSnackBarModule, EmptyStateComponent],
  template: `
    <div class="cc-page" style="max-width:900px">
      <div class="cc-page-head">
        <div>
          <h1>Doctor applications</h1>
          <div class="cc-sub">Verify credentials before a doctor becomes bookable</div>
        </div>
      </div>

      @if (applications().length) {
        @for (doctor of applications(); track doctor.id) {
          <div class="cc-card" style="margin-bottom:14px">
            <div class="cc-row">
              <div class="cc-stat-icon"><mat-icon>stethoscope</mat-icon></div>
              <div style="flex:1">
                <div style="font-size:17px;font-weight:600">
                  Dr. {{ doctor.firstName }} {{ doctor.lastName }}
                </div>
                <div class="cc-faint">
                  {{ doctor.specialty }} · {{ doctor.departmentName }}
                </div>
              </div>
              <span class="cc-pill warn">PENDING</span>
            </div>

            <div class="cc-divider"></div>

            <div class="cc-grid cc-grid-4">
              <div>
                <div class="cc-faint">Qualification</div>
                <div style="font-weight:600">{{ doctor.qualification || '—' }}</div>
              </div>
              <div>
                <div class="cc-faint">Registration no.</div>
                <div style="font-weight:600" class="cc-mono">{{ doctor.registrationNo || '—' }}</div>
              </div>
              <div>
                <div class="cc-faint">Experience</div>
                <div style="font-weight:600">
                  {{ doctor.experienceYears !== null ? doctor.experienceYears + ' years' : '—' }}
                </div>
              </div>
              <div>
                <div class="cc-faint">Fee</div>
                <div style="font-weight:600">
                  {{ doctor.consultationFee | currency:'INR':'symbol':'1.0-0' }}
                </div>
              </div>
            </div>

            @if (doctor.bio) {
              <p class="cc-muted" style="margin-top:12px;font-size:14px">{{ doctor.bio }}</p>
            }
            @if (doctor.email) {
              <div class="cc-faint" style="margin-top:8px">Contact: {{ doctor.email }}</div>
            }

            <div class="cc-form-actions" style="margin-top:14px">
              <button mat-stroked-button color="warn" (click)="reject(doctor)">
                <mat-icon>close</mat-icon> Reject
              </button>
              <button mat-flat-button class="cc-btn-primary" (click)="approve(doctor)">
                <mat-icon>verified</mat-icon> Approve &amp; publish
              </button>
            </div>
          </div>
        }
        <p class="cc-faint">
          Approving publishes the doctor in the patient-facing directory immediately.
          Rejecting keeps their account but hides them; they see the reason you give.
        </p>
      } @else {
        <cc-empty icon="verified" title="No applications waiting"
                  text="Doctors who sign up will appear here for verification." />
      }
    </div>
  `
})
export class DoctorApprovalsComponent {
  private readonly providers = inject(ProvidersService);
  private readonly snackBar = inject(MatSnackBar);

  readonly applications = signal<Doctor[]>([]);

  constructor() {
    this.reload();
  }

  approve(doctor: Doctor): void {
    this.providers.approve(doctor.id).subscribe({
      next: () => {
        this.snackBar.open(
          `Dr. ${doctor.lastName} is now bookable by patients`, 'OK', { duration: 4000 });
        this.reload();
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }

  reject(doctor: Doctor): void {
    const reason = prompt(`Why is Dr. ${doctor.lastName}'s application rejected?`,
      'Registration number could not be verified');
    if (!reason) { return; }
    this.providers.reject(doctor.id, reason).subscribe({
      next: () => {
        this.snackBar.open('Application rejected', 'OK', { duration: 3000 });
        this.reload();
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }

  private reload(): void {
    this.providers.applications().subscribe(list => this.applications.set(list));
  }
}
