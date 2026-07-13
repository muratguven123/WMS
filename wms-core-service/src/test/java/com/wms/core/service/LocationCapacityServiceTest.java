package com.wms.core.service;

import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.core.exception.BusinessException;
import com.wms.core.exception.LocationCapacityExceededException;
import com.wms.core.repository.StorageLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationCapacityServiceTest {

    @Mock
    private StorageLocationRepository storageLocationRepository;

    @InjectMocks
    private LocationCapacityService locationCapacityService;

    private Long locationId;
    private StorageLocation location;

    @BeforeEach
    void setUp() {
        locationId = 1L;
        location = new StorageLocation();
        location.setId(locationId);
        location.setMaxVolume(new BigDecimal("100.0000"));
        location.setMaxWeight(new BigDecimal("500.0000"));
        location.setCurrentVolume(new BigDecimal("20.0000"));
        location.setCurrentWeight(new BigDecimal("100.0000"));
        location.setStatus(StorageLocationStatus.ACTIVE);
    }

    // =========================================================================
    // hasAvailableCapacity Tests
    // =========================================================================

    @Test
    void hasAvailableCapacity_returnsTrue_whenWithinLimits() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal itemVolume = new BigDecimal("10.0000"); // Available: 80
        BigDecimal itemWeight = new BigDecimal("50.0000"); // Available: 400

        // When
        boolean result = locationCapacityService.hasAvailableCapacity(locationId, itemVolume, itemWeight);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasAvailableCapacity_returnsTrue_whenExactlyAtLimits() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal itemVolume = new BigDecimal("80.0000"); // Available: 80
        BigDecimal itemWeight = new BigDecimal("400.0000"); // Available: 400

        // When
        boolean result = locationCapacityService.hasAvailableCapacity(locationId, itemVolume, itemWeight);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasAvailableCapacity_returnsFalse_whenVolumeExceedsLimit() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal itemVolume = new BigDecimal("81.0000"); // Available: 80
        BigDecimal itemWeight = new BigDecimal("50.0000"); // Available: 400

        // When
        boolean result = locationCapacityService.hasAvailableCapacity(locationId, itemVolume, itemWeight);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasAvailableCapacity_returnsFalse_whenWeightExceedsLimit() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal itemVolume = new BigDecimal("10.0000"); // Available: 80
        BigDecimal itemWeight = new BigDecimal("401.0000"); // Available: 400

        // When
        boolean result = locationCapacityService.hasAvailableCapacity(locationId, itemVolume, itemWeight);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasAvailableCapacity_throwsException_whenLocationNotFound() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> locationCapacityService.hasAvailableCapacity(locationId, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Storage location not found")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void hasAvailableCapacity_throwsException_whenArgumentsInvalid() {
        // Null checks
        assertThatThrownBy(() -> locationCapacityService.hasAvailableCapacity(null, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locationCapacityService.hasAvailableCapacity(locationId, null, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locationCapacityService.hasAvailableCapacity(locationId, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Negative check
        assertThatThrownBy(() -> locationCapacityService.hasAvailableCapacity(locationId, new BigDecimal("-1.00"), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locationCapacityService.hasAvailableCapacity(locationId, BigDecimal.TEN, new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasAvailableCapacity_returnsFalse_whenLocationIsBlocked() {
        location.setStatus(StorageLocationStatus.BLOCKED);
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));

        boolean result = locationCapacityService.hasAvailableCapacity(
                locationId, new BigDecimal("10.0000"), new BigDecimal("50.0000"));

        assertThat(result).isFalse();
    }

    @Test
    void hasAvailableCapacity_returnsFalse_whenLocationIsFull() {
        location.setStatus(StorageLocationStatus.FULL);
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));

        boolean result = locationCapacityService.hasAvailableCapacity(
                locationId, new BigDecimal("10.0000"), new BigDecimal("50.0000"));

        assertThat(result).isFalse();
    }

    @Test
    void hasAvailableCapacity_returnsFalse_whenLocationIsInactive() {
        location.setActive(false);
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));

        boolean result = locationCapacityService.hasAvailableCapacity(
                locationId, new BigDecimal("10.0000"), new BigDecimal("50.0000"));

        assertThat(result).isFalse();
    }

    // =========================================================================
    // updateLocationLoad - Addition Tests
    // =========================================================================

    @Test
    void updateLocationLoad_throwsException_whenAddingToBlockedLocation() {
        location.setStatus(StorageLocationStatus.BLOCKED);
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(
                locationId, new BigDecimal("10.0000"), new BigDecimal("50.0000"), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BLOCKED")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(storageLocationRepository, never()).save(any());
    }

    @Test
    void updateLocationLoad_addsLoadSuccessfully_whenWithinLimits() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("30.0000");
        BigDecimal weightDelta = new BigDecimal("150.0000");

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, true);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo("50.0000");
        assertThat(location.getCurrentWeight()).isEqualByComparingTo("250.0000");
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.ACTIVE);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_setsStatusToFull_whenLoadReachesMaxVolumeLimit() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("80.0000"); // Reaches 100.0000 (Max Volume)
        BigDecimal weightDelta = new BigDecimal("100.0000"); // Reaches 200.0000 (Max is 500)

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, true);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo("100.0000");
        assertThat(location.getCurrentWeight()).isEqualByComparingTo("200.0000");
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.FULL);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_setsStatusToFull_whenLoadReachesMaxWeightLimit() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("20.0000"); // Reaches 40.0000 (Max is 100)
        BigDecimal weightDelta = new BigDecimal("400.0000"); // Reaches 500.0000 (Max Weight)

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, true);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo("40.0000");
        assertThat(location.getCurrentWeight()).isEqualByComparingTo("500.0000");
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.FULL);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_throwsException_whenVolumeExceedsLimit() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("80.0001"); // Reaches 100.0001
        BigDecimal weightDelta = new BigDecimal("100.0000");

        // When & Then
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, true))
                .isInstanceOf(LocationCapacityExceededException.class)
                .hasMessageContaining("Location capacity exceeded");
        verify(storageLocationRepository, never()).save(any());
    }

    @Test
    void updateLocationLoad_throwsException_whenWeightExceedsLimit() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("10.0000");
        BigDecimal weightDelta = new BigDecimal("400.0001"); // Reaches 500.0001

        // When & Then
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, true))
                .isInstanceOf(LocationCapacityExceededException.class)
                .hasMessageContaining("Location capacity exceeded");
        verify(storageLocationRepository, never()).save(any());
    }

    // =========================================================================
    // updateLocationLoad - Subtraction Tests
    // =========================================================================

    @Test
    void updateLocationLoad_subtractsLoadSuccessfully() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("10.0000");
        BigDecimal weightDelta = new BigDecimal("50.0000");

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, false);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo("10.0000");
        assertThat(location.getCurrentWeight()).isEqualByComparingTo("50.0000");
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.ACTIVE);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_clampsToZero_whenSubtractingMoreThanCurrent() {
        // Given
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));
        BigDecimal volumeDelta = new BigDecimal("30.0000"); // Current is 20
        BigDecimal weightDelta = new BigDecimal("150.0000"); // Current is 100

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, false);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(location.getCurrentWeight()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.ACTIVE);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_revertsStatusFromFullToActive_whenLoadDecreases() {
        // Given
        location.setCurrentVolume(new BigDecimal("100.0000"));
        location.setCurrentWeight(new BigDecimal("500.0000"));
        location.setStatus(StorageLocationStatus.FULL);
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));

        BigDecimal volumeDelta = new BigDecimal("1.0000");
        BigDecimal weightDelta = new BigDecimal("1.0000");

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, false);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo("99.0000");
        assertThat(location.getCurrentWeight()).isEqualByComparingTo("499.0000");
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.ACTIVE);
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_retainsBlockedStatus_whenLoadDecreasesOnBlockedLocation() {
        // Given
        location.setCurrentVolume(new BigDecimal("100.0000"));
        location.setCurrentWeight(new BigDecimal("500.0000"));
        location.setStatus(StorageLocationStatus.BLOCKED);
        when(storageLocationRepository.findById(locationId)).thenReturn(Optional.of(location));

        BigDecimal volumeDelta = new BigDecimal("10.0000");
        BigDecimal weightDelta = new BigDecimal("50.0000");

        // When
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, false);

        // Then
        assertThat(location.getCurrentVolume()).isEqualByComparingTo("90.0000");
        assertThat(location.getCurrentWeight()).isEqualByComparingTo("450.0000");
        assertThat(location.getStatus()).isEqualTo(StorageLocationStatus.BLOCKED); // Should not become ACTIVE
        verify(storageLocationRepository).save(location);
    }

    @Test
    void updateLocationLoad_throwsException_whenArgumentsInvalid() {
        // Null checks
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(null, BigDecimal.TEN, BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(locationId, null, BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(locationId, BigDecimal.TEN, null, true))
                .isInstanceOf(IllegalArgumentException.class);

        // Negative check
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(locationId, new BigDecimal("-1.00"), BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locationCapacityService.updateLocationLoad(locationId, BigDecimal.TEN, new BigDecimal("-1.00"), true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
