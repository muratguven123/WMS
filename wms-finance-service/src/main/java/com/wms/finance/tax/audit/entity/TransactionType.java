package com.wms.finance.tax.audit.entity;

/**
 * Vergi hesaplama kaydının hangi iş nesnesiyle ilişkili olduğunu tanımlar.
 *
 * <p>Yeni işlem tipleri sisteme eklenirken bu enum'a yeni bir değer
 * eklenmeli ve ilgili Flyway migration'ı oluşturulmalıdır.
 */
public enum TransactionType {

    /** Fatura satır kalemi */
    INVOICE_LINE,

    /** İşlem ücreti / komisyon */
    TRANSACTION_FEE,

    /** Depo giriş hareketi */
    WAREHOUSE_RECEIPT,

    /** Depo çıkış hareketi */
    WAREHOUSE_DISPATCH,

    /** Dönemsel fatura */
    PERIODIC_INVOICE,

    /** İade işlemi */
    RETURN_ORDER
}
