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
}
