import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProvidersService } from '../../core/providers/providers.service';
import { Doctor } from '../../core/providers/provider.models';

@Component({
  selector: 'cc-directory',
  standalone: true,
  imports: [CurrencyPipe, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
            MatInputModule, MatIconModule, MatChipsModule],
  template: `
    <div class="cc-page">
      <div class="cc-page-head"><div><h1>Find a doctor</h1><div class="cc-sub">Browse specialists and their consultation fees</div></div></div>
      <mat-form-field appearance="outline" class="cc-full-width">
        <mat-label>Search by name or specialty</mat-label>
        <input matInput [formControl]="query" placeholder="e.g. Cardiology, Rao">
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>

      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px">
        @for (doc of doctors(); track doc.id) {
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-icon mat-card-avatar>stethoscope</mat-icon>
              <mat-card-title>Dr. {{ doc.firstName }} {{ doc.lastName }}</mat-card-title>
              <mat-card-subtitle>{{ doc.specialty }} · {{ doc.departmentName }}</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-chip-set>
                <mat-chip>{{ doc.consultationFee | currency:'INR':'symbol':'1.0-0' }} / visit</mat-chip>
              </mat-chip-set>
            </mat-card-content>
          </mat-card>
        } @empty {
          <p>No doctors match your search.</p>
        }
      </div>
    </div>
  `
})
export class DirectoryComponent {
  private readonly service = inject(ProvidersService);

  readonly doctors = signal<Doctor[]>([]);
  readonly query = new FormControl('', { nonNullable: true });

  constructor() {
    this.load();
    this.query.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load());
  }

  private load(): void {
    this.service.directory(this.query.value, 0, 50).subscribe(r => this.doctors.set(r.data));
  }
}
