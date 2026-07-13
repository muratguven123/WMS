package com.wms.localization.repository;

import com.wms.localization.entity.TranslationValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface TranslationValueRepository extends JpaRepository<TranslationValue, Long> {

    /**
     * Tek çeviri değeri — dil kodu + anahtar kodu ile.
     * Redis cache-miss sonrası çağrılır.
     */
    @Query("""
            SELECT tv FROM TranslationValue tv
            JOIN FETCH tv.language l
            JOIN FETCH tv.translationKey tk
            WHERE l.code = :langCode
              AND tk.keyCode = :keyCode
            """)
    Optional<TranslationValue> findByLanguageCodeAndKeyCode(
            @Param("langCode") String langCode,
            @Param("keyCode")  String keyCode);

    /**
     * Bir modülün tüm çevirilerini tek sorguda getir.
     * Uygulama başlangıcında veya cache warm-up sırasında kullanılır.
     * Dönen liste: keyCode → value eşleşmesi için servis katmanında Map'e dönüştürülür.
     */
    @Query("""
            SELECT tv FROM TranslationValue tv
            JOIN FETCH tv.language l
            JOIN FETCH tv.translationKey tk
            WHERE l.code = :langCode
              AND tk.module = :module
            ORDER BY tk.keyCode
            """)
    List<TranslationValue> findAllByLanguageCodeAndModule(
            @Param("langCode") String langCode,
            @Param("module")   String module);

    /**
     * Dil kodu ve birden fazla keyCode ile toplu sorgulama.
     * Batch cache-miss resolve için kullanılır.
     */
    @Query("""
            SELECT tv FROM TranslationValue tv
            JOIN FETCH tv.language l
            JOIN FETCH tv.translationKey tk
            WHERE l.code = :langCode
              AND tk.keyCode IN :keyCodes
            """)
    List<TranslationValue> findAllByLanguageCodeAndKeyCodeIn(
            @Param("langCode")  String langCode,
            @Param("keyCodes")  List<String> keyCodes);

    /**
     * Bir anahtarın tüm dillerdeki çevirilerini döner.
     * Çeviri yönetim ekranı (admin) için kullanılır.
     */
    @Query("""
            SELECT tv FROM TranslationValue tv
            JOIN FETCH tv.language l
            JOIN FETCH tv.translationKey tk
            WHERE tk.keyCode = :keyCode
            ORDER BY l.code
            """)
    List<TranslationValue> findAllByKeyCode(@Param("keyCode") String keyCode);

    /** Belirli (language, key) çifti mevcut mu? — upsert öncesi kontrol. */
    @Query("""
            SELECT COUNT(tv) > 0 FROM TranslationValue tv
            WHERE tv.language.id = :languageId
              AND tv.translationKey.id = :translationKeyId
            """)
    boolean existsByLanguageIdAndTranslationKeyId(
            @Param("languageId") Long languageId,
            @Param("translationKeyId") Long translationKeyId);

    /**
     * Dil koduna göre tüm çeviri değerlerini getir (export için).
     * TranslationKey JOIN FETCH ile N+1 önlenir.
     */
    @Query("""
            SELECT tv FROM TranslationValue tv
            JOIN FETCH tv.language l
            JOIN FETCH tv.translationKey tk
            WHERE l.code = :langCode
            ORDER BY tk.module, tk.keyCode
            """)
    List<TranslationValue> findAllTranslationValuesByLanguageCode(
            @Param("langCode") String langCode);

    @Query("""
            SELECT COUNT(tk) FROM TranslationKey tk
            WHERE tk.module = :module
              AND NOT EXISTS (
                SELECT tv FROM TranslationValue tv
                JOIN tv.language l
                WHERE tv.translationKey = tk
                  AND l.code = :langCode
                  AND tv.value IS NOT NULL
                  AND TRIM(tv.value) <> ''
              )
            """)
    long countMissingTranslationsByLanguageAndModule(
            @Param("langCode") String langCode,
            @Param("module") String module);

    @Query("""
            SELECT COUNT(tv) FROM TranslationValue tv
            JOIN tv.language l
            JOIN tv.translationKey tk
            WHERE l.code = :langCode
              AND tk.module = :module
            """)
    long countByLanguageCodeAndModule(
            @Param("langCode") String langCode,
            @Param("module") String module);

    /** Modüldeki tüm dolu çeviri değerleri — çok dilli kaynak çözümleme için. */
    @Query("""
            SELECT tv FROM TranslationValue tv
            JOIN FETCH tv.language l
            JOIN FETCH tv.translationKey tk
            WHERE tk.module = :module
              AND tv.value IS NOT NULL
              AND TRIM(tv.value) <> ''
            ORDER BY l.code, tk.keyCode
            """)
    List<TranslationValue> findAllNonBlankByModule(@Param("module") String module);

    /**
     * Belirli bir dile ait tüm Translation Value'ları sil.
     * Dil kaldırma işleminde kullanılır.
     */
    @Query("DELETE FROM TranslationValue tv WHERE tv.language.id = :languageId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteAllByLanguageId(@Param("languageId") Long languageId);
}
