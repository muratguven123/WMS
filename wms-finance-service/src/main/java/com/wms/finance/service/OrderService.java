package com.wms.finance.service;

import com.wms.finance.dto.OrderRequestDto;
import com.wms.finance.dto.OrderResponseDto;
import com.wms.finance.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final CustomerCurrencyValidator currencyValidator;
    private final CurrencyRepository currencyRepository;

    /**
     * Yeni sipariş oluşturur.
     *
     * <p>Para birimi doğrulaması ilk adım olarak çalışır; izinsiz para birimi
     * geldiğinde {@code InvalidCustomerCurrencyException} fırlatılır ve
     * sipariş veritabanına yazılmaz.
     */
    public OrderResponseDto createOrder(OrderRequestDto request) {
        // 1. Para birimi izin kontrolü — yetkisizse buradan exception fırlar
        currencyValidator.validateCurrencyPermission(request.customerId(), request.currencyId());

        // 2. Para birimi detayını getir
        var currency = currencyRepository.findById(request.currencyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Currency not found: " + request.currencyId()));

        // 3. Sözleşme varsa para birimi kilidi burada uygulanabilir (Prompt 9.3)

        // 4. Sipariş kaydını oluştur (domain entity henüz tanımsız; Long simüle edildi)
        Long orderId = 1L;

        return OrderResponseDto.builder()
                .orderId(orderId)
                .customerId(request.customerId())
                .currencyId(currency.getId())
                .currencyCode(currency.getCode())
                .amount(request.amount())
                .orderDate(request.orderDate())
                .build();
    }
}
