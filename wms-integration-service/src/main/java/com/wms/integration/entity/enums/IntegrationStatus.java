package com.wms.integration.entity.enums;

/**
 * Bir entegrasyon logunun anlık durumu.
 *
 * <ul>
 *   <li>{@code SUCCESS}  — ERP başarılı yanıt döndürdü</li>
 *   <li>{@code FAILED}   — Tüm retry denemeleri tükendi, kalıcı hata</li>
 *   <li>{@code RETRYING} — Outbox Worker yeniden deneme kuyruğunda</li>
 * </ul>
 */
public enum IntegrationStatus {
    SUCCESS,
    FAILED,
    RETRYING
}
