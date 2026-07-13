package com.wms.integration.adapter.dto;

import lombok.Builder;
import lombok.Value;

/**
 * ERP adaptörlerinden dönen standart yanıt.
 */
@Value
@Builder
public class ErpResponse {

    /** İşlem başarılı mı? */
    boolean success;

    /** ERP sisteminin döndüğü referans/belge numarası */
    String externalReference;

    /** Hata veya bilgi mesajı */
    String message;

    /** HTTP durum kodu veya ERP hata kodu */
    String errorCode;

    // ---- Factory metodlar ------------------------------------------------

    public static ErpResponse success(String externalReference) {
        return ErpResponse.builder()
                .success(true)
                .externalReference(externalReference)
                .build();
    }

    public static ErpResponse success(String externalReference, String message) {
        return ErpResponse.builder()
                .success(true)
                .externalReference(externalReference)
                .message(message)
                .build();
    }

    public static ErpResponse failure(String errorCode, String message) {
        return ErpResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}
