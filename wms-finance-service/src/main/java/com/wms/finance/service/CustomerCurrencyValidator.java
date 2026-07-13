package com.wms.finance.service;

import com.wms.finance.exception.InvalidCustomerCurrencyException;
import com.wms.finance.repository.CustomerPermittedCurrencyRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Müşteri bazlı para birimi izin kontrolü.
 *
 * <p>İzin politikası:
 * <ol>
 *   <li>Müşterinin {@code defaultCurrency}'si her zaman izinlidir (tablodan bağımsız).</li>
 *   <li>{@code invoicingCurrency} de otomatik olarak izinlidir.</li>
 *   <li>Bunların dışındaki para birimleri yalnızca {@code customer_permitted_currencies}
 *       tablosunda kayıt varsa kabul edilir.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerCurrencyValidator {

    private final FinanceCustomerRepository customerRepository;
    private final CustomerPermittedCurrencyRepository permittedCurrencyRepository;

    /**
     * Verilen {@code currencyId}'nin müşteri için izinli olup olmadığını doğrular.
     *
     * @throws InvalidCustomerCurrencyException para birimi izinli değilse
     */
    public void validateCurrencyPermission(Long customerId, Long currencyId) {
        var customer = customerRepository.findByIdWithCurrency(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: " + customerId));

        // Varsayılan para birimi her zaman izinlidir
        if (customer.getDefaultCurrency() != null
                && customer.getDefaultCurrency().getId().equals(currencyId)) {
            return;
        }

        // Fatura para birimi de izinlidir
        if (customer.getInvoicingCurrency() != null
                && customer.getInvoicingCurrency().getId().equals(currencyId)) {
            return;
        }

        // İzin tablosunda kontrol
        if (!permittedCurrencyRepository.existsByCustomerIdAndCurrencyId(customerId, currencyId)) {
            throw new InvalidCustomerCurrencyException(customerId, currencyId);
        }
    }
}
