package com.wms.core.service;

import com.wms.core.entity.City;
import com.wms.core.entity.District;
import com.wms.core.entity.Neighborhood;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.CityRepository;
import com.wms.core.repository.DistrictRepository;
import com.wms.core.repository.NeighborhoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Adres hiyerarşisi çapraz doğrulama servisi.
 *
 * <p>API üzerinden gönderilen {@code countryId / cityId / districtId / neighborhoodId}
 * dördlüsünün gerçekten birbirine bağlı ve tutarlı olduğunu doğrular.
 * Manipülasyon girişimlerini (örn: A şehrine ait bir districtId ile B şehrini
 * birleştirmeye çalışmak) {@link BusinessException} fırlatarak engeller.</p>
 *
 * <p>Doğrulama zinciri (aşağıdan yukarı):
 * <pre>
 *   Neighborhood.districtId  == districtId  (parametre)
 *   District.cityId          == cityId      (parametre)
 *   City.countryId           == countryId   (parametre)
 * </pre>
 * Herhangi bir halka kırılırsa ya da kayıt pasifse işlem durur.</p>
 *
 * <p>Bu servis yalnızca doğrulama yapar; kayıt oluşturma/güncelleme
 * ilgili domain servisinin sorumluluğundadır.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressMasterValidationService {

    private final NeighborhoodRepository neighborhoodRepository;
    private final DistrictRepository     districtRepository;
    private final CityRepository         cityRepository;

    /**
     * Hiyerarşik adres tutarlılığını doğrular.
     *
     * <p>{@code neighborhoodId} null ise mahalle katmanı atlanır ve doğrulama
     * district → city → country zinciriyle devam eder.</p>
     *
     * @param countryId      zorunlu
     * @param cityId         zorunlu
     * @param districtId     zorunlu
     * @param neighborhoodId opsiyonel (null olabilir)
     * @throws BusinessException hiyerarşi kırılırsa veya herhangi bir kayıt pasifse
     */
    public void validateAddressHierarchy(Long countryId,
                                         Long cityId,
                                         Long districtId,
                                         Long neighborhoodId) {

        // 1. Neighborhood → District kontrolü (neighborhoodId verilmişse)
        if (neighborhoodId != null) {
            Neighborhood neighborhood = neighborhoodRepository.findById(neighborhoodId)
                    .orElseThrow(() -> new BusinessException(
                            "Mahalle bulunamadı: " + neighborhoodId,
                            HttpStatus.BAD_REQUEST,
                            "ADDRESS_NEIGHBORHOOD_NOT_FOUND"));

            if (!neighborhood.getDistrict().getId().equals(districtId)) {
                throw new BusinessException(
                        "Mahalle [%s], ilçe [%s] ile eşleşmiyor."
                                .formatted(neighborhoodId, districtId),
                        HttpStatus.BAD_REQUEST,
                        "ADDRESS_NEIGHBORHOOD_DISTRICT_MISMATCH");
            }
        }

        // 2. District → City kontrolü
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new BusinessException(
                        "İlçe bulunamadı: " + districtId,
                        HttpStatus.BAD_REQUEST,
                        "ADDRESS_DISTRICT_NOT_FOUND"));

        if (!district.getCity().getId().equals(cityId)) {
            throw new BusinessException(
                    "İlçe [%s], şehir [%s] ile eşleşmiyor."
                            .formatted(districtId, cityId),
                    HttpStatus.BAD_REQUEST,
                    "ADDRESS_DISTRICT_CITY_MISMATCH");
        }

        // 3. City → Country kontrolü
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new BusinessException(
                        "Şehir bulunamadı: " + cityId,
                        HttpStatus.BAD_REQUEST,
                        "ADDRESS_CITY_NOT_FOUND"));

        if (!city.getCountry().getId().equals(countryId)) {
            throw new BusinessException(
                    "Şehir [%s], ülke [%s] ile eşleşmiyor."
                            .formatted(cityId, countryId),
                    HttpStatus.BAD_REQUEST,
                    "ADDRESS_CITY_COUNTRY_MISMATCH");
        }

        /*
         * Pasiflik kontrolü (@SQLRestriction notu):
         * Entity'lerdeki @SQLRestriction("is_active = true") Hibernate'e tüm
         * sorgulara otomatik "AND is_active = true" filtresi ekletir.
         * Dolayısıyla yukarıdaki findById çağrıları pasif kaydı zaten bulamaz
         * ve "NOT_FOUND" exception fırlatır — ayrıca isActive kontrolü gerekmez.
         */
    }
}
