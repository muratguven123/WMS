package com.wms.core.service;

import com.wms.core.dto.address.*;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cascade dropdown API'si için okuma servisi.
 *
 * <p>Tüm metodlar sadece {@code isActive = true} kayıtları döner —
 * bu filtre JPA entity'lerindeki {@code @SQLRestriction("is_active = true")}
 * anotasyonu ile otomatik olarak uygulanır.</p>
 *
 * <p>Yazma işlemleri (CRUD) admin servisi tarafından yönetilir; bu sınıf
 * yalnızca dropdown sorgularını barındırır (SRP).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressQueryService {

    private final CountryRepository       countryRepository;
    private final StateProvinceRepository stateProvinceRepository;
    private final CityRepository          cityRepository;
    private final DistrictRepository      districtRepository;
    private final NeighborhoodRepository  neighborhoodRepository;

    // ------------------------------------------------------------------
    // 1. Ülkeler
    // ------------------------------------------------------------------

    /**
     * Tüm aktif ülkeleri döner.
     * {@code @SQLRestriction} sayesinde ek filtre gerekmez.
     */
    public List<CountryDto> listCountries() {
        return countryRepository.findAll()
                .stream()
                .sorted(java.util.Comparator.comparing(c -> c.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(c -> new CountryDto(c.getId(), c.getIsoCode(), c.getName()))
                .toList();
    }

    // ------------------------------------------------------------------
    // 2. Eyaletler / İller
    // ------------------------------------------------------------------

    /**
     * Verilen ülkeye bağlı aktif eyaletleri döner.
     */
    public List<StateProvinceDto> listStates(Long countryId) {
        validateCountryExists(countryId);
        return stateProvinceRepository.findByCountryIdOrderByNameAsc(countryId)
                .stream()
                .map(s -> new StateProvinceDto(s.getId(), s.getName(), s.getCode()))
                .toList();
    }

    // ------------------------------------------------------------------
    // 3. Şehirler
    // ------------------------------------------------------------------

    /**
     * Eyalet varsa eyalete, yoksa doğrudan ülkeye bağlı aktif şehirleri döner.
     *
     * @param countryId zorunlu
     * @param stateId   opsiyonel — null ise ülke bazında sorgu yapılır
     */
    public List<CityDto> listCities(Long countryId, Long stateId) {
        validateCountryExists(countryId);

        List<CityDto> result;
        if (stateId != null) {
            validateStateBelongsToCountry(stateId, countryId);
            result = cityRepository.findByStateProvinceIdOrderByNameAsc(stateId)
                    .stream()
                    .map(c -> new CityDto(c.getId(), c.getName()))
                    .toList();
        } else {
            // Eyaletsiz ülkeler: yalnızca doğrudan ülkeye bağlı şehirler
            result = cityRepository.findByCountryIdAndStateProvinceIsNullOrderByNameAsc(countryId)
                    .stream()
                    .map(c -> new CityDto(c.getId(), c.getName()))
                    .toList();
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 4. İlçeler
    // ------------------------------------------------------------------

    /**
     * Verilen şehre bağlı aktif ilçeleri döner.
     */
    public List<DistrictDto> listDistricts(Long cityId) {
        validateCityExists(cityId);
        return districtRepository.findByCityIdOrderByNameAsc(cityId)
                .stream()
                .map(d -> new DistrictDto(d.getId(), d.getName()))
                .toList();
    }

    // ------------------------------------------------------------------
    // 5. Mahalleler
    // ------------------------------------------------------------------

    /**
     * Verilen ilçeye bağlı aktif mahalleleri posta kodlarıyla döner.
     * zipCode UI'da Posta Kodu alanını otomatik doldurmak için kullanılır
     * (İş Kuralı 4); kullanıcı gerekirse override edebilir.
     */
    public List<NeighborhoodDto> listNeighborhoods(Long districtId) {
        validateDistrictExists(districtId);
        return neighborhoodRepository.findByDistrictIdOrderByNameAsc(districtId)
                .stream()
                .map(n -> new NeighborhoodDto(n.getId(), n.getName(), n.getZipCode()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Guard helpers
    // ------------------------------------------------------------------

    private void validateCountryExists(Long countryId) {
        if (!countryRepository.existsById(countryId)) {
            throw new BusinessException(
                    "Ülke bulunamadı: " + countryId, HttpStatus.NOT_FOUND, "COUNTRY_NOT_FOUND");
        }
    }

    private void validateStateBelongsToCountry(Long stateId, Long countryId) {
        if (!stateProvinceRepository.existsByIdAndCountryId(stateId, countryId)) {
            throw new BusinessException(
                    "Eyalet/İl [%s], ülke [%s] ile eşleşmiyor veya bulunamadı."
                            .formatted(stateId, countryId),
                    HttpStatus.BAD_REQUEST,
                    "STATE_COUNTRY_MISMATCH");
        }
    }

    private void validateCityExists(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new BusinessException(
                    "Şehir bulunamadı: " + cityId, HttpStatus.NOT_FOUND, "CITY_NOT_FOUND");
        }
    }

    private void validateDistrictExists(Long districtId) {
        if (!districtRepository.existsById(districtId)) {
            throw new BusinessException(
                    "İlçe bulunamadı: " + districtId, HttpStatus.NOT_FOUND, "DISTRICT_NOT_FOUND");
        }
    }
}
