export interface Diagnosis {
  id: string;
  code: string;
  description: string;
}

export interface Prescription {
  id: string;
  medication: string;
  dosage: string;
  frequency: string;
  durationDays: number;
  instructions: string | null;
}

export interface Amendment {
  previousNote: string | null;
  reason: string;
  amendedAt: string;
}

export interface Encounter {
  id: string;
  appointmentId: string;
  patientId: string;
  doctorId: string;
  patientName: string;
  doctorName: string;
  occurredAt: string;
  chiefComplaint: string | null;
  notes: string | null;
  status: 'OPEN' | 'SIGNED' | 'AMENDED';
  diagnoses: Diagnosis[];
  prescriptions: Prescription[];
  amendments: Amendment[];
}
