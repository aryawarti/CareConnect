import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { humanizeError, isSystemError } from '../../core/http/http-status';

/**
 * Public signup for the two roles that can join on their own:
 *  - a patient, ready to book immediately;
 *  - a doctor, who then submits credentials for the hospital to verify.
 * Front-desk and administrator accounts are created only by an administrator.
 */
@Component({
  selector: 'cc-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule,
            MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <mat-card class="cc-auth-card" appearance="outlined">
      <mat-card-header style="display:block;text-align:center;padding-top:8px">
        <mat-card-title style="font-size:22px">Create your account</mat-card-title>
        <mat-card-subtitle>Join CareConnect</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <div class="role-picker">
          <button type="button" class="role" [class.selected]="role() === 'PATIENT'"
                  (click)="role.set('PATIENT')">
            <mat-icon>personal_injury</mat-icon>
            <div class="role-title">I'm a patient</div>
            <div class="role-sub">Book visits, see my records</div>
          </button>
          <button type="button" class="role" [class.selected]="role() === 'DOCTOR'"
                  (click)="role.set('DOCTOR')">
            <mat-icon>stethoscope</mat-icon>
            <div class="role-title">I'm a doctor</div>
            <div class="role-sub">Apply to practise here</div>
          </button>
        </div>

        @if (role() === 'DOCTOR') {
          <div class="notice">
            <mat-icon>verified_user</mat-icon>
            <span>
              Next you'll submit your qualification and medical registration number.
              The hospital verifies these before patients can find or book you.
            </span>
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()"
              style="display:flex;flex-direction:column;gap:8px;margin-top:16px">
          <mat-form-field appearance="outline" class="cc-full-width">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email">
          </mat-form-field>
          <mat-form-field appearance="outline" class="cc-full-width">
            <mat-label>Password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password">
            <mat-hint>10+ characters, with upper case, lower case and a digit</mat-hint>
            @if (form.controls.password.hasError('pattern') || form.controls.password.hasError('minlength')) {
              <mat-error>10+ chars with upper, lower and a digit</mat-error>
            }
          </mat-form-field>
          @if (error(); as message) {
            <div class="cc-alert cc-alert-error" role="alert">
              <mat-icon>error_outline</mat-icon>
              <span>{{ message }}</span>
            </div>
          }
          <button mat-flat-button class="cc-btn-primary" type="submit" [disabled]="loading()" style="height:44px">
            @if (loading()) {
              <span style="display:inline-flex;align-items:center;gap:8px">
                <mat-spinner diameter="20" /> Creating your account…
              </span>
            } @else {
              {{ role() === 'DOCTOR' ? 'Continue as doctor' : 'Create patient account' }}
            }
          </button>
        </form>
      </mat-card-content>
      <mat-card-actions>
        <a mat-button routerLink="/login">Already have an account? Sign in</a>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    .role-picker { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 8px; }
    .role {
      background: var(--cc-surface); border: 1.5px solid var(--cc-line); border-radius: 12px;
      padding: 14px 10px; cursor: pointer; font: inherit; text-align: center;
      transition: border-color .15s, background .15s;
    }
    .role:hover { border-color: var(--cc-primary); }
    .role.selected { border-color: var(--cc-primary); background: var(--cc-primary-light); }
    .role mat-icon { color: var(--cc-primary); }
    .role-title { font-weight: 600; margin-top: 6px; font-size: 14px; }
    .role-sub { font-size: 11.5px; color: var(--cc-ink-soft); margin-top: 2px; }
    .notice {
      display: flex; gap: 10px; align-items: flex-start; margin-top: 14px;
      background: var(--cc-info-bg); color: var(--cc-info);
      padding: 12px 14px; border-radius: 10px; font-size: 13px; line-height: 1.5;
    }
    .notice mat-icon { flex: none; }
  `]
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = signal<'PATIENT' | 'DOCTOR'>('PATIENT');
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10),
                    Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).*$/)]]
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.register(email, password, this.role()).subscribe({
      // Doctors go straight to the credentials form; patients to their dashboard.
      next: () => this.router.navigate([this.role() === 'DOCTOR' ? '/doctor-application' : '/']),
      error: err => {
        this.loading.set(false);
        this.error.set(isSystemError(err)
          ? null
          : humanizeError(err, 'Registration failed. Please check your details and try again.'));
      }
    });
  }
}
