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
}
