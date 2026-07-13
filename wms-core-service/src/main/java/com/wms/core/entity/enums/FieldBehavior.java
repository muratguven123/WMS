package com.wms.core.entity.enums;

/**
 * Bir ekran alanının çalışma zamanında alabileceği davranış değerleri.
 *
 * <ul>
 *   <li>{@code MANDATORY}  — Alan zorunludur; boş bırakılamaz.</li>
 *   <li>{@code HIDDEN}     — Alan arayüzde görünmez; sunucu tarafında da işlenmez.</li>
 *   <li>{@code READ_ONLY}  — Alan gösterilir ancak kullanıcı düzenleyemez.</li>
 *   <li>{@code OPTIONAL}   — Alan gösterilir, doldurulması zorunlu değildir.</li>
 * </ul>
 *
 * {@link FieldBehaviorRule#behavior} ve {@link ScreenField#defaultBehavior} tarafından kullanılır.
 */
public enum FieldBehavior {
    MANDATORY,
    HIDDEN,
    READ_ONLY,
    OPTIONAL
}
