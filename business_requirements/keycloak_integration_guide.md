# WMS — Keycloak (OAuth2 Resource Server & JWT) Entegrasyon Rehberi

> Faz: Güvenlik Altyapısı Modernizasyonu
> Kapsam: 7 mikroservis (core, inventory, outbound, finance, billing, integration, localization)
> Keycloak sürümü: **26.6.x** (Haziran 2026 itibarıyla en güncel kararlı sürüm)
> Strateji: Core-service → DB destekli tam doğrulama (UserAccess) · Diğer servisler → JWT-stateless

---

## AŞAMA 1 — Docker Compose: Keycloak Servisi

Mevcut `docker-compose.yml` dosyanıza aşağıdaki servisi ekleyin. Keycloak, yerel PostgreSQL
sunucusundaki `keycloak_db` veritabanını kullanır ve yönetim paneli `8080` portundan erişilir.

```yaml
services:
  # ... mevcut servisleriniz ...

  keycloak:
    image: quay.io/keycloak/keycloak:26.6
    container_name: wms-keycloak
    command: start-dev          # Çalışma ortamı — production'da "start" + TLS kullanın
    ports:
      - "8080:8080"
    environment:
      # --- Yönetici hesabı (çalışma ortamı) ---
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      # --- PostgreSQL bağlantısı ---
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://host.docker.internal:5432/keycloak_db
      KC_DB_USERNAME: postgres
      KC_DB_PASSWORD: postgres
      # --- Ana bilgisayar ayarları (dev) ---
      KC_HOSTNAME: localhost
      KC_HTTP_ENABLED: "true"
      KC_HEALTH_ENABLED: "true"
    extra_hosts:
      - "host.docker.internal:host-gateway"   # Linux'ta host PostgreSQL erişimi için
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/127.0.0.1/9000 && echo -e 'GET /health/ready HTTP/1.1\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
      interval: 15s
      timeout: 5s
      retries: 10
    restart: unless-stopped
```

Veritabanını önceden oluşturun:

```sql
CREATE DATABASE keycloak_db;
```

> **Not:** PostgreSQL'iniz de aynı compose dosyasında konteyner olarak çalışıyorsa
> `KC_DB_URL` içindeki `host.docker.internal` yerine PostgreSQL servisinin adını yazın
> (örn. `jdbc:postgresql://wms-postgres:5432/keycloak_db`) ve `depends_on` ekleyin.
> Keycloak 26.x'te eski `KEYCLOAK_ADMIN` değişkenlerinin yerini `KC_BOOTSTRAP_ADMIN_*` almıştır.

---

## AŞAMA 1.5 — Keycloak Yönetim Paneli Kurulum Rehberi

`http://localhost:8080` → `admin / admin` ile giriş yapın.

### 1. Realm oluşturma
Sol üst köşedeki realm seçiciden **Create realm** → `wms-realm` → **Create**.

### 2. Client oluşturma (`wms-api`)
**Clients → Create client**

| Alan | Değer |
|---|---|
| Client type | OpenID Connect |
| Client ID | `wms-api` |
| Client authentication | **On** (confidential) |
| Authentication flow | Standard flow ✔, Direct access grants ✔ (dev/test için) |
| Valid redirect URIs | `http://localhost:*` (frontend'e göre daraltın) |
| Web origins | `+` |

> Frontend (SPA) için ayrıca `wms-frontend` adında **public** bir client oluşturup
> Direct access grants yerine PKCE'li Standard flow kullanmanız önerilir.

### 3. Realm rolleri
**Realm roles → Create role** ile aşağıdaki rolleri oluşturun
(JWT'de `realm_access.roles` altında taşınırlar):

| Rol | Açıklama |
|---|---|
| `WMS_ADMIN` | Tüm sistem yönetimi |
| `WAREHOUSE_MANAGER` | Depo yöneticisi — operasyonel onaylar, konfigürasyon |
| `INBOUND_CLERK` | Mal kabul operatörü |
| `INVENTORY_CLERK` | Envanter operatörü |
| `PICKER` | Sipariş toplama operatörü |
| `PACKER` | Paketleme operatörü |
| `SHIPPING_CLERK` | Sevkiyat operatörü |
| `FINANCE_USER` | Finans görüntüleme/işlem |
| `FINANCE_MANAGER` | Finans yönetimi (kur, vergi, kontrat) |
| `INTEGRATION_ADMIN` | ERP entegrasyon izleme/yönetim |
| `LOCALIZATION_ADMIN` | Çeviri ve format yönetimi |

> Client-scoped rol tercih ederseniz **Clients → wms-api → Roles** altında oluşturun;
> `KeycloakJwtAuthenticationConverter` her iki kaynağı da (`realm_access` + `resource_access.wms-api`) okur.

### 4. Custom claim: `wms_user_id` (opsiyonel ama önerilir)
Stateless servislerin JWT'den yerel kullanıcı PK'sini okuyabilmesi için:

1. **Users → (kullanıcı) → Attributes** → key: `wms_user_id`, value: `<users.id UUID>`
2. **Client scopes → Create client scope** → `wms-claims` (Default açık)
3. `wms-claims` → **Mappers → Add mapper → By configuration → User Attribute**:
   - Name: `wms_user_id`, User Attribute: `wms_user_id`, Token Claim Name: `wms_user_id`
   - Claim JSON Type: `String`, Add to access token: **On**
4. **Clients → wms-api → Client scopes → Add client scope** → `wms-claims` (Default)

### 5. Kullanıcı oluşturma ve WMS eşleştirmesi
1. **Users → Create new user** → username/email doldurun → **Credentials** sekmesinden şifre atayın (Temporary: Off).
2. **Role mapping** sekmesinden realm rollerini atayın.
3. Kullanıcı detayındaki **ID** değerini (UUID) kopyalayın — bu, JWT'nin `sub` claim'idir.
4. WMS veritabanında eşleştirin:

```sql
UPDATE users
SET keycloak_user_id = '<keycloak-user-uuid>'
WHERE email = 'kullanici@firma.com';
```

### 6. Token alma (test)

```bash
curl -X POST "http://localhost:8080/realms/wms-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=wms-api" \
  -d "client_secret=<Clients → wms-api → Credentials>" \
  -d "username=depo_yoneticisi" \
  -d "password=sifre"
```

Dönen `access_token` ile API çağrısı:

```bash
curl "http://localhost:8081/api/stocks" \
  -H "Authorization: Bearer <access_token>" \
  -H "X-Active-Company-ID: <company-uuid>" \
  -H "X-Active-Location-ID: <location-uuid>"
```

---

## AŞAMA 2 — Maven Bağımlılıkları ve application.yml

### pom.xml (7 servise de eklendi)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

> **Parent pom alternatifi:** Bağımlılıklar sürüm yönetimi Spring Boot BOM'dan geldiği için
> her servisin kendi `pom.xml`'ine eklendi. Dilerseniz parent `pom.xml`'in
> `<dependencyManagement>` bölümüne taşıyıp servislerde sürümsüz referans verebilirsiniz.

### application.yml (7 serviste de eklendi)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/wms-realm}
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://localhost:8080/realms/wms-realm/protocol/openid-connect/certs}

# Client rollerinin okunacağı client-id (SecurityConfig içinde kullanılır)
wms:
  security:
    keycloak:
      client-id: wms-api
```

> Ortam değişkeni ile override edilebilir — docker profilinde issuer
> `http://keycloak:8080/...` olarak ayarlanır (bkz. inventory `application-docker.yml`).
> **Önemli:** `issuer-uri` token içindeki `iss` claim'i ile birebir aynı olmalıdır.

---

## AŞAMA 3 — Güvenlik Mimarisi ve TenantContextFilter

### Mimari karar: hibrit doğrulama

| Servis | Strateji | Gerekçe |
|---|---|---|
| **wms-core-service** | JWT + **DB doğrulama** | `users` ve `user_accesses` tablolarının sahibi. `sub` → `keycloak_user_id` eşleştirmesi ve company/location üyelik kontrolü burada yapılır. |
| Diğer 6 servis | **JWT-stateless** | UserAccess tablosuna sahip değiller. İmzası doğrulanmış JWT + tenant header'ları yeterlidir; DB round-trip yok. |

### İstek akışı (core-service)

```
İstek → BearerTokenAuthenticationFilter (Spring OAuth2 RS)
          ├─ JWT imza doğrulama (JWK Set — Keycloak public key)
          ├─ issuer + expiry kontrolü
          └─ KeycloakJwtAuthenticationConverter → ROLE_* authorities
       → TenantContextFilter
          ├─ sub (Keycloak User ID) → users.keycloak_user_id eşleştirme
          ├─ X-Active-Company-ID / X-Active-Location-ID header doğrulama
          ├─ UserAccess.hasAccess(userId, companyId, locationId) kontrolü
          └─ TenantContextHolder.setContext(...) — ThreadLocal
       → @PreAuthorize (method security) → Controller
       → finally: TenantContextHolder.clear()
```

### Değişen / eklenen dosyalar

| Dosya | Değişiklik |
|---|---|
| `core/entity/User.java` | `keycloakUserId` alanı + unique constraint eklendi |
| `core/repository/UserRepository.java` | `findByKeycloakUserId()` eklendi |
| `core/security/KeycloakJwtAuthenticationConverter.java` | **Yeni** — realm/client rolleri → `ROLE_*` |
| `core/security/TenantContextFilter.java` | JWT `sub` → yerel kullanıcı → UserAccess akışına refactor edildi |
| `core/config/SecurityConfig.java` | `oauth2ResourceServer(jwt)` + filter zinciri |
| `core/security/JwtAuthenticationFilter.java` | **Silindi** — sahte UUID-token filtresi, yerini Spring OAuth2 RS aldı |
| Diğer 6 servis | `security/` paketi (Converter, TenantContext, Holder, Filter) + `config/SecurityConfig` eklendi |
| `localization/.../TenantContextFilter.java` | `X-User-ID` header'ı kaldırıldı — kimlik artık JWT'den |

### Veritabanı migration

`ddl-auto: update` kolonu otomatik ekler; kontrollü ortamlar için:

```sql
ALTER TABLE users ADD COLUMN keycloak_user_id VARCHAR(36);
ALTER TABLE users ADD CONSTRAINT uk_user_keycloak_id UNIQUE (keycloak_user_id);
```

---

## AŞAMA 4 — Endpoint Koruma Matrisi (@PreAuthorize)

`@EnableMethodSecurity` tüm servislerin `SecurityConfig`'inde aktif. Uygulanan kurallar:

### wms-core-service (8081)

| Controller | Kural |
|---|---|
| `InboundWorkflowController` `/api/inbound` | `INBOUND_CLERK, WAREHOUSE_MANAGER, WMS_ADMIN` |
| `StockController` `/api/stocks` | `INVENTORY_CLERK, WAREHOUSE_MANAGER, WMS_ADMIN` |
| `TransferController` `/api/transfers` | `INVENTORY_CLERK, WAREHOUSE_MANAGER, WMS_ADMIN` |
| `StorageLocationController` `/api/locations` | `WAREHOUSE_MANAGER, WMS_ADMIN` |
| `ApprovalController` `/api/approvals` | `WAREHOUSE_MANAGER, WMS_ADMIN` |
| `ProcessConfigController`, `WorkflowController` | `WAREHOUSE_MANAGER, WMS_ADMIN` |
| `UiRuleManagementController`, `ReceiptDemoController` | `WMS_ADMIN` |
| `DynamicUiController`, `AddressController` | authenticated (rol kısıtı yok — referans veri/UI) |

### Diğer servisler

| Servis | Controller | Kural |
|---|---|---|
| inventory (8087) | `InventoryController` | `INVENTORY_CLERK, WAREHOUSE_MANAGER, WMS_ADMIN` — `/count/adjust` yalnızca `WAREHOUSE_MANAGER, WMS_ADMIN` (method-level override) |
| outbound (8088) | `PickingController` | `PICKER, WAREHOUSE_MANAGER, WMS_ADMIN` |
| outbound | `PackingController` | `PACKER, WAREHOUSE_MANAGER, WMS_ADMIN` |
| outbound | `ShippingController` | `SHIPPING_CLERK, WAREHOUSE_MANAGER, WMS_ADMIN` |
| finance (8083) | `ContractController`, `TaxRateController` | `FINANCE_MANAGER, WMS_ADMIN` |
| finance | `ExchangeRate`, `CurrencyRateLookup`, `FinancialTransaction`, `Order` | `FINANCE_USER, FINANCE_MANAGER, WMS_ADMIN` |
| integration (8085) | 4 controller | `INTEGRATION_ADMIN, WMS_ADMIN` |
| localization (8082) | `TranslationImportExport` | `LOCALIZATION_ADMIN, WMS_ADMIN` |
| localization | `FormatConfig`, `Language`, `Translation`, `Address` | authenticated (referans veri — frontend format çözümlemesi tüm kullanıcılara açık) |
| billing (8084) | Controller yok — altyapı hazır | — |

### Kullanım kalıpları

```java
// Class-level — tüm endpoint'ler için varsayılan
@PreAuthorize("hasAnyRole('PICKER', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
@RestController
@RequestMapping("/api/picking")
public class PickingController { ... }

// Method-level — hassas işlem için class kuralını override eder
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
@PostMapping("/count/adjust")
public ResponseEntity<Void> adjustStock(...) { ... }
```

> `hasRole('X')` ifadesi `ROLE_X` authority'sini arar; `KeycloakJwtAuthenticationConverter`
> Keycloak rollerini `ROLE_` prefix'i ve UPPER_CASE ile map ettiği için Keycloak'ta rol
> adlarını prefix'siz tanımlamanız yeterlidir (örn. `WAREHOUSE_MANAGER`).

---

## Doğrulama Senaryoları

| Senaryo | Beklenen |
|---|---|
| Token'sız istek | `401` — `WWW-Authenticate: Bearer` |
| Geçersiz/expired token | `401` — invalid_token |
| Geçerli token, WMS'de eşleşmeyen `sub` (core) | `403` — "not provisioned in WMS" |
| Tenant header eksik | `400` — missing header |
| UserAccess'te olmayan company/location (core) | `403` — access denied |
| Rolü yetersiz kullanıcı | `403` — Access Denied (method security) |
| Doğru rol + doğru tenant | `200` |

## Production Notları

1. `start-dev` yerine `start` + TLS sertifikası; `KC_HOSTNAME` gerçek domain.
2. `admin/admin` yerine güçlü parola; bootstrap sonrası ayrı yönetici hesabı oluşturup bootstrap hesabını devre dışı bırakın.
3. Access token lifespan: 5-15 dk (Realm settings → Tokens); refresh token rotasyonu açık.
4. `issuer-uri` üzerinden JWK cache'lenir; Keycloak key rotation otomatik çalışır.
5. Servisler arası çağrılar için `client_credentials` grant'lı ayrı bir service-account client'ı (`wms-service`) tanımlayın.
