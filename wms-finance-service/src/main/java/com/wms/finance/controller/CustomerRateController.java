package com.wms.finance.controller;

import com.wms.finance.dto.CustomerExchangeRateDto;
import com.wms.finance.dto.CustomerExchangeRateRequest;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.service.CustomerRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/finance/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class CustomerRateController {

    private final CustomerRateService customerRateService;

    @PostMapping("/{customerId}/rates")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<CustomerExchangeRateDto> upsertCustomerRate(
            @PathVariable Long customerId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody CustomerExchangeRateRequest request) {

        log.info("[API] Müşteri kur girişi → customer={} source={} target={} date={} type={} userId={}",
                customerId, request.sourceCurrency(), request.targetCurrency(),
                request.rateDate(), request.rateType(), userId);

        CustomerExchangeRateDto response = customerRateService.upsertCustomerRate(customerId, request, userId);

        HttpStatus status = switch (response.action()) {
            case INSERT -> HttpStatus.CREATED;
            default     -> HttpStatus.OK;
        };

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{customerId}/rates")
    public ResponseEntity<CustomerExchangeRateDto> getCustomerRate(
            @PathVariable Long customerId,
            @RequestParam String sourceCurrency,
            @RequestParam String targetCurrency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rateDate,
            @RequestParam(defaultValue = "SELLING") String rateType) {

        log.info("[API] Müşteri kur sorgusu → customer={} source={} target={} date={} type={}",
                customerId, sourceCurrency, targetCurrency, rateDate, rateType);

        CustomerExchangeRateDto response = customerRateService.getCustomerRateQuery(
                customerId, sourceCurrency, targetCurrency, rateDate, RateType.valueOf(rateType));

        return ResponseEntity.ok(response);
    }
}
