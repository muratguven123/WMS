package com.wms.localization.service.address;

import com.wms.localization.domain.address.AddressTemplateField;
import com.wms.localization.domain.address.CountryAddressTemplate;
import com.wms.localization.dto.address.*;
import com.wms.localization.exception.address.TemplateConfigException;
import com.wms.localization.integration.CoreMasterDataClient;
import com.wms.localization.repository.address.AddressTemplateFieldRepository;
import com.wms.localization.repository.address.CountryAddressTemplateRepository;
import com.wms.localization.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * İş İsteri 17 — Adres şablonu ve alan kataloğu admin CRUD servisi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AddressTemplateAdminService {

    private static final Set<String> ROOT_PARENT_KEYS = Set.of("__country__", "__city__");

    private final AddressTemplateFieldRepository fieldRepository;
    private final CountryAddressTemplateRepository templateRepository;
    private final CoreMasterDataClient coreMasterDataClient;

    // ══════════════════════════════════════════════════════════════════
    // Alan kataloğu
    // ══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AddressTemplateFieldDto> listFields() {
        return fieldRepository.findAll().stream()
                .sorted(Comparator.comparing(AddressTemplateField::getFieldKey))
                .map(AddressTemplateFieldDto::from)
                .toList();
    }

    public AddressTemplateFieldDto createField(CreateAddressTemplateFieldRequest request) {
        String fieldKey = request.fieldKey().trim();
        if (fieldRepository.existsByFieldKey(fieldKey)) {
            throw conflict("Bu field_key zaten mevcut: " + fieldKey, "TEMPLATE_FIELD_KEY_EXISTS");
        }
        validateParentKey(request.parentFieldKey(), fieldKey);

        AddressTemplateField field = AddressTemplateField.builder()
                .fieldKey(fieldKey)
                .fieldLabelKey(request.fieldLabelKey().trim())
                .fieldType(AddressTemplateField.FieldType.valueOf(request.fieldType()))
                .masterDataSource(AddressTemplateField.MasterDataSource.valueOf(request.masterDataSource()))
                .parentFieldKey(trimToNull(request.parentFieldKey()))
                .build();
        fieldRepository.save(field);

        log.info("[TemplateAdmin] Alan kataloğuna eklendi. id={} key={}", field.getId(), fieldKey);
        return AddressTemplateFieldDto.from(field);
    }

    public AddressTemplateFieldDto updateField(Long fieldId, UpdateAddressTemplateFieldRequest request) {
        AddressTemplateField field = findField(fieldId);
        validateParentKey(request.parentFieldKey(), field.getFieldKey());

        field.setFieldLabelKey(request.fieldLabelKey().trim());
        field.setFieldType(AddressTemplateField.FieldType.valueOf(request.fieldType()));
        field.setMasterDataSource(AddressTemplateField.MasterDataSource.valueOf(request.masterDataSource()));
        field.setParentFieldKey(trimToNull(request.parentFieldKey()));
        fieldRepository.save(field);

        log.info("[TemplateAdmin] Alan kataloğu güncellendi. id={} key={}", fieldId, field.getFieldKey());
        return AddressTemplateFieldDto.from(field);
    }

    public void deleteField(Long fieldId) {
        AddressTemplateField field = findField(fieldId);
        if (fieldRepository.existsInAnyCountryTemplate(fieldId)) {
            throw conflict(
                    "Alan en az bir ülke şablonunda kullanılıyor; önce şablonlardan çıkarın: " + field.getFieldKey(),
                    "TEMPLATE_FIELD_IN_USE");
        }
        fieldRepository.delete(field);
        log.info("[TemplateAdmin] Alan katalogdan silindi. id={} key={}", fieldId, field.getFieldKey());
    }

    // ══════════════════════════════════════════════════════════════════
    // Ülke şablonu
    // ══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<CountryAddressTemplateDto> getTemplate(Long countryId) {
        return templateRepository.findByCountryIdOrderBySequenceAsc(countryId).stream()
                .map(CountryAddressTemplateDto::from)
                .toList();
    }

    public TemplateFieldMutationResponse addTemplateField(Long countryId, AddCountryTemplateFieldRequest request) {
        AddressTemplateField field = findField(request.fieldId());

        if (templateRepository.existsByCountryIdAndAddressTemplateField_Id(countryId, field.getId())) {
            throw conflict("Bu alan ülke şablonunda zaten var: " + field.getFieldKey(), "TEMPLATE_FIELD_EXISTS");
        }

        validateParentInTemplate(countryId, field);
        validateRegex(request.validationRegex());

        int sequence = request.sequence() != null
                ? request.sequence()
                : templateRepository.maxSequenceByCountryId(countryId) + 1;

        CountryAddressTemplate row = CountryAddressTemplate.builder()
                .countryId(countryId)
                .addressTemplateField(field)
                .mandatory(Boolean.TRUE.equals(request.mandatory()))
                .sequence(sequence)
                .validationRegex(trimToNull(request.validationRegex()))
                .errorMessageKey(trimToNull(request.errorMessageKey()))
                .updatedBy(SecurityUtils.currentJwtSubject())
                .build();
        templateRepository.save(row);

        String warning = checkMasterDataWarning(countryId, field);
        log.info("[TemplateAdmin] Şablona alan eklendi. countryId={} fieldKey={} warning={}",
                countryId, field.getFieldKey(), warning);

        return new TemplateFieldMutationResponse(CountryAddressTemplateDto.from(row), warning);
    }

    public TemplateFieldMutationResponse updateTemplateField(
            Long countryId, Long templateId, UpdateCountryTemplateFieldRequest request) {
        CountryAddressTemplate row = findTemplateRow(countryId, templateId);

        if (request.mandatory() != null) {
            row.setMandatory(request.mandatory());
        }
        if (request.validationRegex() != null) {
            validateRegex(request.validationRegex());
            row.setValidationRegex(trimToNull(request.validationRegex()));
        }
        if (request.errorMessageKey() != null) {
            row.setErrorMessageKey(trimToNull(request.errorMessageKey()));
        }
        row.setUpdatedBy(SecurityUtils.currentJwtSubject());
        templateRepository.save(row);

        log.info("[TemplateAdmin] Şablon satırı güncellendi. countryId={} templateId={}", countryId, templateId);
        return new TemplateFieldMutationResponse(CountryAddressTemplateDto.from(row), null);
    }

    public List<CountryAddressTemplateDto> reorderTemplate(Long countryId, ReorderCountryTemplateRequest request) {
        List<CountryAddressTemplate> existing =
                templateRepository.findByCountryIdOrderBySequenceAsc(countryId);
        Map<Long, CountryAddressTemplate> byId = existing.stream()
                .collect(Collectors.toMap(CountryAddressTemplate::getId, t -> t));

        Set<Long> seen = new HashSet<>();
        for (ReorderEntryDto entry : request.entries()) {
            if (!byId.containsKey(entry.templateId())) {
                throw badRequest("Geçersiz templateId: " + entry.templateId(), "TEMPLATE_ROW_NOT_FOUND");
            }
            if (!seen.add(entry.templateId())) {
                throw badRequest("Mükerrer templateId: " + entry.templateId(), "TEMPLATE_REORDER_DUPLICATE");
            }
            CountryAddressTemplate row = byId.get(entry.templateId());
            row.setSequence(entry.sequence());
            row.setUpdatedBy(SecurityUtils.currentJwtSubject());
        }
        templateRepository.saveAll(existing);

        log.info("[TemplateAdmin] Şablon sırası güncellendi. countryId={} rows={}", countryId, request.entries().size());
        return templateRepository.findByCountryIdOrderBySequenceAsc(countryId).stream()
                .map(CountryAddressTemplateDto::from)
                .toList();
    }

    public void removeTemplateField(Long countryId, Long templateId) {
        CountryAddressTemplate row = findTemplateRow(countryId, templateId);
        String removedKey = row.getAddressTemplateField().getFieldKey();

        List<CountryAddressTemplate> all = templateRepository.findByCountryIdOrderBySequenceAsc(countryId);
        boolean hasDependent = all.stream()
                .filter(t -> !t.getId().equals(templateId))
                .map(t -> t.getAddressTemplateField().getParentFieldKey())
                .filter(Objects::nonNull)
                .anyMatch(parent -> parent.equals(removedKey));
        if (hasDependent) {
            throw conflict(
                    "Bu alana bağlı alt alanlar şablonda mevcut; önce onları çıkarın: " + removedKey,
                    "TEMPLATE_HAS_CHILDREN");
        }

        templateRepository.delete(row);
        log.info("[TemplateAdmin] Şablondan alan çıkarıldı. countryId={} fieldKey={}", countryId, removedKey);
    }

    public CopyTemplateResultDto copyFrom(Long countryId, Long sourceCountryId) {
        if (countryId.equals(sourceCountryId)) {
            throw badRequest("Kaynak ve hedef ülke aynı olamaz", "TEMPLATE_COPY_SAME_COUNTRY");
        }
        List<CountryAddressTemplate> source =
                templateRepository.findByCountryIdOrderBySequenceAsc(sourceCountryId);
        if (source.isEmpty()) {
            throw badRequest("Kaynak ülkenin şablonu boş: " + sourceCountryId, "TEMPLATE_SOURCE_EMPTY");
        }

        templateRepository.deleteByCountryId(countryId);
        String subject = SecurityUtils.currentJwtSubject();

        List<CountryAddressTemplate> copies = source.stream()
                .map(s -> CountryAddressTemplate.builder()
                        .countryId(countryId)
                        .addressTemplateField(s.getAddressTemplateField())
                        .mandatory(s.isMandatory())
                        .sequence(s.getSequence())
                        .validationRegex(s.getValidationRegex())
                        .errorMessageKey(s.getErrorMessageKey())
                        .updatedBy(subject)
                        .build())
                .toList();
        templateRepository.saveAll(copies);

        log.info("[TemplateAdmin] Şablon kopyalandı. target={} source={} count={}",
                countryId, sourceCountryId, copies.size());
        return new CopyTemplateResultDto(countryId, sourceCountryId, copies.size());
    }

    // ══════════════════════════════════════════════════════════════════
    // Yardımcılar
    // ══════════════════════════════════════════════════════════════════

    private void validateParentInTemplate(Long countryId, AddressTemplateField field) {
        String parentKey = field.getParentFieldKey();
        if (parentKey == null || ROOT_PARENT_KEYS.contains(parentKey)) {
            return;
        }
        if (!templateRepository.existsByCountryIdAndAddressTemplateField_FieldKey(countryId, parentKey)) {
            throw badRequest(
                    "Parent alan önce şablona eklenmelidir: " + parentKey,
                    "TEMPLATE_PARENT_MISSING");
        }
    }

    private void validateParentKey(String parentFieldKey, String fieldKey) {
        if (parentFieldKey == null || parentFieldKey.isBlank()) {
            return;
        }
        String parent = parentFieldKey.trim();
        if (parent.equals(fieldKey)) {
            throw badRequest("Alan kendi parent'ı olamaz", "TEMPLATE_CIRCULAR_PARENT");
        }
        if (fieldRepository.findByFieldKey(parent).isEmpty() && !ROOT_PARENT_KEYS.contains(parent)) {
            throw badRequest("Parent field_key katalogda bulunamadı: " + parent, "TEMPLATE_PARENT_UNKNOWN");
        }
    }

    private String checkMasterDataWarning(Long countryId, AddressTemplateField field) {
        if (field.getFieldType() != AddressTemplateField.FieldType.MASTER_SELECT) {
            return null;
        }
        String source = field.getMasterDataSource().name();
        if ("NONE".equals(source)) {
            return null;
        }
        if (!coreMasterDataClient.hasMasterData(countryId, source)) {
            return "MASTER_DATA_EMPTY";
        }
        return null;
    }

    private void validateRegex(String regex) {
        if (regex == null || regex.isBlank()) {
            return;
        }
        try {
            Pattern.compile(regex.trim());
        } catch (PatternSyntaxException ex) {
            throw badRequest("Geçersiz regex: " + ex.getDescription(), "TEMPLATE_REGEX_INVALID");
        }
    }

    private AddressTemplateField findField(Long fieldId) {
        return fieldRepository.findById(fieldId)
                .orElseThrow(() -> notFound("Alan bulunamadı: " + fieldId, "TEMPLATE_FIELD_NOT_FOUND"));
    }

    private CountryAddressTemplate findTemplateRow(Long countryId, Long templateId) {
        return templateRepository.findByIdAndCountryId(templateId, countryId)
                .orElseThrow(() -> notFound(
                        "Şablon satırı bulunamadı: countryId=" + countryId + " templateId=" + templateId,
                        "TEMPLATE_ROW_NOT_FOUND"));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TemplateConfigException badRequest(String message, String code) {
        return new TemplateConfigException(message, HttpStatus.BAD_REQUEST, code);
    }

    private TemplateConfigException conflict(String message, String code) {
        return new TemplateConfigException(message, HttpStatus.CONFLICT, code);
    }

    private TemplateConfigException notFound(String message, String code) {
        return new TemplateConfigException(message, HttpStatus.NOT_FOUND, code);
    }
}
