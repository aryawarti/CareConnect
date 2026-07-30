import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { map } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { RecordsService } from '../../core/records/records.service';
import { Encounter } from '../../core/records/record.models';
import { asyncResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/**
 * Patient-facing visit history. Read-only by design (FR-E3).
 *
 * Two levels of async here, and both need their own states: the list of visits,
 * and each visit's detail, fetched when its panel is opened. The detail used to
 * render a bare "Loading…" that never went away if the request failed — so a
 * failure was indistinguishable from a slow network, forever.
 */
@Component({
  selector: 'cc-my-records',
  standalone: true,
  imports: [DatePipe, MatCardModule, MatIconModule, MatButtonModule, MatExpansionModule,
            SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page cc-narrow">
      <div class="cc-page-head">
        <div>
          <h1>My medical records</h1>
          <div class="cc-sub">Notes, diagnoses and prescriptions from each visit</div>
        </div>
      </div>

      @if (encounters.loading()) {
        <cc-skeleton [count]="4" label="Loading your visit history…" />
      } @else if (encounters.failed()) {
        <cc-error [message]="encounters.error()!" (retry)="encounters.reload()" />
      } @else if (encounters.value()?.length) {
        <mat-accordion>
          @for (e of encounters.value(); track e.id) {
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
                    <p class="cc-faint">{{ a.amendedAt | date:'medium' }} — {{ a.reason }}</p>
                  }
                }
                @if (!full.notes && !full.diagnoses.length && !full.prescriptions.length) {
                  <p class="cc-muted">
                    The doctor has not added notes to this visit yet.
                  </p>
                }
              } @else if (detailError()[e.id]) {
                <!-- No "as" alias: Angular allows it only on the primary @if. -->
                <cc-error [message]="detailError()[e.id]!" (retry)="loadDetail(e.id, true)" />
              } @else {
                <cc-skeleton [count]="2" label="Loading visit details…" />
              }
            </mat-expansion-panel>
          }
        </mat-accordion>
      } @else {
        <cc-empty icon="folder_open" title="No visit records yet"
                  text="A record is created automatically after a completed appointment, with
                        your doctor's notes, diagnoses and prescriptions." />
      }
    </div>
  `
})
export class MyRecordsComponent {
  private readonly service = inject(RecordsService);

  readonly encounters = asyncResource(() => this.service.myHistory().pipe(map(r => r.data)));

  /** Detail per encounter id, fetched lazily when a panel opens. */
  readonly detail = signal<Record<string, Encounter>>({});
  readonly detailError = signal<Record<string, string>>({});

  loadDetail(id: string, force = false): void {
    if (this.detail()[id] || (!force && this.detailError()[id])) {
      return;
    }
    this.detailError.update(errors => {
      const { [id]: _dropped, ...rest } = errors;
      return rest;
    });
    this.service.get(id).subscribe({
      next: full => this.detail.update(d => ({ ...d, [id]: full })),
      error: err => this.detailError.update(e => ({ ...e, [id]: humanizeError(err) }))
    });
  }
}
