import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { Doctor, Slot } from '../../core/providers/provider.models';

const DAYS = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

@Component({
  selector: 'cc-availability-editor',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatButtonModule, MatIconModule, MatListModule, MatSnackBarModule],
  template: `
    <mat-card appearance="outlined" style="margin-top:24px">
      <mat-card-header>
        <mat-card-title>Weekly availability — Dr. {{ doctor().lastName }}</mat-card-title>
        <span style="flex:1"></span>
        <button mat-icon-button (click)="closed.emit()" aria-label="Close">
          <mat-icon>close</mat-icon>
        </button>
      </mat-card-header>
      <mat-card-content>
        <mat-list>
          @for (slot of slots(); track $index) {
            <mat-list-item>
              <span matListItemTitle>
                {{ dayName(slot.dayOfWeek) }} {{ slot.startTime }}–{{ slot.endTime }}
                ({{ slot.slotMinutes }} min slots)
              </span>
              <button matListItemMeta mat-icon-button (click)="remove($index)" aria-label="Remove">
                <mat-icon>delete</mat-icon>
              </button>
            </mat-list-item>
          } @empty {
            <p class="cc-muted">No availability defined yet.</p>
          }
        </mat-list>

        <form [formGroup]="form" (ngSubmit)="add()"
              style="display:flex;gap:12px;align-items:baseline;flex-wrap:wrap;margin-top:8px">
          <mat-form-field appearance="outline">
            <mat-label>Day</mat-label>
            <mat-select formControlName="dayOfWeek">
              @for (d of [1,2,3,4,5,6,7]; track d) {
                <mat-option [value]="d">{{ dayName(d) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>From</mat-label>
            <input matInput type="time" formControlName="startTime">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>To</mat-label>
            <input matInput type="time" formControlName="endTime">
          </mat-form-field>
          <mat-form-field appearance="outline" style="width:110px">
            <mat-label>Slot (min)</mat-label>
            <input matInput type="number" formControlName="slotMinutes">
          </mat-form-field>
          <button mat-stroked-button type="submit">Add window</button>
          <span style="flex:1"></span>
          <button mat-flat-button color="primary" type="button" (click)="saveAll()">
            Save schedule
          </button>
        </form>
      </mat-card-content>
    </mat-card>
  `
})
export class AvailabilityEditorComponent {
  readonly doctor = input.required<Doctor>();
  readonly closed = output<void>();

  private readonly service = inject(ProvidersService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly slots = signal<Slot[]>([]);

  readonly form = this.fb.nonNullable.group({
    dayOfWeek: [1, Validators.required],
    startTime: ['09:00', Validators.required],
    endTime: ['13:00', Validators.required],
    slotMinutes: [30, [Validators.min(5), Validators.max(240)]]
  });

  constructor() {
    effect(() => {
      const doc = this.doctor();
      this.service.availability(doc.id).subscribe(s => this.slots.set(s));
    });
  }

  dayName(d: number): string {
    return DAYS[d];
  }

  add(): void {
    const v = this.form.getRawValue();
    if (v.startTime >= v.endTime) {
      this.snackBar.open('Start must be before end', 'OK', { duration: 3000 });
      return;
    }
    this.slots.update(s => [...s, v]);
  }

  remove(index: number): void {
    this.slots.update(s => s.filter((_, i) => i !== index));
  }

  saveAll(): void {
    this.service.replaceAvailability(this.doctor().id, this.slots()).subscribe({
      next: saved => {
        this.slots.set(saved);
        this.snackBar.open('Schedule saved', 'OK', { duration: 3000 });
      },
      error: err => this.snackBar.open(
          err?.error?.detail ?? 'Save failed — check for overlapping windows', 'OK',
          { duration: 4000 })
    });
  }
}
