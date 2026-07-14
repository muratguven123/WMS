-- V27: Firma Yönetimi tablosu (COMPANY_LIST) dinamik kolon şeması
-- DataTable screenCode="COMPANY_LIST" → GET /api/ui/screens/COMPANY_LIST/table-schema

CREATE OR REPLACE FUNCTION wms_seed_table_screen(
    p_code VARCHAR,
    p_name VARCHAR,
    p_columns JSONB
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    v_screen_id BIGINT;
    col JSONB;
BEGIN
    INSERT INTO screens (code, name, is_active, created_at)
    VALUES (p_code, p_name, TRUE, now())
    ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
    RETURNING id INTO v_screen_id;

    IF v_screen_id IS NULL THEN
        SELECT id INTO v_screen_id FROM screens WHERE code = p_code;
    END IF;

    FOR col IN SELECT * FROM jsonb_array_elements(p_columns)
    LOOP
        INSERT INTO table_column_defs (
            screen_id, column_key, label_key, data_type,
            default_visible, default_sequence, locked, is_active, created_at
        ) VALUES (
            v_screen_id,
            col->>'key',
            col->>'label',
            (col->>'type')::column_data_type,
            COALESCE((col->>'visible')::boolean, TRUE),
            COALESCE((col->>'seq')::int, 0),
            COALESCE((col->>'locked')::boolean, FALSE),
            TRUE,
            now()
        ) ON CONFLICT (screen_id, column_key) DO NOTHING;
    END LOOP;
END;
$$;

SELECT wms_seed_table_screen('COMPANY_LIST', 'Company List', '[
  {"key":"name","label":"columns.company.name","type":"STRING","seq":0,"locked":true},
  {"key":"organizationName","label":"columns.company.organization","type":"STRING","seq":1},
  {"key":"taxNumber","label":"columns.company.taxNumber","type":"STRING","seq":2},
  {"key":"taxOffice","label":"columns.company.taxOffice","type":"STRING","seq":3},
  {"key":"locationCount","label":"columns.company.locationCount","type":"NUMBER","seq":4},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":5},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

DROP FUNCTION wms_seed_table_screen(VARCHAR, VARCHAR, JSONB);
