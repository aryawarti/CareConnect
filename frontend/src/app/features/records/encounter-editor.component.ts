import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RecordsService } from '../../core/records/records.service';
import { Encounter } from '../../core/records/record.models';
import { LabOrderPanelComponent } from './lab-order-panel.component';

/** Doctor's charting workspace: pick an encounter, document it, sign it. */
@Component({
  selector: 'cc-encounter-editor',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatButtonModule, MatIconModule, MatChipsModule, MatListModule, MatSelectModule,
            MatSnackBarModule, LabOrderPanelComponent],
  template: `
    <div class="cc-page" style="max-width:900px">
      <div class="cc-page-head"><div><h1>Clinical charts</h1><div class="cc-sub">Document and sign your encounters</div></div></div>

      <mat-form-field appearance="outline" class="cc-full-width">
        <mat-label>Encounter</mat-label>
        <mat-select [value]="current()?.id" (valueChange)="select($event)">
          @for (e of encounters(); track e.id) {
            <mat-option [value]="e.id">
              {{ e.occurredAt | date:'MMM d, h:mm a' }} — {{ e.patientName }} ({{ e.status }})
            </mat-option>
          }
        </mat-select>
      </mat-form-field>

      @if (current(); as enc) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-icon mat-card-avatar>clinical_notes</mat-icon>
            <mat-card-title>{{ enc.patientName }}</mat-card-title>
            <mat-card-subtitle>
              {{ enc.occurredAt | date:'full' }} · <span class="cc-pill" [class]="enc.status">{{ enc.status }}</span>
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (enc.status === 'OPEN') {
              <form [formGroup]="noteForm" (ngSubmit)="saveNotes(enc)"
                    style="display:flex;flex-direction:column;gap:8px;margin-top:16px">
                <mat-form-field appearance="outline">
                  <mat-label>Chief complaint</mat-label>
                  <input matInput formControlName="chiefComplaint">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Clinical notes</mat-label>
                  <textarea matInput rows="6" formControlName="notes"></textarea>
                </mat-form-field>
                <div style="display:flex;gap:8px;justify-content:flex-end">
                  <button mat-stroked-button type="submit">Save notes</button>
                  <button mat-flat-button class="cc-btn-primary" type="button" (click)="sign(enc)">
                    Sign encounter
                  </button>
                </div>
              </form>
            } @else {
              <p style="white-space:pre-wrap">{{ enc.notes }}</p>
              <form [formGroup]="amendForm" (ngSubmit)="amend(enc)"
                    style="display:flex;flex-direction:column;gap:8px;margin-top:16px">
                <p style="color:#666;font-size:13px">
                  This record is {{ enc.status }} — corrections are recorded as amendments
                  and the previous text is preserved.
                </p>
                <mat-form-field appearance="outline">
                  <mat-label>Corrected notes</mat-label>
                  <textarea matInput rows="5" formControlName="notes"></textarea>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Reason for amendment</mat-label>
                  <input matInput formControlName="reason">
                </mat-form-field>
                <div style="display:flex;justify-content:flex-end">
                  <button mat-stroked-button type="submit">Record amendment</button>
                </div>
              </form>
            }

            <h4>Diagnoses</h4>
            <mat-list>
              @for (d of enc.diagnoses; track d.id) {
                <mat-list-item><strong>{{ d.code }}</strong> — {{ d.description }}</mat-list-item>
              } @empty { <p style="color:#666">None recorded.</p> }
            </mat-list>
            @if (enc.status === 'OPEN') {
              <form [formGroup]="dxForm" (ngSubmit)="addDiagnosis(enc)"
                    style="display:flex;gap:12px;align-items:baseline">
                <mat-form-field appearance="outline" style="width:140px">
                  <mat-label>ICD code</mat-label>
                  <input matInput formControlName="code" placeholder="J06.9">
                </mat-form-field>
                <mat-form-field appearance="outline" style="flex:1">
                  <mat-label>Description</mat-label>
                  <input matInput formControlName="description">
                </mat-form-field>
                <button mat-stroked-button type="submit">Add</button>
              </form>
            }

            <h4>Prescriptions</h4>
            <mat-list>
              @for (p of enc.prescriptions; track p.id) {
                <mat-list-item>
                  <strong>{{ p.medication }}</strong> {{ p.dosage }}, {{ p.frequency }},
                  {{ p.durationDays }}d
                </mat-list-item>
              } @empty { <p style="color:#666">None recorded.</p> }
            </mat-list>
            @if (enc.status === 'OPEN') {
              <form [formGroup]="rxForm" (ngSubmit)="addPrescription(enc)"
                    style="display:flex;gap:12px;align-items:baseline;flex-wrap:wrap">
                <mat-form-field appearance="outline">
                  <mat-label>Medication</mat-label>
                  <input matInput formControlName="medication">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:120px">
                  <mat-label>Dosage</mat-label>
                  <input matInput formControlName="dosage" placeholder="500mg">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:120px">
                  <mat-label>Frequency</mat-label>
                  <input matInput formControlName="frequency" placeholder="TID">
                </mat-form-field>
                <mat-form-field appearance="outline" style="width:100px">
                  <mat-label>Days</mat-label>
                  <input matInput type="number" formControlName="durationDays">
                </mat-form-field>
                <button mat-stroked-button type="submit">Add</button>
              </form>
            }

            <cc-lab-order-panel [encounterId]="enc.id" [patientId]="enc.patientId"
                                [canOrder]="enc.status === 'OPEN'" />
          </mat-card-content>
        </mat-card>
      } @else {
        <mat-card appearance="outlined">
          <mat-card-content>
            <p>No encounters yet. One is created automatically when you mark an
               appointment complete on the Schedule screen.</p>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `
})
export class EncounterEditorComponent {
  private readonly service = inject(RecordsService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly encounters = signal<Encounter[]>([]);
  readonly current = signal<Encounter | null>(null);

  readonly noteForm = this.fb.nonNullable.group({ chiefComplaint: [''], notes: [''] });
  readonly dxForm = this.fb.nonNullable.group({
    code: ['', Validators.required], description: ['', Validators.required]
  });
  readonly rxForm = this.fb.nonNullable.group({
    medication: ['', Validators.required], dosage: ['', Validators.required],
    frequency: ['', Validators.required], durationDays: [5, [Validators.min(1), Validators.max(365)]]
  });
  readonly amendForm = this.fb.nonNullable.group({
    notes: ['', Validators.required], reason: ['', Validators.required]
  });

  constructor() {
    this.service.doctorEncounters().subscribe(r => {
      this.encounters.set(r.data);
      if (r.data.length) {
        this.select(r.data[0].id);
      }
    });
  }

  select(id: string): void {
    this.service.get(id).subscribe(e => {
      this.current.set(e);
      this.noteForm.patchValue({ chiefComplaint: e.chiefComplaint ?? '', notes: e.notes ?? '' });
      this.amendForm.patchValue({ notes: e.notes ?? '', reason: '' });
    });
  }

  private apply(e: Encounter, message: string): void {
    this.current.set(e);
    this.snackBar.open(message, 'OK', { duration: 3000 });
  }

  private fail(err: unknown): void {
    const detail = (err as { error?: { detail?: string } })?.error?.detail;
    this.snackBar.open(detail ?? 'Action failed', 'OK', { duration: 4000 });
  }

  saveNotes(enc: Encounter): void {
    const v = this.noteForm.getRawValue();
    this.service.updateContent(enc.id, v.chiefComplaint, v.notes)
      .subscribe({ next: e => this.apply(e, 'Notes saved'), error: e => this.fail(e) });
  }

  addDiagnosis(enc: Encounter): void {
    if (this.dxForm.invalid) { return; }
    const v = this.dxForm.getRawValue();
    this.service.addDiagnosis(enc.id, v.code, v.description).subscribe({
      next: e => { this.apply(e, 'Diagnosis added'); this.dxForm.reset(); },
      error: e => this.fail(e)
    });
  }

  addPrescription(enc: Encounter): void {
    if (this.rxForm.invalid) { return; }
    this.service.addPrescription(enc.id, { ...this.rxForm.getRawValue(), instructions: '' })
      .subscribe({
        next: e => { this.apply(e, 'Prescription added'); this.rxForm.reset({ durationDays: 5 }); },
        error: e => this.fail(e)
      });
  }

  sign(enc: Encounter): void {
    this.service.sign(enc.id).subscribe({
      next: e => this.apply(e, 'Encounter signed'), error: e => this.fail(e)
    });
  }

  amend(enc: Encounter): void {
    if (this.amendForm.invalid) { return; }
    const v = this.amendForm.getRawValue();
    this.service.amend(enc.id, v.notes, v.reason).subscribe({
      next: e => this.apply(e, 'Amendment recorded'), error: e => this.fail(e)
    });
  }
}
