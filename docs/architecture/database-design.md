# Database Design

Database-per-service (ADR-003): locally one PostgreSQL container with **one logical database per service** (`infra/postgres/init/01-create-databases.sql`); in a real deployment these would be separate instances. Services never read another service's database — cross-context data travels via API or events, referenced by **UUID**.

Conventions: UUID PKs, `snake_case`, audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`) on every table, Flyway migrations per service (`V1__init.sql`…), soft-delete via status fields where domain requires history.

## careconnect_identity
```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : grants
    users ||--o{ refresh_tokens : owns
    users { uuid id PK
      varchar email UK
      varchar password_hash
      varchar status
    }
    roles { uuid id PK
      varchar name UK
    }
    refresh_tokens { uuid id PK
      uuid user_id FK
      varchar token_hash
      timestamptz expires_at
      boolean revoked
    }
```
Note: `users` holds credentials only. Patient/doctor profile data lives in its owning service, linked by `user_id`. This avoids the common trap of identity-service becoming a "person service".

## careconnect_patient
```mermaid
erDiagram
    patients { uuid id PK
      uuid user_id UK "nullable; set for self-registered"
      varchar patient_number UK "human-readable MRN"
      varchar first_name
      varchar last_name
      date date_of_birth
      varchar gender
      varchar phone
      varchar email
      varchar address_line1
      varchar address_line2
      varchar city
      varchar state
      varchar postal_code
      varchar emergency_contact_name
      varchar emergency_contact_phone
      varchar status "ACTIVE|INACTIVE"
    }
```

*Design note:* address was originally sketched as `jsonb`; implemented as flat embedded
columns instead — no query-into-JSON requirement exists, `@Embeddable` is simpler than a JSON
mapper, and columns are constrainable. `jsonb` remains right when structure varies per row.

## careconnect_provider
```mermaid
erDiagram
    departments ||--o{ doctors : contains
    doctors ||--o{ availability_slots : defines
    doctors ||--o{ schedule_exceptions : takes
    departments { uuid id PK
      varchar name UK
    }
    doctors { uuid id PK
      uuid user_id UK
      varchar first_name
      varchar last_name
      varchar specialty
      uuid department_id FK
      numeric consultation_fee
      varchar status
    }
    availability_slots { uuid id PK
      uuid doctor_id FK
      smallint day_of_week
      time start_time
      time end_time
      smallint slot_minutes
    }
    schedule_exceptions { uuid id PK
      uuid doctor_id FK
      date exception_date
      varchar reason
    }
```

## careconnect_appointment
```mermaid
erDiagram
    appointments ||--o{ appointment_status_history : tracks
    appointments { uuid id PK
      uuid patient_id "ref patient-service"
      uuid doctor_id "ref provider-service"
      timestamptz start_at
      timestamptz end_at
      varchar status "REQUESTED|CONFIRMED|COMPLETED|CANCELLED|NO_SHOW"
      varchar reason
      numeric fee_snapshot "copied at booking (ADR-004)"
    }
    appointment_status_history { uuid id PK
      uuid appointment_id FK
      varchar from_status
      varchar to_status
      varchar changed_by
      timestamptz changed_at
    }
```
`fee_snapshot` and an exclusion constraint on `(doctor_id, tstzrange(start_at, end_at))` prevent double-booking at the database level — the app checks first, the constraint guarantees.

## careconnect_medical_record
```mermaid
erDiagram
    encounters ||--o{ diagnoses : records
    encounters ||--o{ prescriptions : issues
    encounters { uuid id PK
      uuid appointment_id UK "ref appointment-service"
      uuid patient_id
      uuid doctor_id
      text notes
      varchar status "OPEN|SIGNED|AMENDED"
    }
    diagnoses { uuid id PK
      uuid encounter_id FK
      varchar code "ICD-10-style"
      varchar description
    }
    prescriptions { uuid id PK
      uuid encounter_id FK
      varchar medication
      varchar dosage
      varchar frequency
      smallint duration_days
    }
```

## careconnect_billing
```mermaid
erDiagram
    invoices ||--o{ payments : settles
    invoices { uuid id PK
      varchar invoice_number UK
      uuid appointment_id UK
      uuid patient_id
      numeric amount
      varchar status "ISSUED|PAID|VOID"
      timestamptz issued_at
    }
    payments { uuid id PK
      uuid invoice_id FK
      numeric amount
      varchar method "SIMULATED"
      varchar status
      timestamptz paid_at
    }
```

## careconnect_notification
```mermaid
erDiagram
    notifications { uuid id PK
      uuid recipient_user_id
      varchar channel "EMAIL|IN_APP"
      varchar template_code
      jsonb payload
      varchar status "PENDING|SENT|FAILED"
      varchar dedupe_key UK "event id — idempotency"
      timestamptz sent_at
    }
    processed_events { varchar event_id PK
      varchar topic
      timestamptz processed_at
    }
```

## Cross-service consistency
No foreign keys across services (impossible by construction). Where a service needs another context's data at read time it either calls the owning API (fresh, coupled) or keeps a snapshot taken from events (`fee_snapshot`, denormalized names in invoices). Rule of thumb used: **snapshot facts that must not change retroactively** (fees, amounts, names on invoices); **query live data that must be current** (slot availability).
