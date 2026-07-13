package com.wms.localization.integration;

/**
 * Core-service master data varlık kontrolü (MASTER_SELECT ön kontrolü).
 */
public interface CoreMasterDataClient {

    /**
     * Hedef ülkede ilgili master data kaydı var mı?
     *
     * @param countryId ülke id
     * @param source    STATE | CITY | DISTRICT | NEIGHBORHOOD
     * @return true ise en az bir kayıt var; servis erişilemezse true (uyarı üretilmez)
     */
    boolean hasMasterData(Long countryId, String source);
}
