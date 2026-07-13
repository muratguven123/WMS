package com.wms.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Bir depo lokasyonunun (StorageLocation) hacimsel veya ağırlık kapasitesi 
 * aşıldığında fırlatılan özel iş kuralı istisnası.
 */
public class LocationCapacityExceededException extends BusinessException {

    public LocationCapacityExceededException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "LOCATION_CAPACITY_EXCEEDED");
    }
}
