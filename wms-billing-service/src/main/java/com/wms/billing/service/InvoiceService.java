package com.wms.billing.service;

import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.dto.CalculateInvoiceRequest;
import com.wms.billing.dto.CreateInvoiceRequest;
import com.wms.billing.dto.InvoiceDto;
import com.wms.billing.dto.InvoiceResponse;
import com.wms.billing.exception.InvoiceNotFoundException;
import com.wms.billing.mapper.InvoiceMapper;
import com.wms.billing.repository.InvoiceRepository;
import com.wms.billing.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceCalculationService calculationService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;

    @Transactional(readOnly = true)
    public InvoiceResponse calculatePreview(CalculateInvoiceRequest request) {
        Long locationId = TenantContextHolder.getLocationId();
        InvoiceDto calculated = calculate(request.customerId(), locationId,
                request.invoiceCurrency(), request.exchangeRateDate(), request.countryId(), request.items());
        return invoiceMapper.toPreviewResponse(calculated, request.customerId(), locationId);
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        Long locationId = TenantContextHolder.getLocationId();
        InvoiceDto calculated = calculate(request.customerId(), locationId,
                request.invoiceCurrency(), request.exchangeRateDate(), request.countryId(), request.items());

        String invoiceNumber = invoiceNumberGenerator.generate(locationId);
        Invoice invoice = invoiceMapper.toEntity(calculated, invoiceNumber, locationId);
        return invoiceMapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        return invoiceMapper.toResponse(requireInvoiceForTenant(id));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> list(Long customerId, InvoiceStatus status, Pageable pageable) {
        Long locationId = TenantContextHolder.getLocationId();
        Page<Invoice> page = resolveListQuery(locationId, customerId, status, pageable);
        return page.map(invoiceMapper::toResponse);
    }

    @Transactional
    public InvoiceResponse updateDraft(Long id, CreateInvoiceRequest request) {
        Invoice invoice = requireInvoiceForTenant(id);
        assertDraft(invoice, "güncellenemez");

        Long locationId = TenantContextHolder.getLocationId();
        InvoiceDto calculated = calculate(request.customerId(), locationId,
                request.invoiceCurrency(), request.exchangeRateDate(), request.countryId(), request.items());

        invoice.setCustomerId(request.customerId());
        invoiceMapper.applyCalculated(invoice, calculated);
        return invoiceMapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse approve(Long id) {
        Invoice invoice = requireInvoiceForTenant(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Yalnızca DRAFT durumundaki faturalar onaylanabilir. Mevcut durum: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.APPROVED);
        return invoiceMapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse cancel(Long id) {
        Invoice invoice = requireInvoiceForTenant(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Yalnızca DRAFT durumundaki faturalar iptal edilebilir. Mevcut durum: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return invoiceMapper.toResponse(invoiceRepository.save(invoice));
    }

    public Invoice requireInvoiceForTenant(Long id) {
        Invoice invoice = invoiceRepository.findByIdWithItems(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        Long locationId = TenantContextHolder.getLocationId();
        if (!invoice.getLocationId().equals(locationId)) {
            throw new InvoiceNotFoundException(id);
        }
        return invoice;
    }

    private InvoiceDto calculate(Long customerId, Long locationId, String invoiceCurrency,
                                 java.time.LocalDate exchangeRateDate, Long countryId,
                                 java.util.List<com.wms.billing.dto.InvoiceItemInputDto> items) {
        return calculationService.calculateInvoice(items, customerId, locationId,
                invoiceCurrency, exchangeRateDate, countryId);
    }

    private Page<Invoice> resolveListQuery(Long locationId, Long customerId,
                                           InvoiceStatus status, Pageable pageable) {
        if (customerId != null && status != null) {
            return invoiceRepository.findByLocationIdAndCustomerIdAndStatus(
                    locationId, customerId, status, pageable);
        }
        if (customerId != null) {
            return invoiceRepository.findByLocationIdAndCustomerId(locationId, customerId, pageable);
        }
        if (status != null) {
            return invoiceRepository.findByLocationIdAndStatus(locationId, status, pageable);
        }
        return invoiceRepository.findByLocationId(locationId, pageable);
    }

    private static void assertDraft(Invoice invoice, String action) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Yalnızca DRAFT durumundaki faturalar %s. Mevcut durum: %s"
                            .formatted(action, invoice.getStatus()));
        }
    }
}
