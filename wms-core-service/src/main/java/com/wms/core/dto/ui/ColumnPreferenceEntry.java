package com.wms.core.dto.ui;

/**
 * Kullanıcı tablo kolon tercihi JSONB girdisi.
 */
public record ColumnPreferenceEntry(
        String key,
        boolean visible,
        int sequence,
        Integer width
) {}
