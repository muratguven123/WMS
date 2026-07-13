-- Picking list task assignment fields
ALTER TABLE picking_lists
    ADD COLUMN IF NOT EXISTS company_id UUID,
    ADD COLUMN IF NOT EXISTS assigned_user_id UUID,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_picking_lists_assigned ON picking_lists (assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_picking_lists_company ON picking_lists (company_id);

COMMENT ON COLUMN picking_lists.company_id IS 'Firma UUID — outbound order üzerinden set edilir';
COMMENT ON COLUMN picking_lists.assigned_user_id IS 'Atanan operatör (Keycloak sub veya wms_user_id)';
COMMENT ON COLUMN picking_lists.assigned_at IS 'Operatör atama zamanı';
