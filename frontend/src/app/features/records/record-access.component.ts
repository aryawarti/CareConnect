import { Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RecordsService } from '../../core/records/records.service';
import { AccessLogEntry } from '../../core/records/record.models';
import { AuthService } from '../../core/auth/auth.service';
import { asyncResource } from '../../core/http/async-resource';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/**
 * The chart access trail — who opened a patient's records, and when.
 *
 * Access control decides who *may* read a chart; this shows who *did*. That
 * distinction is the point: "only your treating doctor can see this" is a claim,
 * and until the person the data is about can check it, it stays a claim.
 *
 * One component, two audiences, because the content is identical and only the
 * source differs: a patient sees their own trail, staff audit any patient's.
 * Splitting it would duplicate the presentation to no benefit.
 */
@Component({
  selector: 'cc-record-access',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule,
            SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page cc-narrow">
      <div class="cc-page-head">
        <div>
          <h1>{{ patientId() ? 'Chart access trail' : 'Who has seen my records' }}</h1>
          <div class="cc-sub">
            {{ patientId()
                ? 'Every read of this patient’s clinical records, most recent first.'
                : 'Every time someone opened your clinical records. Nothing is hidden from
                   you here — including your own visits to this page.' }}
          </div>
        </div>
        @if (patientId()) {
          <span class="cc-spacer"></span>
          <a mat-stroked-button routerLink="/patients">
            <mat-icon>arrow_back</mat-icon> Patients
          </a>
        }
      </div>

      @if (entries.loading()) {
        <cc-skeleton [count]="5" label="Loading the access trail…" />
      } @else if (entries.failed()) {
        <cc-error [message]="entries.error()!" (retry)="entries.reload()" />
      } @else if (entries.value()?.length) {
        <div class="cc-table-wrap">
          @for (e of entries.value(); track e.id) {
            <div class="cc-row cc-access-row">
              <div class="cc-access-icon" [class.self]="e.selfAccess">
                <mat-icon>{{ e.selfAccess ? 'person' : icon(e.actorRole) }}</mat-icon>
              </div>
              <div class="cc-fill">
                <div class="cc-strong">
                  {{ e.selfAccess ? 'You' : (e.actorName || 'Account ' + shortId(e.actorUserId)) }}
                  <span class="cc-pill">{{ e.actorRole }}</span>
                </div>
                <div class="cc-faint">{{ describe(e) }}</div>
              </div>
              <div style="text-align:right">
                <div class="cc-text-sm cc-mono">{{ e.accessedAt | date:'MMM d, y' }}</div>
                <div class="cc-faint cc-mono">{{ e.accessedAt | date:'h:mm:ss a' }}</div>
              </div>
            </div>
          }
        </div>
        <p class="cc-faint" style="margin-top:14px">
          This trail is append-only — entries cannot be edited or removed, including by
          administrators.
        </p>
      } @else {
        <cc-empty icon="shield_person" title="No access recorded yet"
                  [text]="patientId()
                    ? 'Nobody has opened this patient’s records.'
                    : 'Nobody has opened your records yet. When a clinician does, it will
                       appear here with their name and the exact time.'" />
      }
    </div>
  `,
  styles: [`
    .cc-access-row {
      padding: 14px 16px;
      border-bottom: 1px solid var(--cc-line);
      background: var(--cc-surface);
    }
    .cc-access-row:last-child { border-bottom: none; }
    .cc-access-icon {
      width: 38px; height: 38px; flex: none;
      display: grid; place-items: center; border-radius: 10px;
      background: var(--cc-info-bg); color: var(--cc-info);
    }
    .cc-access-icon.self { background: var(--cc-primary-light); color: var(--cc-primary-dark); }
  `]
})
export class RecordAccessComponent {
  /** Route param when auditing a specific patient; absent for "my records". */
  readonly patientId = input<string | undefined>();

  private readonly records = inject(RecordsService);
  private readonly auth = inject(AuthService);

  readonly entries = asyncResource<AccessLogEntry[]>(() => {
    const id = this.patientId();
    return id ? this.records.patientAccessLog(id) : this.records.myAccessLog();
  });

  readonly isStaff = computed(() =>
    ['ADMIN', 'STAFF'].some(r => this.auth.user()?.roles.includes(r)));

  icon(role: string): string {
    switch (role) {
      case 'DOCTOR': return 'stethoscope';
      case 'ADMIN': case 'STAFF': return 'badge';
      default: return 'visibility';
    }
  }

  /** Plain language, because the audience includes the patient. */
  describe(e: AccessLogEntry): string {
    switch (e.action) {
      case 'VIEW_ENCOUNTER':
        return e.selfAccess ? 'Opened one of your visit records'
                            : 'Opened a visit record in full';
      case 'LIST_PATIENT_HISTORY':
        return 'Listed the visit history';
      case 'LIST_OWN_HISTORY':
        return 'Viewed your own visit history';
      default:
        return e.action;
    }
  }

  shortId(id: string): string {
    return id.slice(0, 8);
  }
}
