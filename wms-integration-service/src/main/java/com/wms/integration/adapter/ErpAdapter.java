package com.wms.integration.adapter;

import com.wms.integration.adapter.dto.*;

import java.util.List;

/**
 * ERP entegrasyon adaptörü sözleşmesi.
 *
 * <p>Her ERP sistemine (SAP, Oracle, Logo vb.) özgü somut implementasyonlar bu
 * arayüzü uygular. {@link ErpAdapterFactory} lokasyon ID'ye göre doğru
 * implementasyonu Spring context'ten çözümler.
 *
 * <p>Tüm metodlar senkron çalışır; asenkron gönderim Outbox Worker katmanı
 * tarafından yönetilir.
 */
public interface ErpAdapter {

    /**
     * Malzeme / ürün kartını ERP'ye gönderir.
     *
     * @param material gönderilecek malzeme verisi
     * @return ERP yanıtı (başarı/hata, dış referans no)
     */
    ErpResponse sendMaterialCard(MaterialDto material);

    /**
     * Stok hareketini ERP'ye gönderir.
     *
     * @param movement gönderilecek stok hareketi
     * @return ERP yanıtı
     */
    ErpResponse sendInventoryMovement(MovementDto movement);

    /**
     * Onaylanmış faturayı ERP'ye gönderir.
     *
     * @param invoice gönderilecek fatura (kur kilitli)
     * @return ERP yanıtı
     */
    ErpResponse sendInvoice(InvoiceDto invoice);

    /**
     * Onaylanmış mal kabul kaydını ERP'ye bildirir.
     */
    default ErpResponse sendGoodsReceipt(ReceiptApprovalDto receipt) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Goods receipt sync not implemented for this ERP adapter");
    }

    /**
     * Sevkiyat çıkışını ERP'ye bildirir (stok düşüm / goods issue).
     */
    default ErpResponse sendShipmentDispatch(ShipmentDispatchDto shipment) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Shipment dispatch sync not implemented for this ERP adapter");
    }

    /**
     * Güncel döviz kurlarını ERP/banka servisinden çeker.
     *
     * @return kur listesi
     */
    List<ExchangeRateDto> fetchExchangeRates();

    // -----------------------------------------------------------------------
    // İş isteri 7.4 — ek entegrasyon senaryoları.
    // Tüm metodlar default NOT_IMPLEMENTED döner: mevcut adaptörler
    // (SAP, LOGO, ORACLE vb.) yeniden derlenmeden çalışmaya devam eder;
    // her ERP yalnızca desteklediği senaryoyu override eder (geriye dönük uyum).
    // -----------------------------------------------------------------------

    /**
     * Cari hesap (müşteri/tedarikçi) kartını ERP'ye gönderir.
     *
     * @param customerAccount gönderilecek cari hesap verisi
     * @return ERP yanıtı
     */
    default ErpResponse sendCustomerAccount(CustomerAccountDto customerAccount) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Customer account sync not implemented for this ERP adapter");
    }

    /**
     * Satın alma siparişini ERP'ye gönderir.
     *
     * @param purchaseOrder gönderilecek satın alma siparişi
     * @return ERP yanıtı
     */
    default ErpResponse sendPurchaseOrder(PurchaseOrderDto purchaseOrder) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Purchase order sync not implemented for this ERP adapter");
    }

    /**
     * Satış siparişini ERP'ye gönderir.
     *
     * @param salesOrder gönderilecek satış siparişi
     * @return ERP yanıtı
     */
    default ErpResponse sendSalesOrder(SalesOrderDto salesOrder) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Sales order sync not implemented for this ERP adapter");
    }

    /**
     * İade bildirimini ERP'ye gönderir.
     *
     * @param returnNotice gönderilecek iade bildirimi
     * @return ERP yanıtı
     */
    default ErpResponse sendReturnNotice(ReturnNoticeDto returnNotice) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Return notice sync not implemented for this ERP adapter");
    }

    /**
     * Sayım sonucunu ERP'ye gönderir (stok düzeltme fişine dönüştürülür).
     *
     * @param countResult gönderilecek sayım sonucu
     * @return ERP yanıtı
     */
    default ErpResponse sendCountResult(CountResultDto countResult) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Count result sync not implemented for this ERP adapter");
    }

    /**
     * Muhasebe fişini ERP genel muhasebesine gönderir.
     *
     * @param voucher gönderilecek muhasebe fişi (borç/alacak dengeli)
     * @return ERP yanıtı
     */
    default ErpResponse sendAccountingVoucher(AccountingVoucherDto voucher) {
        return ErpResponse.failure("NOT_IMPLEMENTED",
                "Accounting voucher sync not implemented for this ERP adapter");
    }

    /**
     * Güncel vergi bilgilerini ERP'den çeker (INBOUND).
     *
     * <p>Default implementasyon boş liste döner — desteklemeyen adaptörlerde
     * senkronizasyon sessizce atlanır ({@link #fetchExchangeRates()} ile
     * aynı sözleşme).
     *
     * @return vergi bilgisi listesi
     */
    default List<TaxInfoDto> fetchTaxInfo() {
        return List.of();
    }
}
