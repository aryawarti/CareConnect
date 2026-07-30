-- Database-per-service (ADR-003). One physical Postgres locally, one logical DB per service.
CREATE DATABASE careconnect_identity;
CREATE DATABASE careconnect_patient;
CREATE DATABASE careconnect_provider;
CREATE DATABASE careconnect_appointment;
CREATE DATABASE careconnect_medical_record;
CREATE DATABASE careconnect_billing;
CREATE DATABASE careconnect_notification;
CREATE DATABASE careconnect_queue;
