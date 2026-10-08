-- =====================================================================
-- Billetsystem – databaseskema (PostgreSQL)
-- Kør i pgAdmin: højreklik på din database -> Query Tool -> åbn filen -> F5
-- Alle beløb gemmes i øre (29500 = 295,00 kr.) for at undgå afrundingsfejl.
-- =====================================================================

-- Kun under udvikling: nulstiller alt, så du kan køre filen igen og igen.
DROP VIEW  IF EXISTS v_ticket_sales;
DROP TABLE IF EXISTS payment, participant, customer_order, ticket_type, event, admin_user CASCADE;

-- ---------------------------------------------------------------------
-- event: selve messen. Egen tabel, så næste års messe blot er en ny række.
-- ---------------------------------------------------------------------
CREATE TABLE event (
                       id               SERIAL PRIMARY KEY,
                       name             VARCHAR(100) NOT NULL,
                       start_date       DATE         NOT NULL,
                       end_date         DATE         NOT NULL,
                       max_participants INTEGER CHECK (max_participants IS NULL OR max_participants > 0), -- NULL = ingen grænse
                       CHECK (end_date >= start_date)
);

-- ---------------------------------------------------------------------
-- ticket_type: billettyper med pris og gyldighedsperiode.
-- Early bird er bare en billettype med slutdato – priserne skifter automatisk.
-- ---------------------------------------------------------------------
CREATE TABLE ticket_type (
                             id          SERIAL PRIMARY KEY,
                             event_id    INTEGER      NOT NULL REFERENCES event(id),
                             name        VARCHAR(100) NOT NULL,
                             price_ore   INTEGER      NOT NULL CHECK (price_ore >= 0),
                             valid_from  TIMESTAMPTZ  NOT NULL,
                             valid_to    TIMESTAMPTZ,                       -- NULL = ingen slutdato
                             active      BOOLEAN      NOT NULL DEFAULT TRUE, -- admin kan slå en billettype fra
                             CHECK (valid_to IS NULL OR valid_to > valid_from)
);

-- ---------------------------------------------------------------------
-- customer_order: én bestilling (kan indeholde flere deltagere).
-- ("order" er et reserveret ord i SQL, derfor customer_order.)
-- ---------------------------------------------------------------------
CREATE TABLE customer_order (
                                id                SERIAL PRIMARY KEY,
                                contact_name      VARCHAR(150) NOT NULL,
                                email             VARCHAR(255) NOT NULL,
                                phone             VARCHAR(30),
                                status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED')),
                                total_ore         INTEGER      NOT NULL CHECK (total_ore >= 0),
                                stripe_session_id VARCHAR(255) UNIQUE,
                                note              TEXT,                         -- fx særlige ønsker (bruges især, når bordreservation kommer)
                                created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                paid_at           TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- participant: én række pr. billet/deltager (fulde navne, ikke kælenavne).
-- price_ore er en kopi af prisen på købstidspunktet, så historikken
-- stemmer, selv hvis en billetpris ændres senere.
-- ---------------------------------------------------------------------
CREATE TABLE participant (
                             id             SERIAL PRIMARY KEY,
                             order_id       INTEGER      NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
                             ticket_type_id INTEGER      NOT NULL REFERENCES ticket_type(id),
                             full_name      VARCHAR(150) NOT NULL,
                             price_ore      INTEGER      NOT NULL CHECK (price_ore >= 0)
);

-- ---------------------------------------------------------------------
-- payment: betalinger fra Stripe (kort / MobilePay). En ordre kan have flere
-- betalingsforsøg, derfor en separat tabel.
-- ---------------------------------------------------------------------
CREATE TABLE payment (
                         id                       SERIAL PRIMARY KEY,
                         order_id                 INTEGER     NOT NULL REFERENCES customer_order(id),
                         stripe_payment_intent_id VARCHAR(255) UNIQUE,
                         amount_ore               INTEGER     NOT NULL CHECK (amount_ore >= 0),
                         currency                 CHAR(3)     NOT NULL DEFAULT 'DKK',
                         method                   VARCHAR(30),           -- fx 'card' eller 'mobilepay'
                         status                   VARCHAR(20) NOT NULL
                             CHECK (status IN ('SUCCEEDED', 'FAILED', 'REFUNDED')),
                         created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- admin_user: logins til adminsiden. Gem ALDRIG kodeord i klartekst –
-- kun en hash (fx BCrypt via Spring Security).
-- ---------------------------------------------------------------------
CREATE TABLE admin_user (
                            id            SERIAL PRIMARY KEY,
                            username      VARCHAR(50)  NOT NULL UNIQUE,
                            password_hash VARCHAR(255) NOT NULL,
                            created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Indeks til de opslag adminsiden laver mest
CREATE INDEX idx_participant_order  ON participant(order_id);
CREATE INDEX idx_participant_ticket ON participant(ticket_type_id);
CREATE INDEX idx_order_status       ON customer_order(status);
CREATE INDEX idx_order_email        ON customer_order(email);
CREATE INDEX idx_payment_order      ON payment(order_id);

-- ---------------------------------------------------------------------
-- v_ticket_sales: færdigt overblik til adminsiden – antal solgte og
-- indtægt pr. billettype. Tæller kun BETALTE ordrer.
-- ---------------------------------------------------------------------
CREATE VIEW v_ticket_sales AS
SELECT tt.id                        AS ticket_type_id,
       e.name                       AS event_name,
       tt.name                      AS ticket_type,
       COUNT(p.id)                  AS sold,
       COALESCE(SUM(p.price_ore),0) AS revenue_ore
FROM ticket_type tt
         JOIN event e ON e.id = tt.event_id
         LEFT JOIN (participant p
    JOIN customer_order o ON o.id = p.order_id AND o.status = 'PAID')
                   ON p.ticket_type_id = tt.id
GROUP BY tt.id, e.name, tt.name;

-- ---------------------------------------------------------------------
-- Testdata (eksempel – ret priser og datoer, så de passer til virkeligheden)
-- Early bird: 295 kr. minus 100 kr. = 195 kr., 1.-7. oktober 2026.
-- ---------------------------------------------------------------------
INSERT INTO event (name, start_date, end_date)
VALUES ('Hobbymesse april 2027', '2027-04-03', '2027-04-04');

INSERT INTO ticket_type (event_id, name, price_ore, valid_from, valid_to)
VALUES
    ((SELECT id FROM event WHERE name = 'Hobbymesse april 2027'),
     'Early Bird', 19500, '2026-10-01 00:00:00+02', '2026-10-07 23:59:59+02'),
    ((SELECT id FROM event WHERE name = 'Hobbymesse april 2027'),
     'Standard',   29500, '2026-10-08 00:00:00+02', NULL);