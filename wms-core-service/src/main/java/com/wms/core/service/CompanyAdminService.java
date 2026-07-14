package com.wms.core.service;

import com.wms.core.dto.company.CompanyAdminDto;
import com.wms.core.dto.company.CompanyUsageDto;
import com.wms.core.dto.company.CreateCompanyRequest;
import com.wms.core.dto.company.OrganizationOptionDto;
import com.wms.core.dto.company.UpdateCompanyRequest;
import com.wms.core.entity.Company;
import com.wms.core.entity.Organization;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.CompanyChangedEventFactory;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.OrganizationRepository;
import com.wms.core.repository.TransactionLogRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Firma (Company) admin CRUD servisi.
 *
 * <h3>Tasarım kararları</h3>
 * <ul>
 *   <li><b>Soft delete:</b> Pasifleştirme {@code @SQLDelete} ile yapılır;
 *       aktif lokasyon veya kullanıcı yetkisi varken engellenir (409).</li>
 *   <li><b>Vergi no tekilliği:</b> {@code tax_number} pasif kayıtlar dahil
 *       benzersizdir; mükerrerde 409 + reaktivasyon önerisi.</li>
 *   <li><b>Audit:</b> Tüm yazma işlemleri {@link ConfigChangeEvent} ile
 *       asenkron audit log'a yazılır.</li>
 *   <li><b>Realtime:</b> Yazma işlemleri sonrası {@link CompanyChangedEventFactory}
 *       ile Kafka/STOMP yayını yapılır (Organizasyon Yapısı anlık yenileme).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyAdminService {

    private final CompanyRepository companyRepository;
    private final OrganizationRepository organizationRepository;
    private final LocationRepository locationRepository;
    private final UserAccessRepository userAccessRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyChangedEventFactory companyChangedEventFactory;

    /** Admin listesi: pasif kayıtlar dahil tüm firmalar + aktif depo sayısı. */
    @Transactional(readOnly = true)
    public List<CompanyAdminDto> listCompanies() {
        return companyRepository.findAllAdminRows().stream()
                .map(this::mapAdminRow)
                .toList();
    }

    /** Firma oluşturma formundaki aktif organizasyon seçenekleri. */
    @Transactional(readOnly = true)
    public List<OrganizationOptionDto> listOrganizations() {
        return organizationRepository.findAll().stream()
                .map(o -> new OrganizationOptionDto(o.getId(), o.getName()))
                .toList();
    }

    /**
     * Yeni firma oluşturur.
     *
     * <p>tax_number trim + uppercase normalize edilmez (alfanumerik kodlar
     * korunur); yalnızca trim uygulanır. Tekillik pasif kayıtlar dahil
     * kontrol edilir.</p>
     */
    public CompanyAdminDto createCompany(CreateCompanyRequest request) {
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new BusinessException(
                        "Organizasyon bulunamadı: " + request.organizationId(),
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND"));

        String taxNumber = normalizeTaxNumber(request.taxNumber());
        if (companyRepository.existsByTaxNumberIncludingInactive(taxNumber)) {
            throw new BusinessException(
                    "Bu vergi numarası ile bir firma zaten mevcut (pasif olabilir): " + taxNumber,
                    HttpStatus.CONFLICT, "COMPANY_TAX_EXISTS");
        }

        String name = request.name().trim();
        String taxOffice = trimToNull(request.taxOffice());

        Company company = Company.builder()
                .organization(organization)
                .name(name)
                .taxNumber(taxNumber)
                .taxOffice(taxOffice)
                .build();
        companyRepository.save(company);

        log.info("[CompanyAdmin] Firma oluşturuldu. id={} name={} tax={}",
                company.getId(), name, taxNumber);

        audit("Company", company.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("organizationId", null, String.valueOf(organization.getId())),
                new ConfigChangeEvent.FieldChange("name", null, name),
                new ConfigChangeEvent.FieldChange("taxNumber", null, taxNumber),
                new ConfigChangeEvent.FieldChange("taxOffice", null, taxOffice)));

        companyChangedEventFactory.publish(
                organization.getId(), company.getId(), "CREATE", name, true);

        return toAdminDto(company);
    }

    /** Firma adı, vergi numarası ve vergi dairesini günceller. */
    public CompanyAdminDto updateCompany(Long companyId, UpdateCompanyRequest request) {
        Company company = findActiveCompany(companyId);
        String newName = request.name().trim();
        String newTax = normalizeTaxNumber(request.taxNumber());
        String newOffice = trimToNull(request.taxOffice());

        if (!newTax.equals(company.getTaxNumber())
                && companyRepository.existsByTaxNumberIncludingInactiveExcludingId(newTax, companyId)) {
            throw new BusinessException(
                    "Bu vergi numarası ile bir firma zaten mevcut (pasif olabilir): " + newTax,
                    HttpStatus.CONFLICT, "COMPANY_TAX_EXISTS");
        }

        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();
        if (!newName.equals(company.getName())) {
            changes.add(new ConfigChangeEvent.FieldChange("name", company.getName(), newName));
            company.setName(newName);
        }
        if (!newTax.equals(company.getTaxNumber())) {
            changes.add(new ConfigChangeEvent.FieldChange("taxNumber", company.getTaxNumber(), newTax));
            company.setTaxNumber(newTax);
        }
        if (!Objects.equals(newOffice, company.getTaxOffice())) {
            changes.add(new ConfigChangeEvent.FieldChange("taxOffice", company.getTaxOffice(), newOffice));
            company.setTaxOffice(newOffice);
        }

        if (!changes.isEmpty()) {
            companyRepository.save(company);
            audit("Company", companyId, "UPDATE", changes);
            log.info("[CompanyAdmin] Firma güncellendi. id={} name={}", companyId, newName);
            companyChangedEventFactory.publish(
                    company.getOrganization().getId(), companyId, "UPDATE", newName, company.isActive());
        }
        return toAdminDto(company);
    }

    /**
     * Firmayı pasifleştirir (soft delete).
     *
     * <p>Aktif lokasyon veya kullanıcı yetkisi varsa 409 Conflict.</p>
     */
    public void deactivateCompany(Long companyId) {
        Company company = findActiveCompany(companyId);

        long activeLocations = locationRepository.countActiveByCompanyId(companyId);
        long userAccesses = userAccessRepository.countByCompanyId(companyId);
        if (activeLocations > 0 || userAccesses > 0) {
            throw new BusinessException(
                    "Firma pasifleştirilemez: " + activeLocations + " aktif depo ve "
                            + userAccesses + " kullanıcı yetkisi bağlı. Önce bağımlılıkları kaldırın.",
                    HttpStatus.CONFLICT, "COMPANY_IN_USE");
        }

        companyRepository.delete(company); // @SQLDelete → is_active = false

        log.info("[CompanyAdmin] Firma pasifleştirildi. id={} tax={}", companyId, company.getTaxNumber());
        audit("Company", companyId, "DELETE", List.of(
                new ConfigChangeEvent.FieldChange("isActive", "true", "false")));
        companyChangedEventFactory.publish(
                company.getOrganization().getId(), companyId, "DELETE", company.getName(), false);
    }

    /** Pasif firmayı yeniden aktifleştirir. */
    public CompanyAdminDto reactivateCompany(Long companyId) {
        Company company = companyRepository.findByIdIncludingInactive(companyId)
                .orElseThrow(() -> companyNotFound(companyId));
        if (company.isActive()) {
            throw new BusinessException("Firma zaten aktif: " + companyId,
                    HttpStatus.CONFLICT, "COMPANY_ALREADY_ACTIVE");
        }
        companyRepository.reactivate(companyId);

        log.info("[CompanyAdmin] Firma aktifleştirildi. id={} tax={}", companyId, company.getTaxNumber());
        audit("Company", companyId, "UPDATE", List.of(
                new ConfigChangeEvent.FieldChange("isActive", "false", "true")));

        company.setActive(true);
        companyChangedEventFactory.publish(
                company.getOrganization().getId(), companyId, "REACTIVATE", company.getName(), true);
        return toAdminDto(company);
    }

    /** Firma kullanım özeti — pasifleştirme onay diyaloğu için. */
    @Transactional(readOnly = true)
    public CompanyUsageDto getCompanyUsage(Long companyId) {
        findCompanyIncludingInactive(companyId);
        long activeLocations = locationRepository.countActiveByCompanyId(companyId);
        long userAccesses = userAccessRepository.countByCompanyId(companyId);
        long txLogs = transactionLogRepository.countByCompanyId(companyId);
        boolean canDeactivate = activeLocations == 0 && userAccesses == 0;
        return new CompanyUsageDto(companyId, activeLocations, userAccesses, txLogs, canDeactivate);
    }

    // ── Yardımcılar ───────────────────────────────────────────────────

    private Company findActiveCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> companyNotFound(companyId));
    }

    private Company findCompanyIncludingInactive(Long companyId) {
        return companyRepository.findByIdIncludingInactive(companyId)
                .orElseThrow(() -> companyNotFound(companyId));
    }

    private BusinessException companyNotFound(Long companyId) {
        return new BusinessException("Firma bulunamadı: " + companyId,
                HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND");
    }

    private CompanyAdminDto toAdminDto(Company company) {
        Organization org = company.getOrganization();
        return new CompanyAdminDto(
                company.getId(),
                org != null ? org.getId() : null,
                org != null ? org.getName() : null,
                company.getName(),
                company.getTaxNumber(),
                company.getTaxOffice(),
                company.isActive(),
                locationRepository.countActiveByCompanyId(company.getId()));
    }

    private CompanyAdminDto mapAdminRow(Object[] row) {
        return new CompanyAdminDto(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                toBoolean(row[6]),
                ((Number) row[7]).longValue());
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private String normalizeTaxNumber(String taxNumber) {
        return taxNumber.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void audit(String entityName, Long entityId, String actionType,
                       List<ConfigChangeEvent.FieldChange> changes) {
        Long userId = TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);
        eventPublisher.publishEvent(
                new ConfigChangeEvent(this, entityName, entityId, actionType, changes, userId));
    }
}
