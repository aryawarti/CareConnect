import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProvidersService } from '../../core/providers/providers.service';
import { asyncResource } from '../../core/http/async-resource';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/**
 * Public doctor directory — for most visitors this is the first screen of the
 * product, and it is reachable without logging in. Which makes its error state
 * the one that matters most: if the backend is still warming up, "No doctors
 * match your search" is a lie that reads as an empty clinic.
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
          <h1>Find a doctor</h1>
          <div class="cc-sub">Browse specialists and their consultation fees</div>
        </div>
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
            <mat-card appearance="outlined">
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
                </mat-chip-set>
              </mat-card-content>
            </mat-card>
          }
        </div>
      } @else if (query.value) {
        <cc-empty icon="search_off" title="No doctors match “{{ query.value }}”"
                  text="Try a specialty like Cardiology, or part of a doctor's name." />
      } @else {
        <cc-empty icon="stethoscope" title="No doctors listed yet"
                  text="Doctors appear here once the clinic has verified their credentials.">
          <a mat-stroked-button routerLink="/login">Sign in</a>
        </cc-empty>
      }
    </div>
  `
})
export class DirectoryComponent {
  private readonly service = inject(ProvidersService);

  readonly query = new FormControl('', { nonNullable: true });

  readonly doctors = asyncResource(() =>
    this.service.directory(this.query.value, 0, 50).pipe(map(r => r.data)));

  constructor() {
    this.query.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.doctors.reload());
  }
}
