import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { ProvidersService } from '../../core/providers/providers.service';
import { DAY_NAMES_LONG, formatTime, groupByDay } from '../../core/providers/provider.models';
import { asyncResource } from '../../core/http/async-resource';
import { SkeletonComponent, ErrorPanelComponent } from '../../shared/ui.components';

/**
 * Everything a patient needs to decide on a doctor, before committing to a date.
 *
 * The whole point is the schedule panel. Booking previously offered a date
 * picker with no indication of which days the doctor even consults, so choosing
 * a date was guesswork and a wrong guess produced "no slots available" with no
 * explanation. Showing the working week first turns that into an informed
 * choice, and the "Book" button carries the doctor through so the next screen
 * starts already narrowed.
 *
 * A doctor with no published hours gets an honest panel rather than a hidden
 * one: the patient learns they cannot book yet, instead of finding an empty
 * calendar and assuming the clinic is full.
 */
@Component({
  selector: 'cc-doctor-profile',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, MatCardModule, MatButtonModule,
            MatIconModule, MatChipsModule, SkeletonComponent, ErrorPanelComponent],
  template: `
    <div class="cc-page">
      <!-- An "as" binding is only legal on the PRIMARY @if (NG5002), so the
           success branch leads and the loading/error branches follow it. -->
      @if (profile.value(); as doc) {
        <div class="cc-page-head">
          <div>
            <h2>Dr. {{ doc.firstName }} {{ doc.lastName }}</h2>
            <div class="cc-sub">{{ doc.specialty }} · {{ doc.departmentName }}</div>
          </div>
          <span class="cc-spacer"></span>
          @if (doc.acceptingAppointments) {
            <a mat-flat-button color="primary"
               [routerLink]="['/book']" [queryParams]="{ doctorId: doc.id }">
              <mat-icon>event_available</mat-icon> Book an appointment
            </a>
          } @else {
            <button mat-flat-button color="primary" disabled>
              <mat-icon>event_busy</mat-icon> Not accepting bookings
            </button>
          }
        </div>

        <div class="cc-grid" style="grid-template-columns:minmax(0,2fr) minmax(0,1fr);gap:20px">
          <div>
            <!-- Consulting hours -->
            <mat-card appearance="outlined">
              <mat-card-header><mat-card-title>Consulting hours</mat-card-title></mat-card-header>
              <mat-card-content>
                @if (weekly().length) {
                  @for (group of weekly(); track group.day) {
                    <div class="cc-day-row">
                      <div class="cc-day-name">{{ dayName(group.day) }}</div>
                      <div class="cc-day-windows">
                        @for (slot of group.windows; track slot) {
                          <span class="cc-window-chip">
                            {{ time(slot.startTime) }} – {{ time(slot.endTime) }}
                          </span>
                        }
                      </div>
                    </div>
                  }
                  <p class="cc-faint" style="margin:14px 0 0;font-size:13px">
                    Appointments are {{ slotLength() }} minutes. Only times that are
                    still free are offered when you book.
                  </p>
                } @else {
                  <div class="cc-alert cc-alert-warn" role="status">
                    <mat-icon>event_busy</mat-icon>
                    <div>
                      <strong>No consulting hours published yet.</strong>
                      <div class="cc-faint" style="margin-top:4px">
                        This doctor has not set a schedule, so no appointments can be
                        booked with them at the moment. Try another doctor in
                        {{ doc.departmentName }}, or check back later.
                      </div>
                      <a mat-stroked-button style="margin-top:12px"
                         [routerLink]="['/doctors']"
                         [queryParams]="{ departmentId: doc.departmentId }">
                        Other doctors in {{ doc.departmentName }}
                      </a>
                    </div>
                  </div>
                }
              </mat-card-content>
            </mat-card>

            <!-- About -->
            @if (doc.bio) {
              <mat-card appearance="outlined" style="margin-top:20px">
                <mat-card-header><mat-card-title>About</mat-card-title></mat-card-header>
                <mat-card-content>
                  <p style="line-height:1.65;margin:0">{{ doc.bio }}</p>
                </mat-card-content>
              </mat-card>
            }
          </div>

          <div>
            <!-- At a glance -->
            <mat-card appearance="outlined">
              <mat-card-content>
                <div class="cc-fact">
                  <span class="cc-faint">Consultation fee</span>
                  <span class="cc-money">
                    {{ doc.consultationFee | currency:'INR':'symbol':'1.0-0' }}
                  </span>
                </div>
                <div class="cc-fact">
                  <span class="cc-faint">Department</span>
                  <span>{{ doc.departmentName }}</span>
                </div>
                <div class="cc-fact">
                  <span class="cc-faint">Specialty</span>
                  <span>{{ doc.specialty }}</span>
                </div>
                @if (doc.qualification) {
                  <div class="cc-fact">
                    <span class="cc-faint">Qualification</span>
                    <span>{{ doc.qualification }}</span>
                  </div>
                }
                @if (doc.experienceYears !== null) {
                  <div class="cc-fact">
                    <span class="cc-faint">Experience</span>
                    <span>{{ doc.experienceYears }} years</span>
                  </div>
                }
                <div class="cc-fact">
                  <span class="cc-faint">Consults on</span>
                  <span>{{ workingDaysLabel() }}</span>
                </div>
              </mat-card-content>
            </mat-card>

            <!-- Time off: only shown when it exists, because "no upcoming leave"
                 is not information a patient came here for. -->
            @if (doc.upcomingTimeOff.length) {
              <mat-card appearance="outlined" style="margin-top:20px">
                <mat-card-header>
                  <mat-card-title>Away on</mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  @for (day of doc.upcomingTimeOff; track day.id) {
                    <div class="cc-fact">
                      <span>{{ day.date | date:'EEE, d MMM' }}</span>
                      <span class="cc-faint">{{ day.reason || 'Unavailable' }}</span>
                    </div>
                  }
                </mat-card-content>
              </mat-card>
            }
          </div>
        </div>
      } @else if (profile.loading()) {
        <cc-skeleton [count]="6" variant="card" />
      } @else if (profile.failed()) {
        <cc-error [message]="profile.error()!" (retry)="profile.reload()" />
      }
    </div>
  `,
  styles: [`
    .cc-day-row {
      display: flex; gap: 16px; align-items: baseline;
      padding: 9px 0; border-bottom: 1px solid var(--cc-border, #e2e8f0);
    }
    .cc-day-row:last-of-type { border-bottom: none; }
    .cc-day-name { width: 104px; font-weight: 600; flex: none; }
    .cc-day-windows { display: flex; flex-wrap: wrap; gap: 8px; }
    .cc-window-chip {
      background: var(--cc-chip-bg, #f1f5f9); border-radius: 999px;
      padding: 3px 12px; font-size: 14px;
    }
    .cc-fact {
      display: flex; justify-content: space-between; gap: 16px;
      padding: 9px 0; border-bottom: 1px solid var(--cc-border, #e2e8f0);
    }
    .cc-fact:last-child { border-bottom: none; }
    @media (max-width: 860px) {
      .cc-page :global(.cc-grid) { grid-template-columns: 1fr !important; }
      .cc-day-row { flex-direction: column; gap: 6px; }
      .cc-day-name { width: auto; }
    }
  `]
})
export class DoctorProfileComponent {
  private readonly providers = inject(ProvidersService);
  private readonly route = inject(ActivatedRoute);

  private readonly doctorId = toSignal(
    this.route.paramMap.pipe(map(params => params.get('id') ?? '')),
    { initialValue: '' });

  readonly profile = asyncResource(() => this.providers.profile(this.doctorId()));

  readonly weekly = computed(() => groupByDay(this.profile.value()?.weeklyAvailability ?? []));

  /** "Mon, Wed, Fri" — the single most useful fact before picking a date. */
  readonly workingDaysLabel = computed(() => {
    const days = this.weekly().map(g => DAY_NAMES_LONG[g.day].slice(0, 3));
    return days.length ? days.join(', ') : 'Not scheduled';
  });

  /** Appointment length, taken from the schedule rather than assumed. Doctors
   *  may use different lengths per window; the common case is one value. */
  readonly slotLength = computed(() => {
    const lengths = new Set((this.profile.value()?.weeklyAvailability ?? [])
      .map(s => s.slotMinutes));
    return lengths.size === 1 ? [...lengths][0] : Math.min(...lengths);
  });

  dayName(day: number): string {
    return DAY_NAMES_LONG[day];
  }

  time(value: string): string {
    return formatTime(value);
  }
}
