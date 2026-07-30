-- V2 seeded roles for modules that were either removed (LAB_TECHNICIAN, with
-- laboratory-service — see ADR-010) or never built (RADIOLOGIST, PHARMACIST,
-- NURSE, BILLING, SUPER_ADMIN). A roles table advertising authorities that
-- nothing enforces is misleading, so they go.
--
-- Guarded on having no members: if a real account somehow holds one of these,
-- deleting the row would silently strip that account's authority. Better to
-- leave it behind and have someone notice than to change what a user can do as
-- a side effect of a cleanup migration.
--
-- V2 is left untouched: it has already been applied, and Flyway validates the
-- checksums of applied migrations. Forward-only.
DELETE FROM roles r
 WHERE r.name IN ('LAB_TECHNICIAN', 'RADIOLOGIST', 'PHARMACIST',
                  'NURSE', 'BILLING', 'SUPER_ADMIN')
   AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.role_id = r.id);
