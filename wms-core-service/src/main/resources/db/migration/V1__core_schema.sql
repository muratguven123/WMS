-- =====================================================================
-- V1: WMS Core Schema — Organizasyon, Lokasyon, Yetkilendirme, Adres
-- Flyway migration
-- =====================================================================

-- ---------------------------------------------------------------
-- ENUM TYPES
-- ---------------------------------------------------------------
CREATE TYPE location_type AS ENUM (
    'CENTRAL', 'TRANSIT', 'VIRTUAL', 'DISTRIBUTION', 'RETURN'
);

CREATE TYPE zone_type AS ENUM (
    'STANDARD', 'COLD_ROOM', 'QUARANTINE', 'HAZARDOUS',
    'BULK', 'STAGING', 'SHIPPING', 'RECEIVING'
);

-- ---------------------------------------------------------------
-- 1. ORGANIZATIONS
-- ---------------------------------------------------------------
CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200)     NOT NULL,
    is_active   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

-- ---------------------------------------------------------------
-- 2. COMPANIES
-- ---------------------------------------------------------------
CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID             NOT NULL,
    name            VARCHAR(200)     NOT NULL,
    tax_number      VARCHAR(50)      NOT NULL,
    tax_office      VARCHAR(200),
    is_active       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,

    CONSTRAINT fk_company_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uk_company_tax_number
        UNIQUE (tax_number)
);

CREATE INDEX idx_company_org ON companies (organization_id);

-- ---------------------------------------------------------------
-- 3. COUNTRIES
-- ---------------------------------------------------------------
CREATE TABLE countries (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    iso_code   VARCHAR(3)       NOT NULL,
    name       VARCHAR(100)     NOT NULL,
    is_active  BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT uk_country_iso_code UNIQUE (iso_code)
);

-- ---------------------------------------------------------------
-- 4. REGIONS  (coğrafi bölge — depo lokasyonu için)
-- ---------------------------------------------------------------
CREATE TABLE regions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id UUID             NOT NULL,
    name       VARCHAR(150)     NOT NULL,
    is_active  BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_region_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT uk_region_country_name
        UNIQUE (country_id, name)
);

-- ---------------------------------------------------------------
-- 5. LOCATIONS  (depolar / tesisler)
-- ---------------------------------------------------------------
CREATE TABLE locations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID             NOT NULL,
    region_id  UUID             NOT NULL,
    name       VARCHAR(200)     NOT NULL,
    type       VARCHAR(30)      NOT NULL,
    timezone   VARCHAR(50)      NOT NULL,
    is_active  BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_location_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_location_region
        FOREIGN KEY (region_id)  REFERENCES regions (id)
);

CREATE INDEX idx_location_company ON locations (company_id);
CREATE INDEX idx_location_region  ON locations (region_id);

-- ---------------------------------------------------------------
-- 6. ZONES  (depo bölgeleri)
-- ---------------------------------------------------------------
CREATE TABLE zones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID             NOT NULL,
    name        VARCHAR(100)     NOT NULL,
    type        VARCHAR(30)      NOT NULL,
    is_active   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT fk_zone_location
        FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT uk_zone_location_name
        UNIQUE (location_id, name)
);

-- ---------------------------------------------------------------
-- 7. USERS
-- ---------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100)     NOT NULL,
    email         VARCHAR(255)     NOT NULL,
    password_hash VARCHAR(255)     NOT NULL,
    is_active     BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email    UNIQUE (email)
);

-- ---------------------------------------------------------------
-- 8. ROLES
-- ---------------------------------------------------------------
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)     NOT NULL,
    permissions JSONB,
    is_active   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT uk_role_name UNIQUE (name)
);

-- ---------------------------------------------------------------
-- 9. USER_ACCESSES  (kullanıcı yetkilendirme matrisi)
-- ---------------------------------------------------------------
CREATE TABLE user_accesses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    company_id  UUID NOT NULL,
    location_id UUID,            -- NULL = şirketteki tüm lokasyonlar
    role_id     UUID NOT NULL,

    CONSTRAINT fk_user_access_user
        FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_user_access_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_user_access_location
        FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_user_access_role
        FOREIGN KEY (role_id)    REFERENCES roles (id),
    CONSTRAINT uk_user_access
        UNIQUE (user_id, company_id, location_id, role_id)
);

CREATE INDEX idx_user_access_user    ON user_accesses (user_id);
CREATE INDEX idx_user_access_company ON user_accesses (company_id);

-- ---------------------------------------------------------------
-- 10. TRANSACTION_LOGS  (denetim izi / audit log)
-- ---------------------------------------------------------------
CREATE TABLE transaction_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     UUID         NOT NULL,
    location_id    UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    action_type    VARCHAR(50)  NOT NULL,
    payload        JSONB,
    created_at_utc TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_txlog_company
        FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_txlog_location
        FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_txlog_user
        FOREIGN KEY (user_id)     REFERENCES users (id)
);

CREATE INDEX idx_txlog_company  ON transaction_logs (company_id);
CREATE INDEX idx_txlog_location ON transaction_logs (location_id);
CREATE INDEX idx_txlog_created  ON transaction_logs (created_at_utc);

-- ---------------------------------------------------------------
-- 11. STATE_PROVINCES  (adres hiyerarşisi — eyalet/il)
-- ---------------------------------------------------------------
CREATE TABLE state_provinces (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id UUID             NOT NULL,
    name       VARCHAR(150)     NOT NULL,
    code       VARCHAR(10),
    is_active  BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_state_province_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT uk_state_country_code
        UNIQUE (country_id, code)
);

CREATE INDEX idx_state_country ON state_provinces (country_id);

-- ---------------------------------------------------------------
-- 12. CITIES  (adres hiyerarşisi — şehir)
-- ---------------------------------------------------------------
CREATE TABLE cities (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id        UUID             NOT NULL,
    state_province_id UUID,            -- NULL: eyalet yapısı olmayan ülkeler
    name              VARCHAR(150)     NOT NULL,
    is_active         BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT fk_city_country
        FOREIGN KEY (country_id)        REFERENCES countries (id),
    CONSTRAINT fk_city_state_province
        FOREIGN KEY (state_province_id) REFERENCES state_provinces (id)
);

CREATE INDEX idx_city_country ON cities (country_id);
CREATE INDEX idx_city_state   ON cities (state_province_id);

-- ---------------------------------------------------------------
-- 13. DISTRICTS  (adres hiyerarşisi — ilçe)
-- ---------------------------------------------------------------
CREATE TABLE districts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city_id    UUID             NOT NULL,
    name       VARCHAR(150)     NOT NULL,
    is_active  BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_district_city
        FOREIGN KEY (city_id) REFERENCES cities (id)
);

CREATE INDEX idx_district_city ON districts (city_id);

-- ---------------------------------------------------------------
-- 14. NEIGHBORHOODS  (adres hiyerarşisi — mahalle)
-- ---------------------------------------------------------------
CREATE TABLE neighborhoods (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    district_id UUID             NOT NULL,
    name        VARCHAR(200)     NOT NULL,
    zip_code    VARCHAR(20),
    is_active   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT fk_neighborhood_district
        FOREIGN KEY (district_id) REFERENCES districts (id)
);

CREATE INDEX idx_neighborhood_district ON neighborhoods (district_id);
