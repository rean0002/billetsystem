-- Pladsreservation og praktisk info pr. event, til en database, der allerede er i brug.
--
-- Docker:  docker compose exec -T db psql -U billetsystem -d billetsystem < database/migrations/2026-11-03-pladsreservation.sql
-- pgAdmin: åbn filen i Query Tool på billetsystem-databasen og tryk F5
--
-- Filen kan køres flere gange uden at gøre skade.

-- Praktisk info på bestillingssiden, fx gæstebilletter og åbningstider
ALTER TABLE event ADD COLUMN IF NOT EXISTS public_info TEXT;

-- Fulde navne på andre (med egen billet), som gruppen vil sidde sammen med
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS seat_with TEXT;

-- Ønsker pr. deltager. Bordende er kun et ja/nej: årsagen (helbred) gemmes ikke,
-- fordi helbredsoplysninger er følsomme persondata.
ALTER TABLE participant ADD COLUMN IF NOT EXISTS beginner_table  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE participant ADD COLUMN IF NOT EXISTS needs_table_end BOOLEAN NOT NULL DEFAULT FALSE;
