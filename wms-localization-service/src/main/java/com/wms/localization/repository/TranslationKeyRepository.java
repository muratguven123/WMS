package com.wms.localization.repository;

import com.wms.localization.entity.TranslationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TranslationKeyRepository extends JpaRepository<TranslationKey, Long> {

    /** Tek anahtar kodu ile bul. */
    Optional<TranslationKey> findByKeyCode(String keyCode);

    /** Modüle göre tüm anahtarları getir — toplu yükleme için. */
    List<TranslationKey> findAllByModule(String module);

    long countByModule(String module);

    /**
     * Birden fazla keyCode'u tek sorguda çek.
     * Cache-miss sonrası batch resolve için kullanılır.
     */
    @Query("SELECT tk FROM TranslationKey tk WHERE tk.keyCode IN :keyCodes")
    List<TranslationKey> findAllByKeyCodeIn(@Param("keyCodes") Set<String> keyCodes);

    /** Verilen prefix ile başlayan tüm anahtarlar — namespace bazlı yükleme. */
    @Query("SELECT tk FROM TranslationKey tk WHERE tk.keyCode LIKE :prefix% ORDER BY tk.keyCode")
    List<TranslationKey> findAllByKeyCodeStartingWith(@Param("prefix") String prefix);

    boolean existsByKeyCode(String keyCode);
}
