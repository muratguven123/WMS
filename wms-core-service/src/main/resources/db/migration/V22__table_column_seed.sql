-- =====================================================================
-- V22: Table column seed — liste ekranları (İş İsteri 16)
-- =====================================================================

-- Helper: screen + columns seed
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

SELECT wms_seed_table_screen('ADDRESS_LIST', 'Address List', '[
  {"key":"id","label":"columns.common.id","type":"NUMBER","seq":0,"locked":true},
  {"key":"country","label":"columns.address.country","type":"STRING","seq":1},
  {"key":"formattedAddress","label":"columns.address.formatted","type":"STRING","seq":2},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('TAX_RATE_LIST', 'Tax Rate List', '[
  {"key":"code","label":"columns.tax.code","type":"STRING","seq":0},
  {"key":"rate","label":"columns.tax.rate","type":"NUMBER","seq":1},
  {"key":"effectiveDate","label":"columns.tax.effectiveDate","type":"DATE","seq":2},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":3}
]'::jsonb);

SELECT wms_seed_table_screen('TAX_RATE_VERSION_LIST', 'Tax Rate Versions', '[
  {"key":"version","label":"columns.tax.version","type":"NUMBER","seq":0},
  {"key":"rate","label":"columns.tax.rate","type":"NUMBER","seq":1},
  {"key":"effectiveFrom","label":"columns.tax.effectiveFrom","type":"DATE","seq":2}
]'::jsonb);

SELECT wms_seed_table_screen('CURRENCY_RATE_LIST', 'Currency Rates', '[
  {"key":"pair","label":"columns.fx.pair","type":"STRING","seq":0},
  {"key":"rate","label":"columns.fx.rate","type":"NUMBER","seq":1},
  {"key":"updatedAt","label":"columns.common.updatedAt","type":"DATE","seq":2}
]'::jsonb);

SELECT wms_seed_table_screen('CURRENCY_RATE_HISTORY_LIST', 'Currency History', '[
  {"key":"date","label":"columns.common.date","type":"DATE","seq":0},
  {"key":"rate","label":"columns.fx.rate","type":"NUMBER","seq":1},
  {"key":"source","label":"columns.fx.source","type":"STRING","seq":2}
]'::jsonb);

SELECT wms_seed_table_screen('COUNTRY_LIST', 'Country List', '[
  {"key":"isoCode","label":"columns.country.iso","type":"STRING","seq":0,"locked":true},
  {"key":"name","label":"columns.country.name","type":"STRING","seq":1},
  {"key":"stateCount","label":"columns.country.stateCount","type":"NUMBER","seq":2},
  {"key":"cityCount","label":"columns.country.cityCount","type":"NUMBER","seq":3},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":4},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('ADDRESS_TEMPLATE_FIELD_LIST', 'Address Template Fields', '[
  {"key":"sequence","label":"columns.common.sequence","type":"NUMBER","seq":0},
  {"key":"fieldKey","label":"columns.template.fieldKey","type":"STRING","seq":1},
  {"key":"mandatory","label":"columns.template.mandatory","type":"BOOLEAN","seq":2},
  {"key":"regex","label":"columns.template.regex","type":"STRING","seq":3},
  {"key":"errorKey","label":"columns.template.errorKey","type":"STRING","seq":4},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('LANGUAGE_LIST', 'Language List', '[
  {"key":"code","label":"columns.lang.code","type":"STRING","seq":0},
  {"key":"name","label":"columns.lang.name","type":"STRING","seq":1},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":2},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('IMPORT_RESULT_LIST', 'Import Results', '[
  {"key":"row","label":"columns.import.row","type":"NUMBER","seq":0},
  {"key":"message","label":"columns.import.message","type":"STRING","seq":1}
]'::jsonb);

SELECT wms_seed_table_screen('AUDIT_LOG_LIST', 'Audit Log', '[
  {"key":"timestamp","label":"columns.audit.timestamp","type":"DATE","seq":0},
  {"key":"user","label":"columns.audit.user","type":"STRING","seq":1},
  {"key":"entity","label":"columns.audit.entity","type":"STRING","seq":2},
  {"key":"action","label":"columns.audit.action","type":"STRING","seq":3},
  {"key":"details","label":"columns.audit.details","type":"STRING","seq":4}
]'::jsonb);

SELECT wms_seed_table_screen('USER_LIST', 'User List', '[
  {"key":"username","label":"columns.user.username","type":"STRING","seq":0},
  {"key":"email","label":"columns.user.email","type":"STRING","seq":1},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":2},
  {"key":"role","label":"columns.user.role","type":"STRING","seq":3},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('ORG_HIERARCHY_LIST', 'Org Hierarchy', '[
  {"key":"type","label":"columns.org.type","type":"STRING","seq":0},
  {"key":"name","label":"columns.org.name","type":"STRING","seq":1},
  {"key":"parent","label":"columns.org.parent","type":"STRING","seq":2},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('INTEGRATION_LOG_LIST', 'Integration Log', '[
  {"key":"id","label":"columns.common.id","type":"NUMBER","seq":0},
  {"key":"type","label":"columns.integration.type","type":"STRING","seq":1},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":2},
  {"key":"createdAt","label":"columns.common.createdAt","type":"DATE","seq":3}
]'::jsonb);

SELECT wms_seed_table_screen('APPROVAL_QUEUE_LIST', 'Approval Queue', '[
  {"key":"id","label":"columns.common.id","type":"NUMBER","seq":0},
  {"key":"type","label":"columns.approval.type","type":"STRING","seq":1},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":2},
  {"key":"requestedAt","label":"columns.approval.requestedAt","type":"DATE","seq":3},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('WORKFLOW_CONFIG_LIST', 'Workflow Config', '[
  {"key":"code","label":"columns.workflow.code","type":"STRING","seq":0},
  {"key":"name","label":"columns.workflow.name","type":"STRING","seq":1},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":2}
]'::jsonb);

SELECT wms_seed_table_screen('UI_RULE_LIST', 'UI Rules', '[
  {"key":"id","label":"columns.common.id","type":"NUMBER","seq":0},
  {"key":"field","label":"columns.ui.field","type":"STRING","seq":1},
  {"key":"behavior","label":"columns.ui.behavior","type":"STRING","seq":2},
  {"key":"priority","label":"columns.ui.priority","type":"NUMBER","seq":3},
  {"key":"actions","label":"columns.common.actions","type":"CUSTOM","seq":99,"locked":true}
]'::jsonb);

SELECT wms_seed_table_screen('INVENTORY_TRANSFER_LIST', 'Inventory Transfer', '[
  {"key":"sku","label":"columns.inventory.sku","type":"STRING","seq":0},
  {"key":"from","label":"columns.inventory.from","type":"STRING","seq":1},
  {"key":"to","label":"columns.inventory.to","type":"STRING","seq":2},
  {"key":"qty","label":"columns.common.qty","type":"NUMBER","seq":3}
]'::jsonb);

SELECT wms_seed_table_screen('SHIPPING_OUTBOUND_LIST', 'Shipping Outbound', '[
  {"key":"id","label":"columns.common.id","type":"NUMBER","seq":0},
  {"key":"order","label":"columns.shipping.order","type":"STRING","seq":1},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":2},
  {"key":"carrier","label":"columns.shipping.carrier","type":"STRING","seq":3}
]'::jsonb);

SELECT wms_seed_table_screen('REALTIME_TEST_LIST', 'Realtime Test', '[
  {"key":"time","label":"columns.common.time","type":"DATE","seq":0},
  {"key":"topic","label":"columns.realtime.topic","type":"STRING","seq":1},
  {"key":"payload","label":"columns.realtime.payload","type":"STRING","seq":2}
]'::jsonb);

SELECT wms_seed_table_screen('FINANCE_TRANSACTION_LIST', 'Finance Transactions', '[
  {"key":"id","label":"columns.common.id","type":"NUMBER","seq":0},
  {"key":"type","label":"columns.finance.type","type":"STRING","seq":1},
  {"key":"amount","label":"columns.finance.amount","type":"NUMBER","seq":2},
  {"key":"date","label":"columns.common.date","type":"DATE","seq":3}
]'::jsonb);

SELECT wms_seed_table_screen('FINANCE_BALANCE_LIST', 'Finance Balances', '[
  {"key":"account","label":"columns.finance.account","type":"STRING","seq":0},
  {"key":"balance","label":"columns.finance.balance","type":"NUMBER","seq":1},
  {"key":"currency","label":"columns.finance.currency","type":"STRING","seq":2}
]'::jsonb);

SELECT wms_seed_table_screen('INVOICE_LIST', 'Invoice List', '[
  {"key":"number","label":"columns.invoice.number","type":"STRING","seq":0},
  {"key":"customer","label":"columns.invoice.customer","type":"STRING","seq":1},
  {"key":"amount","label":"columns.finance.amount","type":"NUMBER","seq":2},
  {"key":"status","label":"columns.common.status","type":"STRING","seq":3}
]'::jsonb);

SELECT wms_seed_table_screen('LOCALE_PREVIEW_LIST', 'Locale Preview', '[
  {"key":"sample","label":"columns.locale.sample","type":"STRING","seq":0},
  {"key":"raw","label":"columns.locale.raw","type":"STRING","seq":1},
  {"key":"display","label":"columns.locale.display","type":"STRING","seq":2}
]'::jsonb);

DROP FUNCTION wms_seed_table_screen(VARCHAR, VARCHAR, JSONB);
