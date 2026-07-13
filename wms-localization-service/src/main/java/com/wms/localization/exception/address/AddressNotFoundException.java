package com.wms.localization.exception.address;


/**
 * İstenen adres kaydı bulunamadığında fırlatılır.
 */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(Long id) {
        super("Address not found: " + id);
    }
}
