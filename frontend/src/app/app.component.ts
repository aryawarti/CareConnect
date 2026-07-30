import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule, MatIconRegistry } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from './core/auth/auth.service';

/**
 * Navigation is built from one declarative table rather than a pile of
 * conditionals, so each role sees exactly its own work and nothing else:
 *
 *   PATIENT  — book, my queue, my visits, my records, my invoices
 *   DOCTOR   — my day, live queue, my patients, charts
 *   FRONT DESK / ADMIN — clinic day, queue, patients, billing, staff
 *
 * Anything a role cannot act on is simply absent, not disabled.
 */
@Component({
  selector: 'cc-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule,
            MatIconModule, MatMenuModule],
  template: `
    <mat-toolbar class="cc-toolbar">
      <a class="cc-brand" [routerLink]="auth.isAuthenticated() ? '/' : '/welcome'">
        <mat-icon>health_and_safety</mat-icon> CareConnect
      </a>
      <span style="flex:1 1 auto"></span>

      @if (auth.user(); as user) {
        @for (item of navigation(); track item.path) {
          <a mat-button [routerLink]="item.path" routerLinkActive="active"
             [routerLinkActiveOptions]="{ exact: item.exact === true }">
            <mat-icon>{{ item.icon }}</mat-icon> {{ item.label }}
          </a>
        }

        <div class="cc-user">
          <button mat-button [matMenuTriggerFor]="menu">
            <mat-icon>account_circle</mat-icon>
            {{ user.email.split('@')[0] }}
          </button>
          <mat-menu #menu="matMenu">
            <div style="padding:10px 16px;border-bottom:1px solid var(--cc-line)">
              <div style="font-weight:600;font-size:13px">{{ user.email }}</div>
              <div class="cc-faint">{{ roleLabel() }}</div>
            </div>
            @for (item of profileMenu(); track item.path) {
              <a mat-menu-item [routerLink]="item.path">
                <mat-icon>{{ item.icon }}</mat-icon> {{ item.label }}
              </a>
            }
            <button mat-menu-item (click)="logout()">
              <mat-icon>logout</mat-icon> Sign out
            </button>
          </mat-menu>
        </div>
      } @else {
        <a mat-button routerLink="/doctors"><mat-icon>stethoscope</mat-icon> Find a doctor</a>
        <a mat-button routerLink="/login">Sign in</a>
        <a mat-flat-button class="cc-btn-primary" routerLink="/register">Register</a>
      }
    </mat-toolbar>

    <router-outlet />
  `
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    // Icons come from Material Symbols (see index.html): the classic
    // "Material Icons" font lacks newer glyphs like `stethoscope`.
    inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
  }

  readonly navigation = computed(() => {
    const roles = this.auth.user()?.roles ?? [];
    const isAdminOrStaff = roles.includes('ADMIN') || roles.includes('STAFF');
    const isDoctor = roles.includes('DOCTOR');
    const isPatient = roles.includes('PATIENT') && !isDoctor && !isAdminOrStaff;

    if (isAdminOrStaff) {
      return [
        { path: '/', icon: 'dashboard', label: 'Dashboard', exact: true },
        { path: '/queue', icon: 'groups_2', label: 'Live queue' },
        { path: '/schedule', icon: 'calendar_month', label: 'Appointments' },
        { path: '/patients', icon: 'groups', label: 'Patients' },
        { path: '/billing', icon: 'receipt_long', label: 'Billing' },
        { path: '/staff', icon: 'badge', label: 'Staff' },
      ];
    }
    if (isDoctor) {
      return [
        { path: '/', icon: 'dashboard', label: 'My day', exact: true },
        { path: '/queue', icon: 'groups_2', label: 'Live queue' },
        { path: '/records', icon: 'clinical_notes', label: 'Charts' },
      ];
    }
    if (isPatient) {
      return [
        { path: '/', icon: 'dashboard', label: 'Dashboard', exact: true },
        { path: '/doctors', icon: 'stethoscope', label: 'Find a doctor' },
        { path: '/my-queue', icon: 'hourglass_top', label: 'My queue' },
        { path: '/my-appointments', icon: 'event', label: 'Appointments' },
        { path: '/my-records', icon: 'clinical_notes', label: 'Records' },
        { path: '/my-record-access', icon: 'shield_person', label: 'Who saw my records' },
        { path: '/my-invoices', icon: 'receipt_long', label: 'Invoices' },
      ];
    }
    return [{ path: '/', icon: 'dashboard', label: 'Dashboard', exact: true }];
  });

  readonly profileMenu = computed(() => {
    const roles = this.auth.user()?.roles ?? [];
    if (roles.includes('DOCTOR')) {
      return [{ path: '/doctor-application', icon: 'badge', label: 'My professional profile' }];
    }
    if (roles.includes('PATIENT') && !roles.includes('ADMIN') && !roles.includes('STAFF')) {
      return [{ path: '/my-profile', icon: 'badge', label: 'My profile' }];
    }
    if (roles.includes('ADMIN') || roles.includes('STAFF')) {
      return [{ path: '/doctor-approvals', icon: 'verified', label: 'Doctor applications' }];
    }
    return [];
  });

  readonly roleLabel = computed(() => {
    const roles = this.auth.user()?.roles ?? [];
    if (roles.includes('ADMIN')) { return 'Administrator'; }
    if (roles.includes('STAFF')) { return 'Front desk'; }
    if (roles.includes('DOCTOR')) { return 'Doctor'; }
    return 'Patient';
  });

  logout(): void {
    // Navigate either way. AuthService clears local session state before the
    // request goes out, so the user is already signed out client-side — leaving
    // them on an authenticated screen because the server-side revocation call
    // failed would be the worse outcome of the two.
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/welcome']),
      error: () => this.router.navigate(['/welcome'])
    });
  }
}
