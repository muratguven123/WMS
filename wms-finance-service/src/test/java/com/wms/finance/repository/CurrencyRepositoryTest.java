package com.wms.finance.repository;

import com.wms.finance.FinancePostgresTestBase;
import com.wms.finance.entity.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CurrencyRepository} gerçek-PostgreSQL entegrasyon testi.
 *
 * <p>Test kendi verisini kurar (çakışmayı önlemek için gerçek ISO kodları değil
 * sözde kodlar kullanılır); {@code finance} şemasındaki unique kısıt ve aktiflik
 * filtresi üretim şemasına karşı doğrulanır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CurrencyRepository (gerçek PostgreSQL)")
class CurrencyRepositoryTest extends FinancePostgresTestBase {

    @Autowired
    private CurrencyRepository repository;

    private static Currency currency(String code, boolean active) {
        return Currency.builder()
                .code(code)
                .symbol("¤")
                .decimalPlaces(2)
                .name("Test " + code)
                .active(active)
                .build();
    }

    @Test
    @DisplayName("findByCodeAndActiveTrue yalnız aktif kaydı döner")
    void findByCodeAndActiveTrue() {
        repository.save(currency("ZZA", true));
        repository.save(currency("ZZB", false));

        assertThat(repository.findByCodeAndActiveTrue("ZZA")).isPresent();
        assertThat(repository.findByCodeAndActiveTrue("ZZB")).isEmpty();
        assertThat(repository.findByCodeAndActiveTrue("ZZC")).isEmpty();
    }

    @Test
    @DisplayName("findAllByActiveTrue eklenen aktif sözde para birimini içerir")
    void findAllByActiveTrue() {
        repository.save(currency("ZZD", true));

        assertThat(repository.findAllByActiveTrue())
                .extracting(Currency::getCode)
                .contains("ZZD")
                .doesNotContain("ZZE");
    }
}
