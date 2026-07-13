package com.wms.finance.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.service.CurrencyConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class CurrencyRateLookupController {

    private final CurrencyConversionService currencyConversionService;

    @GetMapping("/lookup")
    public ExchangeRateDto lookupRate(
            @RequestParam String fromCurrency,
            @RequestParam String toCurrency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rateDate,
            @RequestParam(defaultValue = "SELLING") String rateType) {

        return currencyConversionService.lookupRate(fromCurrency, toCurrency, rateDate, rateType);
    }
}
