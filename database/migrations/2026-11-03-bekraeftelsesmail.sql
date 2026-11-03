-- Holder styr på, om bekræftelsesmails er sendt, til en database, der allerede er i brug.
--
-- Docker:  docker compose exec -T db psql -U billetsystem -d billetsystem < database/migrations/2026-11-03-bekraeftelsesmail.sql
-- pgAdmin: åbn filen i Query Tool på billetsystem-databasen og tryk F5
--
-- Filen kan køres flere gange uden at gøre skade.

-- Hvornår kunden fik bekræftelsen på bestillingen, og hvornår mailen om modtaget betaling blev sendt
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS confirmation_sent_at TIMESTAMPTZ;
ALTER TABLE customer_order ADD COLUMN IF NOT EXISTS paid_mail_sent_at    TIMESTAMPTZ;
