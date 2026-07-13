package com.wms.billing.service;

import com.wms.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final InvoiceRepository invoiceRepository;

    public String generate(Long locationId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        long count = invoiceRepository.countByLocationIdAndIssueDateBetween(locationId, start, end);

        int seq = (int) count + 1;
        String invoiceNumber;
        do {
            invoiceNumber = "INV-%d-%s-%03d".formatted(locationId, today.format(DATE_FMT), seq);
            seq++;
        } while (invoiceRepository.existsByInvoiceNumber(invoiceNumber));

        return invoiceNumber;
    }
}
