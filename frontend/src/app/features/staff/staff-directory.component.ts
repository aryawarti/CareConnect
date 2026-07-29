import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StaffService, StaffUser } from '../../core/staff/staff.service';
import { StatComponent, EmptyStateComponent } from '../../shared/ui.components';

/** Everyone with access to the hospital's systems, and what they can do. */
@Component({
  selector: 'cc-staff-directory',
  standalone: true,
  imports: [DatePipe, RouterLink, ReactiveFormsModule, MatButtonModule, MatIconModule,
            MatMenuModule, MatFormFieldModule, MatInputModule, MatSnackBarModule,
            StatComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div>
          <h1>Hospital staff</h1>
          <div class="cc-sub">Accounts, roles and access</div>
        </div>
        <span class="cc-spacer"></span>
        <a mat-flat-button class="cc-btn-primary" routerLink="/staff/new" style="height:44px">
          <mat-icon>person_add</mat-icon> Add staff member
        </a>
      </div>

      <div class="cc-grid cc-grid-4" style="margin-bottom:20px">
        <cc-stat icon="stethoscope" [value]="countOf('DOCTOR')" label="Doctors" />
        <cc-stat icon="support_agent" [value]="countOf('STAFF')" label="Front desk" tone="info" />
        <cc-stat icon="admin_panel_settings" [value]="countOf('ADMIN')" label="Administrators"
                 tone="accent" />
        <cc-stat icon="personal_injury" [value]="countOf('PATIENT')" label="Patient accounts"
                 tone="ok" />
      </div>

      <mat-form-field appearance="outline" class="cc-full-width" style="max-width:420px">
        <mat-label>Search by email</mat-label>
        <input matInput [formControl]="search">
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>

      @if (filtered().length) {
        <div class="cc-table-wrap">
          @for (member of filtered(); track member.id) {
            <div class="staff-row">
              <div class="avatar" [class.inactive]="member.status !== 'ACTIVE'">
                <mat-icon>{{ iconFor(member) }}</mat-icon>
              </div>
              <div style="flex:1;min-width:200px">
                <div style="font-weight:600">{{ member.email }}</div>
                <div class="cc-faint">Joined {{ member.createdAt | date:'mediumDate' }}</div>
              </div>
              <div class="cc-row" style="gap:6px">
                @for (role of member.roles; track role) {
                  <span class="cc-pill">{{ label(role) }}</span>
                }
              </div>
              <span class="cc-pill" [class.ok]="member.status === 'ACTIVE'"
                    [class.danger]="member.status !== 'ACTIVE'">{{ member.status }}</span>
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                @if (member.status === 'ACTIVE') {
                  <button mat-menu-item (click)="setActive(member, false)">
                    <mat-icon>block</mat-icon> Revoke access
                  </button>
                } @else {
                  <button mat-menu-item (click)="setActive(member, true)">
                    <mat-icon>check_circle</mat-icon> Restore access
                  </button>
                }
                <button mat-menu-item (click)="resetPassword(member)">
                  <mat-icon>key</mat-icon> Reset password
                </button>
              </mat-menu>
            </div>
          }
        </div>
        <p class="cc-faint" style="margin-top:12px">
          Revoking access disables the login and ends active sessions — the account is never
          deleted, because a departed doctor's name still appears on every chart they signed.
        </p>
      } @else {
        <cc-empty icon="badge" title="No staff match" text="Try a different search." />
      }
    </div>
  `,
  styles: [`
    .staff-row {
      display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
      padding: 12px 16px; border-bottom: 1px solid var(--cc-line); background: var(--cc-surface);
    }
    .staff-row:last-child { border-bottom: none; }
    .avatar {
      width: 40px; height: 40px; border-radius: 11px; display: grid; place-items: center;
      background: var(--cc-primary-light); color: var(--cc-primary-dark); flex: none;
    }
    .avatar.inactive { background: var(--cc-line); color: var(--cc-ink-faint); }
  `]
})
export class StaffDirectoryComponent {
  private readonly service = inject(StaffService);
  private readonly snackBar = inject(MatSnackBar);

  readonly members = signal<StaffUser[]>([]);
  readonly search = new FormControl('', { nonNullable: true });
  readonly searchTerm = signal('');

  readonly filtered = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    return this.members().filter(m => !term || m.email.toLowerCase().includes(term));
  });

  constructor() {
    this.reload();
    this.search.valueChanges.subscribe(value => this.searchTerm.set(value));
  }

  countOf(role: string): number {
    return this.members().filter(m => m.roles.includes(role as never)).length;
  }

  label(role: string): string {
    return role === 'STAFF' ? 'FRONT DESK' : role;
  }

  iconFor(member: StaffUser): string {
    if (member.roles.includes('DOCTOR')) { return 'stethoscope'; }
    if (member.roles.includes('ADMIN')) { return 'admin_panel_settings'; }
    if (member.roles.includes('STAFF')) { return 'support_agent'; }
    return 'personal_injury';
  }

  setActive(member: StaffUser, active: boolean): void {
    const call = active ? this.service.activate(member.id) : this.service.deactivate(member.id);
    call.subscribe({
      next: () => {
        this.snackBar.open(active ? 'Access restored' : 'Access revoked', 'OK', { duration: 3000 });
        this.reload();
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }

  resetPassword(member: StaffUser): void {
    const password = `Reset${Math.floor(1000 + Math.random() * 9000)}x`;
    this.service.resetPassword(member.id, password).subscribe({
      next: () => this.snackBar.open(
        `New password for ${member.email}: ${password}`, 'Copy', { duration: 10000 })
        .onAction().subscribe(() => navigator.clipboard?.writeText(password)),
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }

  private reload(): void {
    this.service.list().subscribe(r => this.members.set(r.data));
  }
}
