package com.wms.localization.repository;

import com.wms.localization.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LanguageRepository extends JpaRepository<Language, Long> {

    /** ISO kodu ile dil bul (cache-miss fallback). */
    Optional<Language> findByCode(String code);

    /** Sistemde aktif olan tüm dilleri döner — UI dil seçici için. */
    List<Language> findAllByIsActiveTrue();

    /** Varsayılan dili döner. Birden fazla varsa ilkini alır (veri bütünlüğü serviste sağlanır). */
    Optional<Language> findFirstByIsDefaultTrueAndIsActiveTrue();

    /** Verilen kod aktif mi? — kısa devre sorgusu, entity yükleme yapmaz. */
    @Query("SELECT COUNT(l) > 0 FROM Language l WHERE l.code = :code AND l.isActive = true")
    boolean existsByCodeAndActive(String code);
}
