# WMS — Warehouse Management System

Microservice-based warehouse management platform with multi-tenant organization support, localization, finance/billing, inbound/outbound operations, and a React operator UI.

## Architecture

| Service | Port | Responsibility |
|---------|------|----------------|
| `wms-core-service` | 8081 | Auth, org, users, dynamic UI, workflow, address master, stock |
| `wms-localization-service` | 8082 | i18n, formats, address templates |
| `wms-finance-service` | 8083 | FX rates, tax engine |
| `wms-billing-service` | 8084 | Multi-currency invoicing |
| `wms-integration-service` | 8085 | ERP adapters / outbox |
| `wms-inbound-service` | 8086 | Receipts & putaway |
| `wms-inventory-service` | 8087 | Inventory & transfers |
| `wms-outbound-service` | 8088 | Picking, packing, shipping |
| `wms-notification-service` | 8089 | Kafka → WebSocket (STOMP) |
| `wms-ui` | 5173 | React + Vite operator console |

Shared library: `wms-common-events`.

## Quick start

```bash
docker compose up -d
```

UI: http://localhost:5173  
Keycloak: http://localhost:8080

## Build

```bash
mvn -q -DskipTests package
cd wms-ui && npm ci && npm run build
```

## Docs

Business requirements live under `business_requirements/`. Phase notes are in `proje_fazlari/`.
