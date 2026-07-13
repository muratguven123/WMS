package com.wms.core.entity.enums;

/**
 * Bir süreç adımında hata oluştuğunda sistemin izleyeceği stratejiyi tanımlar.
 * <ul>
 *   <li>BLOCK           – Akışı durdur, kullanıcı müdahalesi gerekir.</li>
 *   <li>BYPASS          – Adımı atla, akışa devam et.</li>
 *   <li>ROUTE_TO_QUARANTINE – Ürünü/işlemi karantina bölgesine yönlendir.</li>
 * </ul>
 */
public enum ErrorStrategy {
    BLOCK,
    BYPASS,
    ROUTE_TO_QUARANTINE
}
