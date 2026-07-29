export interface Payment {
  id: string;
  amount: number;
  method: string;
  reference: string;
  paidAt: string;
}

export interface Invoice {
  id: string;
  invoiceNumber: string;
  appointmentId: string;
  patientId: string;
  patientName: string;
  doctorName: string;
  amount: number;
  status: 'ISSUED' | 'PAID' | 'VOID';
  issuedAt: string;
  paidAt: string | null;
  voidedReason: string | null;
  payments: Payment[];
}
