-- Tilføjer tabellen payment_settings til en database, der allerede er i brug.
-- (schema.sql nulstiller alt, så den må ikke køres på en database med rigtige bestillinger.)
--
-- Docker:  docker compose exec -T db psql -U billetsystem -d billetsystem < database/migrations/2026-11-03-payment-settings.sql
-- pgAdmin: åbn filen i Query Tool på billetsystem-databasen og tryk F5
--
-- Filen kan køres flere gange uden at gøre skade.

-- ---------------------------------------------------------------------
-- payment_settings: betalingsoplysninger, som kunden ser efter bestilling
-- (bankoverførsel og evt. MobilePay). Altid præcis én række (id = 1),
-- så arrangørerne kan rette den på adminsiden.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_settings (
                                  id               INTEGER      PRIMARY KEY CHECK (id = 1),
                                  account_holder   VARCHAR(100),
                                  reg_number       VARCHAR(4)   CHECK (reg_number ~ '^[0-9]{4}$'),
                                  account_number   VARCHAR(10)  CHECK (account_number ~ '^[0-9]{1,10}$'),
                                  mobilepay_number VARCHAR(30),
                                  payment_days     INTEGER      NOT NULL DEFAULT 7 CHECK (payment_days BETWEEN 1 AND 60),
                                  instructions     TEXT,
                                  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO payment_settings (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
