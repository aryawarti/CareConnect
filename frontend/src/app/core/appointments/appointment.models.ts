export interface Appointment {
  id: string;
  patientId: string;
  doctorId: string;
  patientName: string;
  doctorName: string;
  startAt: string;
  endAt: string;
  status: 'REQUESTED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
  reason: string | null;
  feeSnapshot: number;
}

export interface FreeSlot {
  startAt: string;
  endAt: string;
}
