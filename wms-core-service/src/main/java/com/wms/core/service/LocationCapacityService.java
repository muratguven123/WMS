package com.wms.core.service;

import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.core.exception.BusinessException;
import com.wms.core.exception.LocationCapacityExceededException;
import com.wms.core.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Ürün depo gözüne yerleştirilmek istendiğinde hacimsel ve ağırlık kapasitesini doğrulayan,
 * stok giriş-çıkışlarında kapasite güncelleyen servis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationCapacityService {

    private final StorageLocationRepository storageLocationRepository;

    /**
     * Kapasite Uygunluk Metodu:
     * Belirtilen lokasyonun mevcut boş hacmini (maxVolume - currentVolume) ve boş ağırlık kapasitesini (maxWeight - currentWeight) hesaplar.
     * Gelen ürünün hacmi ve ağırlığı bu boş kapasiteden küçük veya eşitse true, değilse false döner.
     *
     * @param storageLocationId Lokasyon ID
     * @param itemVolume        Gelen ürün hacmi (m³)
     * @param itemWeight        Gelen ürün ağırlığı (kg)
     * @return Kapasite uygunsa true, değilse false
     */
    @Transactional(readOnly = true)
    public boolean hasAvailableCapacity(Long storageLocationId, BigDecimal itemVolume, BigDecimal itemWeight) {
        if (storageLocationId == null) {
            throw new IllegalArgumentException("Storage location ID cannot be null");
        }
        if (itemVolume == null || itemWeight == null) {
            throw new IllegalArgumentException("Item volume and weight cannot be null");
        }
        if (itemVolume.compareTo(BigDecimal.ZERO) < 0 || itemWeight.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Item volume and weight cannot be negative");
        }

        StorageLocation location = storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new BusinessException("Storage location not found with ID: " + storageLocationId, HttpStatus.NOT_FOUND));

        if (!location.isActive()) {
            return false;
        }
        if (location.getStatus() == StorageLocationStatus.BLOCKED
                || location.getStatus() == StorageLocationStatus.FULL) {
            return false;
        }

        BigDecimal availableVolume = location.getMaxVolume().subtract(location.getCurrentVolume());
        BigDecimal availableWeight = location.getMaxWeight().subtract(location.getCurrentWeight());

        return itemVolume.compareTo(availableVolume) <= 0 && itemWeight.compareTo(availableWeight) <= 0;
    }

    /**
     * Kapasite Güncelleme Metodu (Stok Girişi/Çıkışı):
     * isAddition = true ise, lokasyonun currentVolume ve currentWeight değerlerini gelen delta değerleri kadar artırır.
     * Eğer yeni yük max limitleri aşarsa LocationCapacityExceededException fırlatır.
     * isAddition = false ise (stok çıkışı), lokasyonun yükünü delta kadar azaltır (Değerlerin sıfırın altına düşmesini engeller).
     * Eğer currentVolume ve currentWeight değerleri maksimum limitlere ulaştıysa lokasyon durumunu otomatik olarak FULL yapar,
     * yük azaldığında tekrar ACTIVE durumuna çeker.
     *
     * @param storageLocationId Lokasyon ID
     * @param volumeDelta       Değişim hacmi (m³)
     * @param weightDelta       Değişim ağırlığı (kg)
     * @param isAddition        Stok girişi ise true, stok çıkışı ise false
     */
    @Transactional
    public void updateLocationLoad(Long storageLocationId, BigDecimal volumeDelta, BigDecimal weightDelta, boolean isAddition) {
        if (storageLocationId == null) {
            throw new IllegalArgumentException("Storage location ID cannot be null");
        }
        if (volumeDelta == null || weightDelta == null) {
            throw new IllegalArgumentException("Volume delta and weight delta cannot be null");
        }
        if (volumeDelta.compareTo(BigDecimal.ZERO) < 0 || weightDelta.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Volume delta and weight delta cannot be negative");
        }

        StorageLocation location = storageLocationRepository.findById(storageLocationId)
                .orElseThrow(() -> new BusinessException("Storage location not found with ID: " + storageLocationId, HttpStatus.NOT_FOUND));

        if (isAddition && location.getStatus() == StorageLocationStatus.BLOCKED) {
            throw new BusinessException(
                    "Cannot add stock to a BLOCKED storage location: " + storageLocationId,
                    HttpStatus.CONFLICT);
        }

        BigDecimal newVolume;
        BigDecimal newWeight;

        if (isAddition) {
            newVolume = location.getCurrentVolume().add(volumeDelta);
            newWeight = location.getCurrentWeight().add(weightDelta);

            if (newVolume.compareTo(location.getMaxVolume()) > 0 || newWeight.compareTo(location.getMaxWeight()) > 0) {
                throw new LocationCapacityExceededException(
                        String.format("Location capacity exceeded. Location ID: %s. Max Volume: %s, Requested Volume: %s. Max Weight: %s, Requested Weight: %s",
                                storageLocationId, location.getMaxVolume(), newVolume, location.getMaxWeight(), newWeight));
            }
        } else {
            newVolume = location.getCurrentVolume().subtract(volumeDelta);
            newWeight = location.getCurrentWeight().subtract(weightDelta);

            // Değerlerin sıfırın altına düşmesini engelle (clamp to zero)
            if (newVolume.compareTo(BigDecimal.ZERO) < 0) {
                newVolume = BigDecimal.ZERO;
            }
            if (newWeight.compareTo(BigDecimal.ZERO) < 0) {
                newWeight = BigDecimal.ZERO;
            }
        }

        location.setCurrentVolume(newVolume);
        location.setCurrentWeight(newWeight);

        // Durum güncelleme:
        // Eğer currentVolume veya currentWeight maksimum limitlere ulaştıysa lokasyon durumunu FULL yap.
        // Yük azaldığında (her ikisi de maksimum limitlerin altındayken) ve eski durum FULL ise ACTIVE durumuna çek.
        // BLOCKED olan durumları otomatik olarak değiştirmemek gerekir çünkü BLOCKED manuel bir durumdur.
        if (newVolume.compareTo(location.getMaxVolume()) >= 0 || newWeight.compareTo(location.getMaxWeight()) >= 0) {
            location.setStatus(StorageLocationStatus.FULL);
        } else if (location.getStatus() == StorageLocationStatus.FULL) {
            location.setStatus(StorageLocationStatus.ACTIVE);
        }

        storageLocationRepository.save(location);
    }
}
