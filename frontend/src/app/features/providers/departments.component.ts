import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ProvidersService } from '../../core/providers/providers.service';
import { asyncResource } from '../../core/http/async-resource';
import { SkeletonComponent, ErrorPanelComponent, EmptyStateComponent }
  from '../../shared/ui.components';

/**
 * The entry point to booking: which kinds of care the clinic offers.
 *
 * A patient does not usually arrive knowing a doctor's name — they arrive
 * knowing what hurts. Starting at the department and narrowing to a doctor
 * matches that, and it is why this sits ahead of the directory rather than
 * beside it.
 *
 * Each card carries its doctor count, so an empty department is visible before
 * the click rather than after it.
 */
@Component({
  selector: 'cc-departments',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatIconModule, MatButtonModule,
            SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div>
          <h2>Departments</h2>
          <div class="cc-sub">Choose the kind of care you need</div>
        </div>
        <span class="cc-spacer"></span>
        <a mat-stroked-button routerLink="/doctors">
          <mat-icon>list</mat-icon> All doctors
        </a>
      </div>

      @if (departments.loading()) {
        <div class="cc-grid cc-grid-3"><cc-skeleton [count]="6" variant="card" /></div>
      } @else if (departments.error()) {
        <cc-error [message]="departments.error()!" (retry)="departments.reload()" />
      } @else if (departments.value()?.length) {
        <div class="cc-grid cc-grid-3">
          @for (dept of departments.value()!; track dept.id) {
              @if (dept.doctorCount > 0) {
                <a class="cc-card cc-dept-card" [routerLink]="['/doctors']"
                   [queryParams]="{ departmentId: dept.id }">
                  <div class="cc-row" style="gap:12px;align-items:center">
                    <div class="cc-stat-icon"><mat-icon>{{ iconFor(dept.name) }}</mat-icon></div>
                    <div>
                      <div style="font-weight:600">{{ dept.name }}</div>
                      <div class="cc-faint" style="font-size:13px">
                        {{ dept.doctorCount }}
                        {{ dept.doctorCount === 1 ? 'doctor' : 'doctors' }}
                      </div>
                    </div>
                    <span style="flex:1"></span>
                    <mat-icon class="cc-faint">chevron_right</mat-icon>
                  </div>
                </a>
              } @else {
                <!-- Shown but not clickable. Hiding it entirely would leave a
                     patient wondering whether the clinic has the department at
                     all; this answers that, and says why they cannot proceed. -->
                <div class="cc-card cc-dept-card cc-dept-empty">
                  <div class="cc-row" style="gap:12px;align-items:center">
                    <div class="cc-stat-icon"><mat-icon>{{ iconFor(dept.name) }}</mat-icon></div>
                    <div>
                      <div style="font-weight:600">{{ dept.name }}</div>
                      <div class="cc-faint" style="font-size:13px">
                        No doctors accepting appointments
                      </div>
                    </div>
                  </div>
                </div>
              }
          }
        </div>
      } @else {
        <cc-empty icon="domain" title="No departments yet"
                  text="Administration has not set up any departments." />
      }
    </div>
  `,
  styles: [`
    .cc-dept-card { display: block; text-decoration: none; color: inherit; transition: .15s; }
    a.cc-dept-card:hover {
      border-color: var(--cc-primary);
      transform: translateY(-2px);
      box-shadow: 0 6px 18px rgba(15, 118, 110, .12);
    }
    a.cc-dept-card:focus-visible { outline: 2px solid var(--cc-primary); outline-offset: 2px; }
    .cc-dept-empty { opacity: .6; }
  `]
})
export class DepartmentsComponent {
  private readonly providers = inject(ProvidersService);

  readonly departments = asyncResource(() => this.providers.departments());

  /** Falls back rather than rendering a blank tile for a department added later. */
  iconFor(name: string): string {
    const icons: Record<string, string> = {
      cardiology: 'cardiology',
      pediatrics: 'child_care',
      orthopedics: 'orthopedics',
      dermatology: 'dermatology',
      neurology: 'neurology',
      'general medicine': 'stethoscope',
      gynecology: 'pregnant_woman',
      ophthalmology: 'visibility',
      dentistry: 'dentistry',
      psychiatry: 'psychology',
    };
    return icons[name.trim().toLowerCase()] ?? 'medical_services';
  }
}
