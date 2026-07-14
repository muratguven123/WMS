package com.wms.integration.outbox;

import java.util.List;

/**
 * Outbox mesaj tipi (jobCode) sabitleri.
 *
 * <p>{@link com.wms.integration.entity.OutboxMessage#getJobCode()} değerleri,
 * {@code integration_jobs.code} kayıtları ve {@link OutboxMessageProcessor}
 * yönlendirme switch'i bu sabitler üzerinden hizalanır — string literal
 * dağınıklığı ve yazım hatası riski önlenir.
 *
 * <p>Yeni senaryo eklerken: (1) sabit ekle, (2) {@code integration_jobs} seed
 * migration'ı yaz, (3) {@link OutboxMessageProcessor#dispatch} yönlendirmesini ekle.
 */
public final class OutboxMessageTypes {

    // -- Mevcut senaryolar ---------------------------------------------------

    /** Stok hareketi senkronizasyonu. */
    public static final String STOCK_MOVE = "STOCK_MOVE";

    /** Fatura senkronizasyonu. */
    public static final String INVOICE_SYNC = "INVOICE_SYNC";

    /** Malzeme kartı senkronizasyonu. */
    public static final String MAT_SYNC = "MAT_SYNC";

    /** Mal kabul senkronizasyonu. */
    public static final String RECEIPT_SYNC = "RECEIPT_SYNC";

    /** Sevkiyat çıkışı senkronizasyonu. */
    public static final String SHIPMENT_SYNC = "SHIPMENT_SYNC";

    // -- İş isteri 7.4 — yeni senaryolar --------------------------------------

    /** Cari hesap (müşteri/tedarikçi) kartı senkronizasyonu. */
    public static final String CUSTOMER_SYNC = "CUSTOMER_SYNC";

    /** Satın alma siparişi senkronizasyonu. */
    public static final String PURCHASE_ORDER_SYNC = "PURCHASE_ORDER_SYNC";

    /** Satış siparişi senkronizasyonu. */
    public static final String SALES_ORDER_SYNC = "SALES_ORDER_SYNC";

    /** İade bildirimi senkronizasyonu. */
    public static final String RETURN_SYNC = "RETURN_SYNC";

    /** Sayım sonucu senkronizasyonu. */
    public static final String COUNT_SYNC = "COUNT_SYNC";

    /** Muhasebe fişi senkronizasyonu. */
    public static final String VOUCHER_SYNC = "VOUCHER_SYNC";

    /** Vergi bilgisi çekme (INBOUND — Outbox değil, scheduler/manuel tetikleme). */
    public static final String TAX_INFO_PULL = "TAX_INFO_PULL";

    /**
     * Monitor filtre ekranları (dropdown) için tüm tanımlı tipler.
     */
    public static List<String> all() {
        return List.of(
                STOCK_MOVE, INVOICE_SYNC, MAT_SYNC, RECEIPT_SYNC, SHIPMENT_SYNC,
                CUSTOMER_SYNC, PURCHASE_ORDER_SYNC, SALES_ORDER_SYNC,
                RETURN_SYNC, COUNT_SYNC, VOUCHER_SYNC, TAX_INFO_PULL
        );
    }

    private OutboxMessageTypes() {
        // sabit sınıfı — örneklenemez
    }
}
