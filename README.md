# WMS (Warehouse Management System)

WMS; çok kiracılı (multi-tenant), mikroservis mimarisinde geliştirilmiş bir depo yönetim platformudur.  
Sistem; organizasyon ve kullanıcı yönetimi, lokalizasyon, finans/faturalama, entegrasyon ve depo operasyon (inbound/inventory/outbound) süreçlerini tek çatı altında sunar.

## Öne Çıkan Yetenekler

- Çoklu şirket/tenant desteği
- Keycloak tabanlı kimlik doğrulama ve yetkilendirme
- Çok dilli arayüz ve yerelleştirme servisleri
- Kur/tax yönetimi ve çok para birimli faturalama
- ERP entegrasyonu ve outbox tabanlı entegrasyon akışı
- Inbound, inventory transfer ve outbound süreç modülleri
- Kafka + STOMP ile gerçek zamanlı bildirim akışı
- React + Vite tabanlı operatör arayüzü

## Mimari

### Backend servisleri

| Servis | Port | Sorumluluk |
|---|---:|---|
| `wms-core-service` | 8081 | Kimlik/tenant, organizasyon, kullanıcı, dinamik UI, workflow, adres ve çekirdek alanlar |
| `wms-localization-service` | 8082 | Çeviri, format yönetimi, adres şablonları |
| `wms-finance-service` | 8083 | Kur oranları, vergi hesaplama altyapısı |
| `wms-billing-service` | 8084 | Çok para birimli fatura süreçleri |
| `wms-integration-service` | 8085 | Dış sistem/ERP entegrasyonları ve outbox işleri |
| `wms-inbound-service` | 8086 | Mal kabul ve yerleştirme (ops profile) |
| `wms-inventory-service` | 8087 | Stok hareketleri ve transfer (ops profile) |
| `wms-outbound-service` | 8088 | Toplama, çıkış ve sevkiyat (ops profile) |
| `wms-notification-service` | 8089 | Kafka olaylarının WebSocket/STOMP’a yayınlanması (ops profile) |

Ortak olay kütüphanesi: `wms-common-events`

### Frontend

- `wms-ui` (React + TypeScript + Vite)
- Varsayılan erişim: `http://localhost:5173`

## Teknoloji Yığını

- Java 21 + Spring Boot 3
- Maven (multi-module)
- PostgreSQL, Redis, Kafka
- Keycloak
- React 19 + TypeScript + Vite
- Docker Compose

## Hızlı Başlangıç (Docker Compose)

### Varsayılan stack

```bash
docker compose up -d
```

Bu modda temel servisler (core/localization/finance/billing/integration/ui vb.) ayağa kalkar.

### Operasyon servisleri ile birlikte

```bash
docker compose --profile ops up -d
```

Bu profile inbound, inventory, outbound ve notification servislerini de ekler.

### Sık kullanılan adresler

- UI: `http://localhost:5173`
- Keycloak: `http://localhost:8080`
- pgAdmin: `http://localhost:5050`

## Yerel Geliştirme

### Backend

Tüm backend modüllerini doğrulamak için:

```bash
mvn -B -ntp -P ops verify
```

Tek bir servisi çalıştırmak için:

```bash
mvn spring-boot:run -pl wms-core-service
```

Alternatif olarak:

- PowerShell: `./run-service.ps1 wms-core-service`
- CMD: `run-service.cmd wms-core-service`

### Frontend

```bash
cd wms-ui
npm ci
npm run dev
```

Diğer komutlar:

- `npm run lint`
- `npm run build`

## Dizin Yapısı (Özet)

- `/wms-*-service`: Domain bazlı mikroservisler
- `/wms-common-events`: Ortak event sözleşmeleri
- `/wms-ui`: Operatör arayüzü
- `/docker`: Ortam ve altyapı konfigürasyonları
- `/business_requirements`: İş gereksinimi dokümanları
- `/proje_fazlari`: Faz planları ve uygulama notları

## Dokümantasyon

- İş gereksinimleri: `business_requirements/`
- Faz dokümanları: `proje_fazlari/`
