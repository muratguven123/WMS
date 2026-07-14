-- V26: Company admin — tax_number benzersizlik kısıtı doğrulama
--
-- V1'de uk_company_tax_number zaten tanımlı; bu migration ortamlarda
-- kısıtın mevcut olduğundan emin olur (idempotent) ve admin sorguları
-- için is_active üzerinde yardımcı indeks ekler.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_company_tax_number'
          AND conrelid = 'companies'::regclass
    ) THEN
        ALTER TABLE companies
            ADD CONSTRAINT uk_company_tax_number UNIQUE (tax_number);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_company_is_active ON companies (is_active);
