package com.wms.localization.repository;

import com.wms.localization.LocalizationPostgresTestBase;
import com.wms.localization.entity.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LanguageRepository} gerçek-PostgreSQL entegrasyon testi.
 *
 * <p>Test kendi verisini kurar (çakışmayı önlemek için gerçek ISO kodları değil
 * sözde kodlar kullanılır); aktiflik filtresi ve unique kod kısıtı üretim şemasına
 * karşı doğrulanır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("LanguageRepository (gerçek PostgreSQL)")
class LanguageRepositoryTest extends LocalizationPostgresTestBase {

    @Autowired
    private LanguageRepository repository;

    private static Language language(String code, boolean active) {
        return Language.builder()
                .code(code)
                .name("Test " + code)
                .isDefault(false)
                .isActive(active)
                .build();
    }

    @Test
    @DisplayName("findByCode eklenen kaydı bulur")
    void findByCode() {
        repository.save(language("zz-AA", true));

        assertThat(repository.findByCode("zz-AA")).isPresent();
        assertThat(repository.findByCode("zz-YOK")).isEmpty();
    }

    @Test
    @DisplayName("existsByCodeAndActive yalnız aktif kod için true döner")
    void existsByCodeAndActive() {
        repository.save(language("zz-AC", true));
        repository.save(language("zz-IN", false));

        assertThat(repository.existsByCodeAndActive("zz-AC")).isTrue();
        assertThat(repository.existsByCodeAndActive("zz-IN")).isFalse();
        assertThat(repository.existsByCodeAndActive("zz-XX")).isFalse();
    }
}
