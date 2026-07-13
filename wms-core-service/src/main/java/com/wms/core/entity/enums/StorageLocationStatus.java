package com.wms.core.entity.enums;

/**
 * Bir depo raf gözünün (StorageLocation / Bin) operasyonel durumunu tanımlar.
 *
 * <ul>
 *   <li>{@code ACTIVE}  – Göz boş veya kısmen dolu; yeni stok kabul edebilir.</li>
 *   <li>{@code BLOCKED} – Göz manuel olarak kapatılmış; stok girişi yasak.</li>
 *   <li>{@code FULL}    – Hacim veya ağırlık kapasitesi doldu; otomatik set edilir.</li>
 * </ul>
 */
public enum StorageLocationStatus {

    /** Göz kullanılabilir; kapasite müsait. */
    ACTIVE,

    /** Depo şefi tarafından manuel olarak kapatılmış. */
    BLOCKED,

    /** Maksimum hacim veya ağırlık limitine ulaşıldı. Sistem tarafından otomatik atanır. */
    FULL
}
