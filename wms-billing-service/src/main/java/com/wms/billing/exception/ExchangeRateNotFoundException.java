package com.wms.billing.exception;

import com.wms.billing.domain.enums.RateType;

import java.time.LocalDate;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(String from, String to, LocalDate date, RateType type) {
        super("Kur bulunamadı: %s → %s, tarih=%s, tip=%s".formatted(from, to, date, type));
    }
}
