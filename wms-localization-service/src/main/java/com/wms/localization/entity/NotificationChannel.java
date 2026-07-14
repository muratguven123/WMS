package com.wms.localization.entity;

/**
 * Bildirim şablonunun gönderim kanalı.
 *
 * <ul>
 *   <li>{@link #EMAIL} — subject + body birlikte kullanılır; subject zorunludur.</li>
 *   <li>{@link #SMS}   — yalnızca body kullanılır; subject boş bırakılır.</li>
 *   <li>{@link #PUSH}  — kısa başlık (subject) + body; subject opsiyoneldir.</li>
 * </ul>
 *
 * DB'de {@code EnumType.STRING} olarak saklanır — enum sırası değişse de veri bozulmaz.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH
}
