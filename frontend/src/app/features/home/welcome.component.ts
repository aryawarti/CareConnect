import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProvidersService } from '../../core/providers/providers.service';
import { Department } from '../../core/providers/provider.models';

/**
 * The public landing page — the first thing a visitor sees.
 *
 * Every call to action here leads somewhere a signed-out visitor can actually
 * go. It previously offered "Browse doctors", which routed to a guarded page
 * and bounced the visitor to the login screen: the one thing a landing page
 * must not do is advertise a door that is locked.
 *
 * Departments are shown instead. That endpoint is genuinely public (see the
 * gateway's public-paths), so the section is real data rather than a mock, and
 * it answers the question a first-time visitor actually has — "do they treat
 * what I have?" — without requiring an account to find out.
 */
@Component({
  selector: 'cc-welcome',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <!-- Hero -->
    <section style="background:linear-gradient(160deg,#0f766e 0%,#115e59 55%,#134e4a 100%);
                    color:#fff;padding:76px 20px 84px">
      <div style="max-width:1080px;margin:0 auto;display:grid;
                  grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:44px;align-items:center">
        <div>
          <div style="display:inline-flex;align-items:center;gap:8px;background:rgba(255,255,255,.14);
                      padding:6px 14px;border-radius:999px;font-size:13px;margin-bottom:20px">
            <mat-icon style="font-size:17px;width:17px;height:17px">verified</mat-icon>
            Outpatient clinic platform
          </div>
          <h1 style="color:#fff;font-size:44px;line-height:1.12;margin-bottom:16px">
            Healthcare scheduling,<br>records and billing —<br>in one place.
          </h1>
          <p style="font-size:17px;line-height:1.6;color:rgba(255,255,255,.86);max-width:520px">
            Book with the right doctor in seconds. Your visit history, prescriptions and
            invoices stay together, and your care team sees exactly what it needs to —
            and nothing more.
          </p>
          <div class="cc-row" style="margin-top:30px;gap:12px">
            <a mat-flat-button routerLink="/register"
               style="background:#fff;color:var(--cc-primary-dark);padding:0 26px;height:46px">
              Create your account
            </a>
            <a mat-stroked-button routerLink="/login"
               style="color:#fff;border-color:rgba(255,255,255,.5);height:46px;padding:0 22px">
              Sign in
            </a>
          </div>
        </div>

        <div class="cc-grid cc-grid-2" style="gap:14px">
          @for (item of highlights; track item.title) {
            <div style="background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.16);
                        border-radius:14px;padding:18px">
              <mat-icon style="color:#5eead4">{{ item.icon }}</mat-icon>
              <div style="font-weight:600;margin:8px 0 4px">{{ item.title }}</div>
              <div style="font-size:13px;color:rgba(255,255,255,.78);line-height:1.5">
                {{ item.text }}
              </div>
            </div>
          }
        </div>
      </div>
    </section>

    <!-- How a visit works: the whole product in three steps -->
    <section class="cc-page">
      <div class="cc-page-head">
        <div>
          <h2>How a visit works</h2>
          <div class="cc-sub">From booking to invoice, one connected record</div>
        </div>
      </div>

      <div class="cc-grid cc-grid-3">
        @for (step of steps; track step.title; let i = $index) {
          <div class="cc-card">
            <div class="cc-row" style="gap:12px;align-items:center">
              <div class="cc-step-number">{{ i + 1 }}</div>
              <div style="font-weight:600">{{ step.title }}</div>
            </div>
            <p class="cc-faint" style="margin:12px 0 0;line-height:1.6">{{ step.text }}</p>
          </div>
        }
      </div>
    </section>

    <!-- Departments: real, public data -->
    @if (departments().length) {
      <section class="cc-page" style="padding-top:0">
        <div class="cc-page-head">
          <div>
            <h2>Departments</h2>
            <div class="cc-sub">Specialist care across the clinic</div>
          </div>
        </div>

        <div class="cc-grid cc-grid-4">
          @for (dept of departments(); track dept.id) {
            <div class="cc-card cc-card-quiet">
              <div class="cc-row" style="gap:10px;align-items:center">
                <mat-icon class="cc-dept-icon">{{ iconFor(dept.name) }}</mat-icon>
                <div style="font-weight:600">{{ dept.name }}</div>
              </div>
              <div class="cc-faint" style="margin-top:8px;font-size:13px">
                {{ dept.doctorCount }}
                {{ dept.doctorCount === 1 ? 'doctor' : 'doctors' }} available
              </div>
            </div>
          }
        </div>

        <p class="cc-faint" style="margin-top:20px;text-align:center">
          <a routerLink="/register">Create an account</a> to see each doctor's
          availability and book a slot.
        </p>
      </section>
    }
  `,
  styles: [`
    .cc-step-number {
      width: 30px; height: 30px; border-radius: 50%;
      display: inline-flex; align-items: center; justify-content: center;
      background: var(--cc-primary); color: #fff;
      font-weight: 600; font-size: 14px; flex: none;
    }
    .cc-dept-icon { color: var(--cc-primary); }
  `]
})
export class WelcomeComponent {
  private readonly providers = inject(ProvidersService);

  readonly departments = signal<Department[]>([]);

  readonly highlights = [
    { icon: 'event_available', title: 'Real-time booking',
      text: 'Only genuinely free slots are offered — double-booking is impossible.' },
    { icon: 'clinical_notes', title: 'Records that follow you',
      text: 'Notes, diagnoses and prescriptions from every visit, in one history.' },
    { icon: 'receipt_long', title: 'Transparent billing',
      text: 'Invoices are issued at the price you were quoted when booking.' },
    { icon: 'shield_lock', title: 'Private by design',
      text: 'Your chart is visible to you and your treating doctor — nobody else.' },
  ];

  readonly steps = [
    { title: 'Choose a doctor',
      text: 'Browse by department, compare specialists, and see the days and hours each ' +
            'one actually consults before you commit to anything.' },
    { title: 'Book a real slot',
      text: 'Pick from the times that are genuinely free. The slot is held the moment ' +
            'you book it, so nobody else can take the one you just chose.' },
    { title: 'Everything follows',
      text: 'Check in on the day and watch your place in the queue. After the visit your ' +
            'notes, prescription and invoice are waiting in your account.' },
  ];

  /** Maps a department to a Material Symbol. Falls back rather than showing a
   *  blank tile for a department the clinic adds later. */
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

  constructor() {
    this.providers.departments().subscribe({
      next: list => this.departments.set(list),
      // Deliberately silent, and the only screen where that is right: this is a
      // marketing panel, not information anyone is acting on. A visitor arriving
      // while the backend is warming up should see the page without this section
      // rather than an error about an endpoint they never asked for. The section
      // hides itself when the list is empty.
      error: () => this.departments.set([])
    });
  }
}
