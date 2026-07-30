-- laboratory-service has been removed (see ADR-010). Nothing writes or reads
-- this table any more, so it goes.
--
-- Dropped in a NEW migration rather than by deleting V2__encounter_lab_links.sql:
-- V2 has already been applied to existing databases and Flyway validates the
-- checksum of every applied migration. Editing history would make the service
-- refuse to start against any database that had already run it. Forward-only
-- migrations are the rule, and this is exactly why.
DROP TABLE IF EXISTS encounter_lab_reports;
