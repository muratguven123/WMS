package com.wms.core.service;

import com.wms.core.dto.address.CityDto;
import com.wms.core.dto.address.DistrictDto;
import com.wms.core.dto.address.NeighborhoodDto;
import com.wms.core.dto.address.StateProvinceDto;
import com.wms.core.dto.geo.*;
import com.wms.core.entity.*;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.integration.LocalizationUsageClient;
import com.wms.core.repository.*;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * İş İsteri 18 — Ülke ve idari birim (eyalet/şehir/ilçe/mahalle) admin CRUD servisi.
 *
 * <h3>Tasarım kararları</h3>
 * <ul>
 *   <li><b>Soft delete:</b> Pasifleştirme entity'lerdeki {@code @SQLDelete} ile
 *       yapılır; mevcut adres kayıtları asla yetim kalmaz (Teknik İş Kuralı 2).
 *       Ülke pasifleştirildiğinde alt birimler dokunulmadan bırakılır — read
 *       endpoint'leri ülke üzerinden filtrelendiği için erişilmez olurlar ve
 *       reaktivasyonda aynen geri gelirler.</li>
 *   <li><b>ISO değişmezliği:</b> {@code iso_code} oluşturulduktan sonra
 *       güncellenemez; localization JSONB'lerinde {@code countryIso} olarak
 *       saklanır (Teknik İş Kuralı 1).</li>
 *   <li><b>Eyalet katmanı opsiyonel:</b> Şehir eyaletli veya eyaletsiz
 *       bağlanabilir; {@code assignCityToState} TR-eyalet geçiş senaryosunda
 *       kademeli taşıma sağlar (Teknik İş Kuralı 4).</li>
 *   <li><b>Audit:</b> Tüm yazma işlemleri {@link ConfigChangeEvent} ile
 *       asenkron audit log'a yazılır (İş İsteri 5 altyapısı).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GeoAdminService {

    private static final String ISO_PATTERN = "^[A-Z]{2,3}$";

    private final CountryRepository       countryRepository;
    private final StateProvinceRepository stateProvinceRepository;
    private final CityRepository          cityRepository;
    private final DistrictRepository      districtRepository;
    private final NeighborhoodRepository  neighborhoodRepository;
    private final LocalizationUsageClient localizationUsageClient;
    private final ApplicationEventPublisher eventPublisher;

    // ══════════════════════════════════════════════════════════════════
    // Ülke
    // ══════════════════════════════════════════════════════════════════

    /** Admin listesi: pasif kayıtlar dahil tüm ülkeler + aktif birim sayaçları. */
    @Transactional(readOnly = true)
    public List<CountryAdminDto> listCountries() {
        return countryRepository.findAllIncludingInactive().stream()
                .map(c -> new CountryAdminDto(
                        c.getId(), c.getIsoCode(), c.getName(), c.isActive(),
                        stateProvinceRepository.countByCountryId(c.getId()),
                        cityRepository.countByCountryId(c.getId())))
                .toList();
    }

    /**
     * Yeni ülke oluşturur.
     *
     * <p>iso_code uppercase'e normalize edilir; tekillik pasif kayıtlar dahil
     * kontrol edilir — pasif bir kayıt varsa reaktivasyon önerilir
     * (DB kısıtı uk_country_iso_code pasifleri de kapsar).</p>
     */
    public CountryAdminDto createCountry(CreateCountryRequest request) {
        String iso = request.isoCode().trim().toUpperCase(Locale.ROOT);
        if (!iso.matches(ISO_PATTERN)) {
            throw new BusinessException("isoCode ISO 3166-1 formatında olmalıdır: " + iso,
                    HttpStatus.BAD_REQUEST, "GEO_ISO_INVALID");
        }
        if (countryRepository.existsByIsoCodeIncludingInactive(iso)) {
            throw new BusinessException(
                    "Bu ISO kodu ile bir ülke zaten mevcut (pasif olabilir): " + iso,
                    HttpStatus.CONFLICT, "GEO_ISO_EXISTS");
        }

        Country country = Country.builder()
                .isoCode(iso)
                .name(request.name().trim())
                .build();
        countryRepository.save(country);

        log.info("[GeoAdmin] Ülke oluşturuldu. id={} iso={} name={}",
                country.getId(), iso, country.getName());

        audit("Country", country.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("isoCode", null, iso),
                new ConfigChangeEvent.FieldChange("name", null, country.getName())));

        return toAdminDto(country);
    }

    /** Ülke adını günceller (iso_code değişmez — Teknik İş Kuralı 1). */
    public CountryAdminDto updateCountry(Long countryId, UpdateCountryRequest request) {
        Country country = findCountry(countryId);
        String newName = request.name().trim();

        if (!newName.equals(country.getName())) {
            audit("Country", countryId, "UPDATE", List.of(
                    new ConfigChangeEvent.FieldChange("name", country.getName(), newName)));
            country.setName(newName);
            countryRepository.save(country);
            log.info("[GeoAdmin] Ülke güncellendi. id={} name={}", countryId, newName);
        }
        return toAdminDto(country);
    }

    /**
     * Ülkeyi pasifleştirir (soft delete).
     *
     * <p>Cross-service kullanım onayı UI sorumluluğundadır — UI önce
     * {@link #getCountryUsage(Long)} ile kullanıcıya onay gösterir.
     * Mevcut adres kayıtları korunur; ülke yalnızca yeni seçimlerde gizlenir.</p>
     */
    public void deactivateCountry(Long countryId) {
        Country country = findCountry(countryId);
        countryRepository.delete(country); // @SQLDelete → is_active = false

        log.info("[GeoAdmin] Ülke pasifleştirildi. id={} iso={}", countryId, country.getIsoCode());
        audit("Country", countryId, "DELETE", List.of(
                new ConfigChangeEvent.FieldChange("isActive", "true", "false")));
    }

    /** Pasif ülkeyi yeniden aktifleştirir. */
    public CountryAdminDto reactivateCountry(Long countryId) {
        Country country = countryRepository.findByIdIncludingInactive(countryId)
                .orElseThrow(() -> countryNotFound(countryId));
        if (country.isActive()) {
            throw new BusinessException("Ülke zaten aktif: " + countryId,
                    HttpStatus.CONFLICT, "GEO_COUNTRY_ALREADY_ACTIVE");
        }
        countryRepository.reactivate(countryId);

        log.info("[GeoAdmin] Ülke aktifleştirildi. id={} iso={}", countryId, country.getIsoCode());
        audit("Country", countryId, "UPDATE", List.of(
                new ConfigChangeEvent.FieldChange("isActive", "false", "true")));

        country.setActive(true);
        return toAdminDto(country);
    }

    /**
     * Ülkenin localization-service'teki kullanım özeti (best-effort).
     * Pasifleştirme onay diyaloğu bu veriyle beslenir (Teknik İş Kuralı 2).
     */
    @Transactional(readOnly = true)
    public CountryUsageDto getCountryUsage(Long countryId) {
        findCountryIncludingInactive(countryId);
        return localizationUsageClient.fetchUsage(countryId)
                .map(u -> new CountryUsageDto(countryId, u.addressCount(), u.templateExists(), true))
                .orElseGet(() -> new CountryUsageDto(countryId, null, null, false));
    }

    // ══════════════════════════════════════════════════════════════════
    // Eyalet / İl
    // ══════════════════════════════════════════════════════════════════

    /** Ülkeye eyalet ekler. Tekillik: ülke içinde name ve code. */
    public StateProvinceDto addState(Long countryId, UpsertStateRequest request) {
        Country country = findCountry(countryId);
        String name = request.name().trim();
        String code = normalizeCode(request.code());

        validateStateUniqueness(countryId, name, code, null);

        StateProvince state = StateProvince.builder()
                .country(country)
                .name(name)
                .code(code)
                .build();
        stateProvinceRepository.save(state);

        log.info("[GeoAdmin] Eyalet eklendi. id={} countryId={} name={}", state.getId(), countryId, name);
        audit("StateProvince", state.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("countryId", null, String.valueOf(countryId)),
                new ConfigChangeEvent.FieldChange("name", null, name),
                new ConfigChangeEvent.FieldChange("code", null, code)));

        return new StateProvinceDto(state.getId(), state.getName(), state.getCode());
    }

    /** Eyalet adı/kodu günceller. */
    public StateProvinceDto updateState(Long stateId, UpsertStateRequest request) {
        StateProvince state = findState(stateId);
        Long countryId = state.getCountry().getId();
        String newName = request.name().trim();
        String newCode = normalizeCode(request.code());

        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();
        if (!newName.equals(state.getName())) {
            changes.add(new ConfigChangeEvent.FieldChange("name", state.getName(), newName));
        }
        if (!Objects.equals(newCode, state.getCode())) {
            changes.add(new ConfigChangeEvent.FieldChange("code", state.getCode(), newCode));
        }
        if (!changes.isEmpty()) {
            validateStateUniqueness(countryId, newName, newCode, stateId);
            state.setName(newName);
            state.setCode(newCode);
            stateProvinceRepository.save(state);
            audit("StateProvince", stateId, "UPDATE", changes);
            log.info("[GeoAdmin] Eyalet güncellendi. id={} name={}", stateId, newName);
        }
        return new StateProvinceDto(state.getId(), state.getName(), state.getCode());
    }

    /**
     * Eyaleti pasifleştirir.
     * Aktif şehri olan eyalet pasifleştirilemez — önce şehirler taşınmalıdır
     * (aksi halde cascade dropdown'da erişilmez şehirler oluşur).
     */
    public void deactivateState(Long stateId) {
        StateProvince state = findState(stateId);
        if (cityRepository.existsByStateProvinceId(stateId)) {
            throw new BusinessException(
                    "Eyalete bağlı aktif şehirler var; önce şehirleri başka eyalete taşıyın veya pasifleştirin.",
                    HttpStatus.CONFLICT, "GEO_STATE_HAS_CITIES");
        }
        stateProvinceRepository.delete(state);

        log.info("[GeoAdmin] Eyalet pasifleştirildi. id={}", stateId);
        audit("StateProvince", stateId, "DELETE", List.of(
                new ConfigChangeEvent.FieldChange("isActive", "true", "false")));
    }

    /**
     * CSV toplu eyalet import'u (Teknik İş Kuralı 5).
     *
     * <p>Format: her satır {@code name} veya {@code name,code}. İlk satır
     * başlıksa ("name" ile başlıyorsa) atlanır. Mükerrer satırlar (dosya içi
     * veya DB'de mevcut) atlanır ve satır numarasıyla rapor edilir.</p>
     */
    public StateImportResultDto importStates(Long countryId, String csvContent) {
        Country country = findCountry(countryId);
        if (csvContent == null || csvContent.isBlank()) {
            throw new BusinessException("CSV içeriği boş", HttpStatus.BAD_REQUEST, "GEO_CSV_EMPTY");
        }

        String[] lines = csvContent.split("\\r?\\n");
        List<String> errors = new ArrayList<>();
        List<StateProvince> toSave = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        int skipped = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNo = i + 1;
            if (line.isEmpty()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).startsWith("name")) continue; // başlık satırı

            String[] parts = line.split(",", -1);
            String name = parts[0].trim();
            String code = parts.length > 1 ? normalizeCode(parts[1]) : null;

            if (name.isEmpty() || name.length() > 150) {
                errors.add("Satır " + lineNo + ": geçersiz name");
                skipped++;
                continue;
            }
            if (code != null && code.length() > 10) {
                errors.add("Satır " + lineNo + ": code en fazla 10 karakter olabilir");
                skipped++;
                continue;
            }
            String nameKey = name.toLowerCase(Locale.ROOT);
            if (!seenNames.add(nameKey) || (code != null && !seenCodes.add(code))) {
                errors.add("Satır " + lineNo + ": dosya içinde mükerrer (" + name + ")");
                skipped++;
                continue;
            }
            if (stateProvinceRepository.existsByCountryIdAndName(countryId, name)
                    || (code != null && stateProvinceRepository.findByCountryIdAndCode(countryId, code).isPresent())) {
                errors.add("Satır " + lineNo + ": zaten kayıtlı (" + name + ")");
                skipped++;
                continue;
            }
            toSave.add(StateProvince.builder().country(country).name(name).code(code).build());
        }

        stateProvinceRepository.saveAll(toSave);

        log.info("[GeoAdmin] Eyalet import tamamlandı. countryId={} imported={} skipped={}",
                countryId, toSave.size(), skipped);
        audit("StateProvince", countryId, "IMPORT", List.of(
                new ConfigChangeEvent.FieldChange("importedCount", null, String.valueOf(toSave.size())),
                new ConfigChangeEvent.FieldChange("skippedCount", null, String.valueOf(skipped))));

        return new StateImportResultDto(toSave.size(), skipped, errors);
    }

    // ══════════════════════════════════════════════════════════════════
    // Şehir
    // ══════════════════════════════════════════════════════════════════

    /**
     * Şehir ekler — {@code stateProvinceId} verilirse eyalete, verilmezse
     * doğrudan ülkeye bağlanır (eyalet katmanı opsiyonel).
     */
    public CityDto addCity(Long countryId, CreateCityRequest request) {
        Country country = findCountry(countryId);
        String name = request.name().trim();

        StateProvince state = null;
        if (request.stateProvinceId() != null) {
            state = findState(request.stateProvinceId());
            if (!state.getCountry().getId().equals(countryId)) {
                throw new BusinessException(
                        "Eyalet bu ülkeye ait değil: stateId=" + request.stateProvinceId(),
                        HttpStatus.BAD_REQUEST, "GEO_STATE_COUNTRY_MISMATCH");
            }
            if (cityRepository.existsByStateProvinceIdAndName(state.getId(), name)) {
                throw new BusinessException("Bu eyalette aynı adlı şehir zaten var: " + name,
                        HttpStatus.CONFLICT, "GEO_CITY_NAME_EXISTS");
            }
        } else if (cityRepository.existsByCountryIdAndStateProvinceIsNullAndName(countryId, name)) {
            throw new BusinessException("Bu ülkede aynı adlı şehir zaten var: " + name,
                    HttpStatus.CONFLICT, "GEO_CITY_NAME_EXISTS");
        }

        City city = City.builder()
                .country(country)
                .stateProvince(state)
                .name(name)
                .build();
        cityRepository.save(city);

        log.info("[GeoAdmin] Şehir eklendi. id={} countryId={} stateId={} name={}",
                city.getId(), countryId, request.stateProvinceId(), name);
        audit("City", city.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("countryId", null, String.valueOf(countryId)),
                new ConfigChangeEvent.FieldChange("stateProvinceId", null,
                        state == null ? null : String.valueOf(state.getId())),
                new ConfigChangeEvent.FieldChange("name", null, name)));

        return new CityDto(city.getId(), city.getName());
    }

    /**
     * Mevcut şehri eyalete bağlar (TR eyalet geçiş senaryosu — Teknik İş Kuralı 4).
     * Eyalet, şehrin ülkesine ait olmalıdır.
     */
    public CityDto assignCityToState(Long cityId, AssignStateRequest request) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new BusinessException("Şehir bulunamadı: " + cityId,
                        HttpStatus.NOT_FOUND, "GEO_CITY_NOT_FOUND"));
        StateProvince state = findState(request.stateProvinceId());

        if (!state.getCountry().getId().equals(city.getCountry().getId())) {
            throw new BusinessException("Eyalet, şehrin ülkesine ait değil.",
                    HttpStatus.BAD_REQUEST, "GEO_STATE_COUNTRY_MISMATCH");
        }
        if (cityRepository.existsByStateProvinceIdAndName(state.getId(), city.getName())) {
            throw new BusinessException("Hedef eyalette aynı adlı şehir zaten var: " + city.getName(),
                    HttpStatus.CONFLICT, "GEO_CITY_NAME_EXISTS");
        }

        String oldStateId = city.getStateProvince() == null
                ? null : String.valueOf(city.getStateProvince().getId());
        city.setStateProvince(state);
        cityRepository.save(city);

        log.info("[GeoAdmin] Şehir eyalete bağlandı. cityId={} stateId={}", cityId, state.getId());
        audit("City", cityId, "UPDATE", List.of(
                new ConfigChangeEvent.FieldChange("stateProvinceId", oldStateId,
                        String.valueOf(state.getId()))));

        return new CityDto(city.getId(), city.getName());
    }

    // ══════════════════════════════════════════════════════════════════
    // İlçe / Mahalle
    // ══════════════════════════════════════════════════════════════════

    /** Şehre ilçe ekler. Tekillik: şehir içinde name. */
    public DistrictDto addDistrict(Long cityId, CreateDistrictRequest request) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new BusinessException("Şehir bulunamadı: " + cityId,
                        HttpStatus.NOT_FOUND, "GEO_CITY_NOT_FOUND"));
        String name = request.name().trim();

        if (districtRepository.existsByCityIdAndName(cityId, name)) {
            throw new BusinessException("Bu şehirde aynı adlı ilçe zaten var: " + name,
                    HttpStatus.CONFLICT, "GEO_DISTRICT_NAME_EXISTS");
        }

        District district = District.builder().city(city).name(name).build();
        districtRepository.save(district);

        log.info("[GeoAdmin] İlçe eklendi. id={} cityId={} name={}", district.getId(), cityId, name);
        audit("District", district.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("cityId", null, String.valueOf(cityId)),
                new ConfigChangeEvent.FieldChange("name", null, name)));

        return new DistrictDto(district.getId(), district.getName());
    }

    /** İlçeye mahalle ekler. Tekillik: ilçe içinde name. */
    public NeighborhoodDto addNeighborhood(Long districtId, CreateNeighborhoodRequest request) {
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new BusinessException("İlçe bulunamadı: " + districtId,
                        HttpStatus.NOT_FOUND, "GEO_DISTRICT_NOT_FOUND"));
        String name = request.name().trim();

        if (neighborhoodRepository.existsByDistrictIdAndName(districtId, name)) {
            throw new BusinessException("Bu ilçede aynı adlı mahalle zaten var: " + name,
                    HttpStatus.CONFLICT, "GEO_NEIGHBORHOOD_NAME_EXISTS");
        }

        Neighborhood neighborhood = Neighborhood.builder()
                .district(district)
                .name(name)
                .zipCode(trimToNull(request.zipCode()))
                .build();
        neighborhoodRepository.save(neighborhood);

        log.info("[GeoAdmin] Mahalle eklendi. id={} districtId={} name={}",
                neighborhood.getId(), districtId, name);
        audit("Neighborhood", neighborhood.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("districtId", null, String.valueOf(districtId)),
                new ConfigChangeEvent.FieldChange("name", null, name),
                new ConfigChangeEvent.FieldChange("zipCode", null, neighborhood.getZipCode())));

        return new NeighborhoodDto(neighborhood.getId(), neighborhood.getName(), neighborhood.getZipCode());
    }

    // ══════════════════════════════════════════════════════════════════
    // Yardımcılar
    // ══════════════════════════════════════════════════════════════════

    private Country findCountry(Long countryId) {
        return countryRepository.findById(countryId)
                .orElseThrow(() -> countryNotFound(countryId));
    }

    private Country findCountryIncludingInactive(Long countryId) {
        return countryRepository.findByIdIncludingInactive(countryId)
                .orElseThrow(() -> countryNotFound(countryId));
    }

    private StateProvince findState(Long stateId) {
        return stateProvinceRepository.findById(stateId)
                .orElseThrow(() -> new BusinessException("Eyalet bulunamadı: " + stateId,
                        HttpStatus.NOT_FOUND, "GEO_STATE_NOT_FOUND"));
    }

    private BusinessException countryNotFound(Long countryId) {
        return new BusinessException("Ülke bulunamadı: " + countryId,
                HttpStatus.NOT_FOUND, "GEO_COUNTRY_NOT_FOUND");
    }

    private void validateStateUniqueness(Long countryId, String name, String code, Long excludeId) {
        if (code != null) {
            stateProvinceRepository.findByCountryIdAndCode(countryId, code)
                    .filter(existing -> !existing.getId().equals(excludeId))
                    .ifPresent(existing -> {
                        throw new BusinessException("Bu ülkede aynı kodlu eyalet zaten var: " + code,
                                HttpStatus.CONFLICT, "GEO_STATE_CODE_EXISTS");
                    });
        }
        boolean nameTaken = stateProvinceRepository.findByCountryIdOrderByNameAsc(countryId).stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(name) && !s.getId().equals(excludeId));
        if (nameTaken) {
            throw new BusinessException("Bu ülkede aynı adlı eyalet zaten var: " + name,
                    HttpStatus.CONFLICT, "GEO_STATE_NAME_EXISTS");
        }
    }

    private CountryAdminDto toAdminDto(Country country) {
        return new CountryAdminDto(
                country.getId(), country.getIsoCode(), country.getName(), country.isActive(),
                stateProvinceRepository.countByCountryId(country.getId()),
                cityRepository.countByCountryId(country.getId()));
    }

    private String normalizeCode(String code) {
        String trimmed = trimToNull(code);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Audit event yayınlar. Kullanıcı kimliği TenantContext'ten alınır;
     * context yoksa (ör. internal çağrı) null geçilir — audit yazımı ana
     * işlemi asla bloklamaz (İş İsteri 5 tasarımı).
     */
    private void audit(String entityName, Long entityId, String actionType,
                       List<ConfigChangeEvent.FieldChange> changes) {
        Long userId = TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);
        eventPublisher.publishEvent(
                new ConfigChangeEvent(this, entityName, entityId, actionType, changes, userId));
    }
}
