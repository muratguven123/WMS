package com.wms.billing.mapper;

import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.entity.InvoiceItem;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.dto.ExchangeDifferenceResponse;
import com.wms.billing.dto.InvoiceDto;
import com.wms.billing.dto.InvoiceItemResultDto;
import com.wms.billing.dto.InvoiceResponse;
import com.wms.billing.domain.entity.ExchangeDifferenceLog;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class InvoiceMapper {

    public Invoice toEntity(InvoiceDto calculated, String invoiceNumber, Long locationId) {
        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .customerId(calculated.customerId())
                .locationId(locationId)
                .issueDate(LocalDateTime.now())
                .invoiceCurrency(calculated.invoiceCurrency())
                .accountingCurrency(calculated.accountingCurrency())
                .exchangeRateDate(calculated.exchangeRateDate())
                .exchangeRateValue(calculated.exchangeRateValue())
                .subtotalOriginal(calculated.subtotalOriginal())
                .taxAmountOriginal(calculated.taxAmountOriginal())
                .grandTotalOriginal(calculated.grandTotalOriginal())
                .grandTotalAccounting(calculated.grandTotalAccounting())
                .status(InvoiceStatus.DRAFT)
                .items(new ArrayList<>())
                .build();

        List<InvoiceItem> items = calculated.items().stream()
                .map(line -> toItemEntity(line, invoice))
                .toList();
        invoice.getItems().addAll(items);
        return invoice;
    }

    public void applyCalculated(Invoice invoice, InvoiceDto calculated) {
        invoice.setInvoiceCurrency(calculated.invoiceCurrency());
        invoice.setAccountingCurrency(calculated.accountingCurrency());
        invoice.setExchangeRateDate(calculated.exchangeRateDate());
        invoice.setExchangeRateValue(calculated.exchangeRateValue());
        invoice.setSubtotalOriginal(calculated.subtotalOriginal());
        invoice.setTaxAmountOriginal(calculated.taxAmountOriginal());
        invoice.setGrandTotalOriginal(calculated.grandTotalOriginal());
        invoice.setGrandTotalAccounting(calculated.grandTotalAccounting());

        invoice.getItems().clear();
        calculated.items().stream()
                .map(line -> toItemEntity(line, invoice))
                .forEach(invoice.getItems()::add);
    }

    public InvoiceResponse toResponse(Invoice invoice) {
        return toResponse(invoice, false);
    }

    public InvoiceResponse toPreviewResponse(InvoiceDto calculated, Long customerId, Long locationId) {
        return InvoiceResponse.builder()
                .id(null)
                .invoiceNumber(null)
                .customerId(customerId)
                .locationId(locationId)
                .issueDate(null)
                .createdAt(null)
                .invoiceCurrency(calculated.invoiceCurrency())
                .accountingCurrency(calculated.accountingCurrency())
                .exchangeRateDate(calculated.exchangeRateDate())
                .exchangeRateValue(calculated.exchangeRateValue())
                .items(calculated.items())
                .subtotalOriginal(calculated.subtotalOriginal())
                .taxAmountOriginal(calculated.taxAmountOriginal())
                .grandTotalOriginal(calculated.grandTotalOriginal())
                .grandTotalAccounting(calculated.grandTotalAccounting())
                .status(calculated.status())
                .preview(true)
                .build();
    }

    public InvoiceResponse toResponse(Invoice invoice, boolean preview) {
        List<InvoiceItemResultDto> items = invoice.getItems().stream()
                .map(this::toItemResult)
                .toList();

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerId(invoice.getCustomerId())
                .locationId(invoice.getLocationId())
                .issueDate(invoice.getIssueDate())
                .createdAt(invoice.getCreatedAt())
                .invoiceCurrency(invoice.getInvoiceCurrency())
                .accountingCurrency(invoice.getAccountingCurrency())
                .exchangeRateDate(invoice.getExchangeRateDate())
                .exchangeRateValue(invoice.getExchangeRateValue())
                .items(items)
                .subtotalOriginal(invoice.getSubtotalOriginal())
                .taxAmountOriginal(invoice.getTaxAmountOriginal())
                .grandTotalOriginal(invoice.getGrandTotalOriginal())
                .grandTotalAccounting(invoice.getGrandTotalAccounting())
                .status(invoice.getStatus())
                .preview(preview)
                .build();
    }

    public ExchangeDifferenceResponse toResponse(ExchangeDifferenceLog log) {
        return ExchangeDifferenceResponse.builder()
                .id(log.getId())
                .invoiceId(log.getInvoice().getId())
                .calculationDate(log.getCalculationDate())
                .originalPaidAmount(log.getOriginalPaidAmount())
                .rateAtPayment(log.getRateAtPayment())
                .exchangeDifferenceAmount(log.getExchangeDifferenceAmount())
                .actionTaken(log.getActionTaken())
                .build();
    }

    private InvoiceItem toItemEntity(InvoiceItemResultDto line, Invoice invoice) {
        InvoiceItem item = InvoiceItem.builder()
                .invoice(invoice)
                .itemDescription(line.itemDescription())
                .quantity(line.quantity())
                .unitPriceOriginal(line.unitPriceOriginal())
                .discountOriginal(line.discountOriginal())
                .taxRate(line.taxRate())
                .taxAmountOriginal(line.taxAmountOriginal())
                .lineTotalOriginal(line.lineTotalOriginal())
                .build();
        return item;
    }

    private InvoiceItemResultDto toItemResult(InvoiceItem item) {
        return InvoiceItemResultDto.builder()
                .itemDescription(item.getItemDescription())
                .quantity(item.getQuantity())
                .unitPriceOriginal(item.getUnitPriceOriginal())
                .discountOriginal(item.getDiscountOriginal())
                .taxRate(item.getTaxRate())
                .lineTotalOriginal(item.getLineTotalOriginal())
                .taxAmountOriginal(item.getTaxAmountOriginal())
                .build();
    }
}
