import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ProvidersService } from '../../core/providers/providers.service';
import { DAY_NAMES } from '../../core/providers/provider.models';
import { asyncResource } from '../../core/http/async-resource';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/**
 * The doctor list — the middle step between choosing a department and opening
 * one doctor's profile.
 *
 * Two things make it more than a grid of names. It honours a `departmentId`
 * query parameter, so arriving from the departments screen keeps the patient's
 * choice instead of dropping them back into every doctor in the clinic. And
 * each card states the days that doctor consults, or says plainly that no hours
 * are published — a card that looks bookable but is not simply moves the
 * disappointment one screen later.
 */
@Component({
  selector: 'cc-directory',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule,
            MatFormFieldModule, MatInputModule, MatIconModule, MatChipsModule,
            SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div>
          <h1>{{ heading() }}</h1>
          <div class="cc-sub">
            Compare specialists, then open a profile to see their consulting hours
          </div>
        </div>
        <span class="cc-spacer"></span>
        @if (departmentId()) {
          <a mat-stroked-button routerLink="/doctors">
            <mat-icon>close</mat-icon> Clear filter
          </a>
        }
        <a mat-stroked-button routerLink="/departments">
          <mat-icon>grid_view</mat-icon> Departments
        </a>
      </div>

      <mat-form-field appearance="outline" class="cc-full-width">
        <mat-label>Search by name or specialty</mat-label>
        <input matInput [formControl]="query" placeholder="e.g. Cardiology, Rao">
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>

      @if (doctors.loading()) {
        <div class="cc-grid cc-grid-3">
          <cc-skeleton [count]="1" variant="card" label="Loading doctors…" />
          <cc-skeleton [count]="1" variant="card" />
          <cc-skeleton [count]="1" variant="card" />
        </div>
      } @else if (doctors.failed()) {
        <cc-error [message]="doctors.error()!" (retry)="doctors.reload()" />
      } @else if (doctors.value()?.length) {
        <div class="cc-grid cc-grid-3" [class.cc-stale]="doctors.refreshing()">
          @for (doc of doctors.value(); track doc.id) {
            <mat-card appearance="outlined" class="cc-doc-card">
              <mat-card-header>
                <mat-icon mat-card-avatar>stethoscope</mat-icon>
                <mat-card-title>Dr. {{ doc.firstName }} {{ doc.lastName }}</mat-card-title>
                <mat-card-subtitle>
                  {{ doc.specialty }} · {{ doc.departmentName }}
                </mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <mat-chip-set>
                  <mat-chip>
                    {{ doc.consultationFee | currency:'INR':'symbol':'1.0-0' }} / visit
                  </mat-chip>
                  @if (doc.experienceYears) {
                    <mat-chip>{{ doc.experienceYears }} yrs</mat-chip>
                  }
                </mat-chip-set>

                <div class="cc-days">
                  @if (doc.bookable) {
                    <mat-icon class="cc-days-icon">event_available</mat-icon>
                    <span>{{ daysLabel(doc.workingDays) }}</span>
                  } @else {
                    <mat-icon class="cc-days-icon cc-days-none">event_busy</mat-icon>
                    <span class="cc-faint">No consulting hours published</span>
                  }
                </div>
              </mat-card-content>
              <mat-card-actions>
                <a mat-button [routerLink]="['/doctors', doc.id]">View profile</a>
                @if (doc.bookable) {
                  <a mat-flat-button color="primary"
                     [routerLink]="['/book']" [queryParams]="{ doctorId: doc.id }">
                    Book
                  </a>
                }
              </mat-card-actions>
            </mat-card>
          }
        </div>
      } @else if (query.value) {
        <cc-empty icon="search_off" title="No doctors match “{{ query.value }}”"
                  text="Try a specialty like Cardiology, or part of a doctor's name." />
      } @else if (departmentId()) {
        <cc-empty icon="stethoscope" title="No doctors in this department yet"
                  text="Nobody is currently accepting appointments here.">
          <a mat-stroked-button routerLink="/departments">Browse other departments</a>
        </cc-empty>
      } @else {
        <cc-empty icon="stethoscope" title="No doctors listed yet"
                  text="Doctors appear here once the clinic has verified their credentials." />
      }
    </div>
  `,
  styles: [`
    .cc-doc-card { display: flex; flex-direction: column; }
    .cc-doc-card mat-card-actions { margin-top: auto; }
    .cc-days {
      display: flex; align-items: center; gap: 8px;
      margin-top: 12px; font-size: 13px;
    }
    .cc-days-icon { font-size: 18px; width: 18px; height: 18px; color: var(--cc-success, #15803d); }
    .cc-days-none { color: #c2410c; }
  `]
})
export class DirectoryComponent {
  private readonly service = inject(ProvidersService);
  private readonly route = inject(ActivatedRoute);

  readonly query = new FormControl('', { nonNullable: true });

  /** Set when arriving from the departments screen. */
  readonly departmentId = toSignal(
    this.route.queryParamMap.pipe(map(params => params.get('departmentId') ?? '')),
    { initialValue: '' });

  readonly doctors = asyncResource(() =>
    this.service.directory(this.query.value, 0, 50, this.departmentId() || undefined)
      .pipe(map(r => r.data)));

  /** Named from the results rather than a second request for the department. */
  readonly heading = computed(() => {
    const list = this.doctors.value();
    if (this.departmentId() && list?.length) {
      return list[0].departmentName;
    }
    return this.departmentId() ? 'Department' : 'Find a doctor';
  });

  constructor() {
    this.query.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.doctors.reload());

    // Reload when the department filter changes. Angular reuses the same
    // component instance when only query parameters change, so without this the
    // list would keep showing the previous department's doctors.
    this.route.queryParamMap
      .pipe(map(p => p.get('departmentId') ?? ''), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.doctors.reload());
  }

  /** "Mon, Wed, Fri", or "Mon–Fri" when the run is unbroken. */
  daysLabel(days: number[]): string {
    if (!days.length) {
      return 'Not scheduled';
    }
    const sorted = [...days].sort((a, b) => a - b);
    const consecutive = sorted.every((d, i) => i === 0 || d === sorted[i - 1] + 1);
    if (consecutive && sorted.length > 2) {
      return `${DAY_NAMES[sorted[0]]}–${DAY_NAMES[sorted[sorted.length - 1]]}`;
    }
    return sorted.map(d => DAY_NAMES[d]).join(', ');
  }
}
