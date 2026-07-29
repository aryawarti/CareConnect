import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { RecordsService } from '../../core/records/records.service';
import { Encounter } from '../../core/records/record.models';

/** Patient-facing visit history. Read-only by design (FR-E3). */
@Component({
  selector: 'cc-my-records',
  standalone: true,
  imports: [DatePipe, MatCardModule, MatChipsModule, MatIconModule, MatButtonModule,
            MatExpansionModule],
  template: `
    <div class="cc-page" style="max-width:820px">
      <div class="cc-page-head"><div><h1>My medical records</h1><div class="cc-sub">Notes, diagnoses and prescriptions from each visit</div></div></div>
      @if (encounters().length) {
        <mat-accordion>
          @for (e of encounters(); track e.id) {
            <mat-expansion-panel (opened)="loadDetail(e.id)">
              <mat-expansion-panel-header>
                <mat-panel-title>
                  {{ e.occurredAt | date:'mediumDate' }} — {{ e.doctorName }}
                </mat-panel-title>
                <mat-panel-description>
                  {{ e.chiefComplaint || 'Visit' }}
                  <span class="cc-pill" [class]="e.status">{{ e.status }}</span>
                </mat-panel-description>
              </mat-expansion-panel-header>

              @if (detail()[e.id]; as full) {
                @if (full.notes) {
                  <h4>Clinical notes</h4>
                  <p style="white-space:pre-wrap">{{ full.notes }}</p>
                }
                @if (full.diagnoses.length) {
                  <h4>Diagnoses</h4>
                  <ul>
                    @for (d of full.diagnoses; track d.id) {
                      <li><strong>{{ d.code }}</strong> — {{ d.description }}</li>
                    }
                  </ul>
                }
                @if (full.prescriptions.length) {
                  <h4>Prescriptions</h4>
                  <ul>
                    @for (p of full.prescriptions; track p.id) {
                      <li>
                        <strong>{{ p.medication }}</strong> {{ p.dosage }},
                        {{ p.frequency }} for {{ p.durationDays }} days
                        @if (p.instructions) { <em>({{ p.instructions }})</em> }
                      </li>
                    }
                  </ul>
                }
                @if (full.amendments.length) {
                  <h4>Amendments</h4>
                  @for (a of full.amendments; track a.amendedAt) {
                    <p style="font-size:13px;color:#666">
                      {{ a.amendedAt | date:'medium' }} — {{ a.reason }}
                    </p>
                  }
                }
              } @else {
                <p style="color:#666">Loading…</p>
              }
            </mat-expansion-panel>
          }
        </mat-accordion>
      } @else {
        <mat-card appearance="outlined">
          <mat-card-content>
            <p>No visit records yet. A record is created automatically after a
               completed appointment.</p>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `
})
export class MyRecordsComponent {
  private readonly service = inject(RecordsService);

  readonly encounters = signal<Encounter[]>([]);
  readonly detail = signal<Record<string, Encounter>>({});

  constructor() {
    this.service.myHistory().subscribe(r => this.encounters.set(r.data));
  }

  loadDetail(id: string): void {
    if (this.detail()[id]) {
      return;
    }
    this.service.get(id).subscribe(full =>
      this.detail.update(d => ({ ...d, [id]: full })));
  }
}
