package com.wms.core.service;

import com.wms.core.dto.StorageLocationResponse;
import com.wms.core.dto.StorageLocationStatusUpdateRequest;
import com.wms.core.entity.Location;
import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.Zone;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.core.entity.enums.ZoneType;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.LocationUpdatedEventFactory;
import com.wms.core.repository.StorageLocationRepository;
import com.wms.core.repository.ZoneRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageLocationServiceTest {

    @Mock
    private StorageLocationRepository storageLocationRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private LocationUpdatedEventFactory locationUpdatedEventFactory;

    @InjectMocks
    private StorageLocationService storageLocationService;

    private Long locationId;
    private Long zoneId;
    private StorageLocation location;

    @BeforeEach
    void setUp() {
        locationId = 1L;
        zoneId = 1L;

        Location warehouse = new Location();
        warehouse.setId(1L);

        Zone zone = new Zone();
        zone.setId(zoneId);
        zone.setLocation(warehouse);
        zone.setCode("STG-A");
        zone.setType(ZoneType.STAGING);

        location = new StorageLocation();
        location.setId(locationId);
        location.setZone(zone);
        location.setAisle("A");
        location.setBay("01");
        location.setShelf("03");
        location.setBin("01");
        location.setAddressCode("A-01-03-01");
        location.setMaxVolume(new BigDecimal("100.0000"));
        location.setMaxWeight(new BigDecimal("500.0000"));
        location.setCurrentVolume(new BigDecimal("20.0000"));
        location.setCurrentWeight(new BigDecimal("100.0000"));
        location.setStatus(StorageLocationStatus.ACTIVE);

        TenantContextHolder.setContext(new TenantContext(1L, 1L, 1L));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void updateStatus_setsBlocked() {
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(location.getZone()));
        when(storageLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorageLocationResponse response = storageLocationService.updateStatus(
                locationId, new StorageLocationStatusUpdateRequest(StorageLocationStatus.BLOCKED));

        assertThat(response.status()).isEqualTo(StorageLocationStatus.BLOCKED);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateStatus_setsActiveWhenCapacityAvailable() {
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(location.getZone()));
        when(storageLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorageLocationResponse response = storageLocationService.updateStatus(
                locationId, new StorageLocationStatusUpdateRequest(StorageLocationStatus.ACTIVE));

        assertThat(response.status()).isEqualTo(StorageLocationStatus.ACTIVE);
    }

    @Test
    void updateStatus_setsFullWhenReactivatingAtCapacity() {
        location.setCurrentVolume(new BigDecimal("100.0000"));
        location.setCurrentWeight(new BigDecimal("500.0000"));
        location.setStatus(StorageLocationStatus.BLOCKED);

        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(location.getZone()));
        when(storageLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorageLocationResponse response = storageLocationService.updateStatus(
                locationId, new StorageLocationStatusUpdateRequest(StorageLocationStatus.ACTIVE));

        assertThat(response.status()).isEqualTo(StorageLocationStatus.FULL);
    }

    @Test
    void findById_rejectsStorageLocationFromAnotherWarehouse() {
        Location berlinWarehouse = new Location();
        berlinWarehouse.setId(2L);
        Zone berlinZone = new Zone();
        berlinZone.setId(99L);
        berlinZone.setLocation(berlinWarehouse);
        location.setZone(berlinZone);

        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(zoneRepository.findById(99L)).thenReturn(Optional.of(berlinZone));

        assertThatThrownBy(() -> storageLocationService.findById(locationId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateStatus_rejectsManualFull() {
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(location.getZone()));

        assertThatThrownBy(() -> storageLocationService.updateStatus(
                locationId, new StorageLocationStatusUpdateRequest(StorageLocationStatus.FULL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_delegatesToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(location.getZone()));
        when(storageLocationRepository.searchLocations(eq(zoneId), eq(StorageLocationStatus.ACTIVE),
                eq("A"), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(location)));

        Page<StorageLocationResponse> result = storageLocationService.search(
                zoneId, StorageLocationStatus.ACTIVE, "A", true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().addressCode()).isEqualTo("A-01-03-01");
    }

    @Test
    void findCriticalUtilization_usesDefaultThreshold() {
        when(zoneRepository.findByLocationId(1L)).thenReturn(List.of(location.getZone()));
        when(storageLocationRepository.findLocationsAboveVolumeUtilizationInZones(
                eq(List.of(zoneId)), eq(new BigDecimal("0.80"))))
                .thenReturn(List.of(location));

        List<StorageLocationResponse> result = storageLocationService.findCriticalUtilization(null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().volumeUtilizationPercent()).isEqualByComparingTo("20.00");
    }
}
