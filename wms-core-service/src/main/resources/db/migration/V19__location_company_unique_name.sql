-- Depo adı şirket içinde benzersiz olmalı (aktif kayıtlar)
CREATE UNIQUE INDEX IF NOT EXISTS uk_location_company_name_active
    ON locations (company_id, lower(name))
    WHERE is_active = TRUE;
