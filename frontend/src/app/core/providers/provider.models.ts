export interface Department {
  id: string;
  name: string;
  /** Bookable doctors in this department. 0 means the browse card says so
   *  rather than letting a patient click into an empty department. */
  doctorCount: number;
}

/** ISO day-of-week, matching java.time.DayOfWeek and the backend's dayOfWeek. */
export const DAY_NAMES = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
export const DAY_NAMES_LONG = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday',
                               'Friday', 'Saturday', 'Sunday'] as const;

/**
 * A doctor as a directory card shows them.
 *
 * Deliberately not the full `Doctor`: a patient browsing has no business
 * receiving a colleague's email, phone or rejection reason. `workingDays` being
 * empty is the signal that nobody has published this doctor's hours yet, which
 * is why `bookable` exists as its own flag rather than being inferred.
 */
export interface DirectoryEntry {
  id: string;
  firstName: string;
  lastName: string;
  specialty: string;
  departmentId: string;
  departmentName: string;
  consultationFee: number;
  qualification: string | null;
  experienceYears: number | null;
  workingDays: number[];
  bookable: boolean;
}

/** Everything the doctor profile page needs, in one response. */
export interface DoctorProfile {
  id: string;
  firstName: string;
  lastName: string;
  specialty: string;
  departmentId: string;
  departmentName: string;
  consultationFee: number;
  qualification: string | null;
  experienceYears: number | null;
  bio: string | null;
  acceptingAppointments: boolean;
  weeklyAvailability: Slot[];
  upcomingTimeOff: ScheduleException[];
}

export interface ScheduleException {
  id: string;
  date: string;
  reason: string | null;
}

/** Groups a doctor's weekly windows by day for display: "Mon 09:00-13:00, 14:00-17:00". */
export function groupByDay(slots: Slot[]): { day: number; windows: Slot[] }[] {
  const byDay = new Map<number, Slot[]>();
  for (const slot of slots) {
    byDay.set(slot.dayOfWeek, [...(byDay.get(slot.dayOfWeek) ?? []), slot]);
  }
  return [...byDay.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([day, windows]) => ({
      day,
      windows: [...windows].sort((a, b) => a.startTime.localeCompare(b.startTime))
    }));
}

/** "09:00:00" -> "9:00 am". Times come from the API as ISO local times. */
export function formatTime(time: string): string {
  const [h, m] = time.split(':').map(Number);
  const suffix = h < 12 ? 'am' : 'pm';
  const hour = h % 12 === 0 ? 12 : h % 12;
  return `${hour}:${String(m).padStart(2, '0')} ${suffix}`;
}

export interface Doctor {
  id: string;
  firstName: string;
  lastName: string;
  specialty: string;
  departmentId: string;
  departmentName: string;
  consultationFee: number;
  email: string | null;
  phone: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  verification: 'PENDING' | 'APPROVED' | 'REJECTED';
  qualification: string | null;
  registrationNo: string | null;
  experienceYears: number | null;
  bio: string | null;
  rejectionReason: string | null;
}

export interface Slot {
  id?: string;
  dayOfWeek: number;      // ISO: 1=Mon … 7=Sun
  startTime: string;      // "09:00"
  endTime: string;
  slotMinutes: number;
}
