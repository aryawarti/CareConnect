/** Laboratory domain models mirrored from laboratory-service DTOs. */

export type OrderStatus =
  | 'ORDERED' | 'COLLECTED' | 'IN_PROCESS' | 'REPORTED' | 'VERIFIED'
  | 'REJECTED' | 'CANCELLED';

export type ResultFlag = 'NORMAL' | 'HIGH' | 'LOW' | 'CRITICAL' | null;

export interface Analyte {
  id: string;
  name: string;
  unit: string;
  refLow: number | null;
  refHigh: number | null;
}

export interface CatalogueTest {
  id: string;
  code: string;
  name: string;
  specimenType: string;
  department: string;
  price: number;
  tatMinutes: number;
  analytes: Analyte[];
}

export interface LabResult {
  analyteName: string;
  value: string;
  unit: string;
  refLow: number | null;
  refHigh: number | null;
  flag: ResultFlag;
}

export interface OrderItem {
  id: string;
  testCode: string;
  testName: string;
  price: number;
  results: LabResult[];
}

export interface Sample {
  accessionNo: string;
  specimenType: string;
  collectedAt: string;
}

export interface LabOrder {
  id: string;
  orderNumber: string;
  encounterId: string | null;
  patientId: string;
  doctorId: string;
  patientName: string;
  doctorName: string;
  priority: 'ROUTINE' | 'URGENT' | 'STAT';
  status: OrderStatus;
  clinicalIndication: string | null;
  orderedAt: string;
  total: number;
  items: OrderItem[];
  samples: Sample[];
}

export interface CreateOrderRequest {
  encounterId?: string | null;
  patientId: string;
  clinicalIndication?: string;
  priority?: 'ROUTINE' | 'URGENT' | 'STAT';
  testIds: string[];
}

export interface ResultEntry {
  analyteId: string;
  value: string;
}

export interface EnterResultsRequest {
  orderItemId: string;
  results: ResultEntry[];
}
