export interface Address {
  line1: string | null;
  line2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
}

export interface Patient {
  id: string;
  patientNumber: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | 'UNDISCLOSED';
  phone: string | null;
  email: string | null;
  address: Address | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedEnvelope<T> {
  data: T[];
  meta: PageMeta;
}

export interface Envelope<T> {
  data: T;
}
