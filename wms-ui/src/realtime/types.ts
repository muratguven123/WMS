export type WmsEventType =
  | "STOCK_CHANGED"
  | "LOCATION_UPDATED"
  | "APPROVAL_CREATED"
  | "APPROVAL_RESOLVED"
  | "INTEGRATION_LOG_UPDATED"
  | "TASK_ASSIGNED"
  | "TASK_PROGRESS"
  | "TASK_COMPLETED"
  | "OPERATOR_STATUS_UPDATED";

export interface DomainEventMessage {
  eventId: string;
  eventType: WmsEventType;
  occurredAt: string;
  companyId: number;
  locationId: number;
  payload: Record<string, unknown>;
}
