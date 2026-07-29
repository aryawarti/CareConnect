export interface Department {
  id: string;
  name: string;
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
