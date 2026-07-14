package com.wms.events;

public final class KafkaTopics {

    public static final String STOCK_CHANGED = "wms.inventory.stock.changed";
    public static final String LOCATION_UPDATED = "wms.core.location.updated";
    public static final String APPROVAL_CREATED = "wms.core.approval.created";
    public static final String APPROVAL_RESOLVED = "wms.core.approval.resolved";
    public static final String INTEGRATION_LOG_UPDATED = "wms.integration.log.updated";
    public static final String TASK_ASSIGNED = "wms.outbound.task.assigned";
    public static final String TASK_PROGRESS = "wms.outbound.task.progress";
    public static final String TASK_COMPLETED = "wms.outbound.task.completed";
    public static final String OPERATOR_STATUS_UPDATED = "wms.outbound.operator.status";
    public static final String LOCATION_PROVISIONED = "wms.core.location.provisioned";
    public static final String COMPANY_CHANGED = "wms.core.company.changed";

    private KafkaTopics() {
    }
}
