-- =============================================================================
-- V19 — state/city master-data cascade (STATE/CITY master_data_source)
--
-- Eyalet, şehir, ilçe, mahalle artık field_key'e göre değil master_data_source
-- metadata'sına göre generic render edilir. zip_code FIXED kalır.
-- =============================================================================

ALTER TABLE localization.address_template_field
    DROP CONSTRAINT IF EXISTS chk_atf_master_data_source;
ALTER TABLE localization.address_template_field
    ADD CONSTRAINT chk_atf_master_data_source
        CHECK (master_data_source IN ('NONE', 'STATE', 'CITY', 'DISTRICT', 'NEIGHBORHOOD'));

UPDATE localization.address_template_field
SET field_type = 'MASTER_SELECT',
    master_data_source = 'STATE',
    parent_field_key = '__country__'
WHERE field_key = 'state';

UPDATE localization.address_template_field
SET field_type = 'MASTER_SELECT',
    master_data_source = 'CITY',
    parent_field_key = '__country__'
WHERE field_key = 'city';

UPDATE localization.address_template_field
SET parent_field_key = 'city'
WHERE field_key = 'district';

COMMENT ON COLUMN localization.address_template_field.master_data_source
    IS 'MASTER_SELECT ise core-service cascade kaynağı: STATE/CITY/DISTRICT/NEIGHBORHOOD';
