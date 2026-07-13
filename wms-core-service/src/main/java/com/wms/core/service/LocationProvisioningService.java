package com.wms.core.service;

import com.wms.core.entity.*;
import com.wms.core.entity.enums.ZoneType;
import com.wms.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Yeni depo oluşturulduğunda varsayılan zone ve süreç konfigürasyonlarını kurar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProvisioningService {

    private static final List<DefaultZone> DEFAULT_ZONES = List.of(
            new DefaultZone("RECEIVING", "Mal Kabul", ZoneType.RECEIVING),
            new DefaultZone("STORAGE", "Ana Depolama", ZoneType.STANDARD),
            new DefaultZone("STAGING", "Hazırlık Alanı", ZoneType.STAGING),
            new DefaultZone("SHIPPING", "Sevkiyat", ZoneType.SHIPPING)
    );

    private final ZoneRepository zoneRepository;
    private final LocationProcessConfigRepository locationProcessConfigRepository;
    private final LocationProcessStepConfigRepository stepConfigRepository;
    private final UserAccessRepository userAccessRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void provisionCoreResources(Location location,
                                       Long creatorUserId,
                                       Long templateLocationId) {
        createDefaultZones(location);
        copyProcessConfigs(location.getId(), location.getCompany().getId(), templateLocationId);
        grantCreatorAccess(location, creatorUserId);
    }

    private void createDefaultZones(Location location) {
        for (DefaultZone def : DEFAULT_ZONES) {
            if (zoneRepository.findByLocationIdAndCode(location.getId(), def.code()).isPresent()) {
                continue;
            }
            Zone zone = Zone.builder()
                    .location(location)
                    .code(def.code())
                    .name(def.name())
                    .type(def.type())
                    .build();
            zoneRepository.save(zone);
        }
        log.info("Default zones created for locationId={}", location.getId());
    }

    private void copyProcessConfigs(Long newLocationId, Long companyId, Long templateLocationId) {
        Long sourceLocationId = resolveTemplateLocationId(companyId, templateLocationId);
        if (sourceLocationId == null) {
            log.warn("No template location for process config copy. locationId={}", newLocationId);
            return;
        }

        List<LocationProcessConfig> sourceConfigs =
                locationProcessConfigRepository.findActiveByLocationId(sourceLocationId);

        for (LocationProcessConfig source : sourceConfigs) {
            if (locationProcessConfigRepository
                    .findByLocationIdAndProcessDefinitionId(newLocationId, source.getProcessDefinition().getId())
                    .isPresent()) {
                continue;
            }

            LocationProcessConfig target = LocationProcessConfig.builder()
                    .locationId(newLocationId)
                    .processDefinition(source.getProcessDefinition())
                    .build();
            target = locationProcessConfigRepository.save(target);

            List<LocationProcessStepConfig> sourceSteps =
                    stepConfigRepository.findActiveStepsByConfigId(source.getId());
            for (LocationProcessStepConfig sourceStep : sourceSteps) {
                LocationProcessStepConfig targetStep = LocationProcessStepConfig.builder()
                        .locationProcessConfig(target)
                        .processStepDefinition(sourceStep.getProcessStepDefinition())
                        .sequence(sourceStep.getSequence())
                        .isMandatory(sourceStep.isMandatory())
                        .responsibleRoleId(sourceStep.getResponsibleRoleId())
                        .requiresApproval(sourceStep.isRequiresApproval())
                        .errorStrategy(sourceStep.getErrorStrategy())
                        .build();
                stepConfigRepository.save(targetStep);
            }
        }
        log.info("Process configs copied from locationId={} to locationId={}", sourceLocationId, newLocationId);
    }

    private Long resolveTemplateLocationId(Long companyId, Long templateLocationId) {
        if (templateLocationId != null) {
            return locationRepository.findByCompanyIdAndId(companyId, templateLocationId)
                    .map(Location::getId)
                    .orElse(null);
        }
        List<Location> companyLocations = locationRepository.findByCompanyIdAndIsActiveTrue(companyId);
        return companyLocations.isEmpty() ? null : companyLocations.getFirst().getId();
    }

    private void grantCreatorAccess(Location location, Long creatorUserId) {
        List<UserAccess> existing = userAccessRepository.findByUserIdAndCompanyId(creatorUserId, location.getCompany().getId());
        boolean hasAllLocations = existing.stream().anyMatch(a -> a.getLocation() == null);
        if (hasAllLocations) {
            return;
        }

        boolean alreadyHasLocation = existing.stream()
                .anyMatch(a -> a.getLocation() != null && a.getLocation().getId().equals(location.getId()));
        if (alreadyHasLocation) {
            return;
        }

        Optional<Role> adminRole = roleRepository.findByName("WMS_ADMIN");
        if (adminRole.isEmpty()) {
            log.warn("WMS_ADMIN role not found; skipping UserAccess grant for locationId={}", location.getId());
            return;
        }

        UserAccess access = UserAccess.builder()
                .user(userRepository.getReferenceById(creatorUserId))
                .company(location.getCompany())
                .location(location)
                .role(adminRole.get())
                .build();
        userAccessRepository.save(access);
        log.info("Granted WMS_ADMIN access to userId={} for locationId={}", creatorUserId, location.getId());
    }

    private record DefaultZone(String code, String name, ZoneType type) {}
}
