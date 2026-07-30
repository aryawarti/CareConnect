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

/**
 * One recorded read of clinical data. Deliberately carries no clinical content:
 * the trail answers "who looked", and a log that leaked the data it protects
 * would defeat itself.
 */
export interface AccessLogEntry {
  id: string;
  actorUserId: string;
  actorRole: string;
  actorName: string | null;
  patientId: string;
  encounterId: string | null;
  action: 'VIEW_ENCOUNTER' | 'LIST_PATIENT_HISTORY' | 'LIST_OWN_HISTORY';
  selfAccess: boolean;
  accessedAt: string;
  correlationId: string | null;
}
