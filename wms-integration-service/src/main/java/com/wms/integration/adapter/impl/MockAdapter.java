package com.wms.integration.adapter.impl;

import com.wms.integration.adapter.ErpAdapter;
import com.wms.integration.adapter.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Test ve geliştirme ortamı için mock ERP adaptörü.
 * Gerçek ağ çağrısı yapmaz; her işlem başarılı kabul edilir.
 */
@Slf4j
@Component("mockAdapter")
public class MockAdapter implements ErpAdapter {

    @Override
    public ErpResponse sendMaterialCard(MaterialDto material) {
        log.info("[Mock] sendMaterialCard -> sku={}", material.getSku());
        return ErpResponse.success("MOCK-MAT-" + material.getSku(), "Mock material sync OK");
    }

    @Override
    public ErpResponse sendInventoryMovement(MovementDto movement) {
        log.info("[Mock] sendInventoryMovement -> movementId={}", movement.getMovementId());
        return ErpResponse.success("MOCK-MOV-" + movement.getMovementId(), "Mock movement sync OK");
    }

    @Override
    public ErpResponse sendInvoice(InvoiceDto invoice) {
        log.info("[Mock] sendInvoice -> invoiceNumber={}", invoice.getInvoiceNumber());
        return ErpResponse.success("MOCK-INV-" + invoice.getInvoiceNumber(), "Mock invoice sync OK");
    }

    @Override
    public ErpResponse sendGoodsReceipt(ReceiptApprovalDto receipt) {
        log.info("[Mock] sendGoodsReceipt -> receiptNumber={}, items={}",
                receipt.getReceiptNumber(),
                receipt.getApprovedItems() != null ? receipt.getApprovedItems().size() : 0);
        return ErpResponse.success("MOCK-GR-" + receipt.getReceiptNumber(), "Mock goods receipt sync OK");
    }

    @Override
    public ErpResponse sendShipmentDispatch(ShipmentDispatchDto shipment) {
        log.info("[Mock] sendShipmentDispatch -> shipmentNumber={}, boxes={}",
                shipment.getShipmentNumber(),
                shipment.getBoxSsccNumbers() != null ? shipment.getBoxSsccNumbers().size() : 0);
        return ErpResponse.success("MOCK-SH-" + shipment.getShipmentNumber(), "Mock shipment dispatch sync OK");
    }

    @Override
    public List<ExchangeRateDto> fetchExchangeRates() {
        return List.of(
                ExchangeRateDto.builder()
                        .fromCurrency("USD").toCurrency("TRY")
                        .rate(new BigDecimal("32.00")).rateDate(LocalDate.now())
                        .build()
        );
    }

    // -----------------------------------------------------------------------
    // İş isteri 7.4 — ek entegrasyon senaryoları (tümü başarılı mock yanıt)
    // -----------------------------------------------------------------------

    @Override
    public ErpResponse sendCustomerAccount(CustomerAccountDto customerAccount) {
        log.info("[Mock] sendCustomerAccount -> customerCode={}", customerAccount.getCustomerCode());
        return ErpResponse.success("MOCK-CUST-" + customerAccount.getCustomerCode(),
                "Mock customer account sync OK");
    }

    @Override
    public ErpResponse sendPurchaseOrder(PurchaseOrderDto purchaseOrder) {
        log.info("[Mock] sendPurchaseOrder -> orderNumber={}, lines={}",
                purchaseOrder.getOrderNumber(),
                purchaseOrder.getLines() != null ? purchaseOrder.getLines().size() : 0);
        return ErpResponse.success("MOCK-PO-" + purchaseOrder.getOrderNumber(),
                "Mock purchase order sync OK");
    }

    @Override
    public ErpResponse sendSalesOrder(SalesOrderDto salesOrder) {
        log.info("[Mock] sendSalesOrder -> orderNumber={}, lines={}",
                salesOrder.getOrderNumber(),
                salesOrder.getLines() != null ? salesOrder.getLines().size() : 0);
        return ErpResponse.success("MOCK-SO-" + salesOrder.getOrderNumber(),
                "Mock sales order sync OK");
    }

    @Override
    public ErpResponse sendReturnNotice(ReturnNoticeDto returnNotice) {
        log.info("[Mock] sendReturnNotice -> referenceOrderNumber={}, reason={}",
                returnNotice.getReferenceOrderNumber(), returnNotice.getReturnReason());
        return ErpResponse.success("MOCK-RET-" + returnNotice.getReferenceOrderNumber(),
                "Mock return notice sync OK");
    }

    @Override
    public ErpResponse sendCountResult(CountResultDto countResult) {
        log.info("[Mock] sendCountResult -> countId={}, lines={}",
                countResult.getCountId(),
                countResult.getLines() != null ? countResult.getLines().size() : 0);
        return ErpResponse.success("MOCK-CNT-" + countResult.getCountId(),
                "Mock count result sync OK");
    }

    @Override
    public ErpResponse sendAccountingVoucher(AccountingVoucherDto voucher) {
        log.info("[Mock] sendAccountingVoucher -> voucherType={}, lines={}",
                voucher.getVoucherType(),
                voucher.getLines() != null ? voucher.getLines().size() : 0);
        return ErpResponse.success("MOCK-VCH-" + voucher.getVoucherType() + "-" + voucher.getVoucherDate(),
                "Mock accounting voucher sync OK");
    }

    @Override
    public List<TaxInfoDto> fetchTaxInfo() {
        log.info("[Mock] fetchTaxInfo -> sample data returned");
        return List.of(
                TaxInfoDto.builder()
                        .companyId(1L).locationId(1L)
                        .taxTypeCode("KDV_STANDART").rate(new BigDecimal("20"))
                        .countryCode("TR").validFrom(LocalDate.of(2024, 1, 1))
                        .build(),
                TaxInfoDto.builder()
                        .companyId(1L).locationId(1L)
                        .taxTypeCode("KDV_INDIRIMLI").rate(new BigDecimal("10"))
                        .countryCode("TR").validFrom(LocalDate.of(2024, 1, 1))
                        .build()
        );
    }
}
