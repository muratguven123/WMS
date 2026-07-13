package com.wms.core.entity.enums;

/**
 * Bir {@link com.wms.core.entity.ApprovalRequest} kaydının yaşam döngüsü durumları.
 */
public enum ApprovalStatus {

    /** Onay bekleniyor — işlem askıya alınmış. */
    PENDING_APPROVAL,

    /** Yetkili tarafından onaylandı — işlem devam edebilir. */
    APPROVED,

    /** Yetkili tarafından reddedildi — işlem iptal edildi. */
    REJECTED
}
