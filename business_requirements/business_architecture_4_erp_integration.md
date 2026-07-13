# İş İsteri 4: ERP / Muhasebe / Finans Entegrasyon Altyapısı
Bu teknik tasarım, LLM modellerinin farklı lokasyonlardaki farklı ERP sistemleriyle (SAP, Oracle, Logo vb.) esnek, hata toleranslı ve izlenebilir bir entegrasyon kurmasını sağlayan mimariyi tanımlar.

---

## 1. Yazılım Tasarım Deseni: Adaptör Deseni (Adapter Pattern)

Sistemin tek bir ERP'ye bağımlı kalmaması ve lokasyon bazlı farklı entegrasyon adaptörlerini dinamik olarak çağırabilmesi için **Adapter Pattern** yapısı uygulanır.

```mermaid
classDiagram
    class IErpAdapter {
        <<interface>>
        +sendMaterialCard(MaterialData data) Task~ErpResponse~
        +sendInventoryMovement(MovementData data) Task~ErpResponse~
        +sendInvoice(InvoiceData data) Task~ErpResponse~
        +fetchExchangeRates() Task~List~ExchangeRate~~
    }

    class SapAdapter {
        -SapClient _client
        +sendMaterialCard(MaterialData data) Task~ErpResponse~
        +sendInventoryMovement(MovementData data) Task~ErpResponse~
    }

    class LogoAdapter {
        -SftpClient _sftp
        +sendMaterialCard(MaterialData data) Task~ErpResponse~
        +sendInventoryMovement(MovementData data) Task~ErpResponse~
    }

    class CustomAdapter {
        -RestHttpClient _httpClient
        +sendMaterialCard(MaterialData data) Task~ErpResponse~
        +sendInventoryMovement(MovementData data) Task~ErpResponse~
    }

    class ErpAdapterFactory {
        +getAdapter(LocationId locationId) IErpAdapter
    }

    class IntegrationEngine {
        -ErpAdapterFactory _factory
        +processOutboxJob(OutboxMessage message)
    }

    IErpAdapter <|.. SapAdapter : implements
    IErpAdapter <|.. LogoAdapter : implements
    IErpAdapter <|.. CustomAdapter : implements
    ErpAdapterFactory ..> IErpAdapter : creates
    IntegrationEngine --> ErpAdapterFactory : uses
```

---

## 2. Entegrasyon Veri Tabanı Şeması (ERD)

Entegrasyonların yapılandırılması, loglanması ve yeniden deneme (retry) süreçlerinin takibi için kullanılan veri tabanı şemasıdır.

```mermaid
erDiagram
    LOCATION ||--o{ LOCATION_INTEGRATION_CONFIG : "has"
    INTEGRATION_SYSTEM ||--o{ LOCATION_INTEGRATION_CONFIG : "configured_by"
    LOCATION_INTEGRATION_CONFIG ||--o{ INTEGRATION_LOG : "generates"
    INTEGRATION_JOB ||--o{ INTEGRATION_LOG : "logs"

    INTEGRATION_SYSTEM {
        uuid id PK
        string code "e.g. SAP, ORACLE, LOGO, MIKRO"
        string name
        boolean is_active
    }

    LOCATION_INTEGRATION_CONFIG {
        uuid id PK
        uuid location_id FK
        uuid integration_system_id FK
        string connection_type "e.g. REST, SOAP, SFTP, DB"
        jsonb connection_params "stores URLs, credentials, paths"
        boolean is_active
        timestamp updated_at
    }

    INTEGRATION_JOB {
        uuid id PK
        string code "e.g. MAT_SYNC, STOCK_MOVE, INVOICE_SYNC"
        string name
        string direction "e.g. INBOUND, OUTBOUND"
    }

    INTEGRATION_LOG {
        uuid id PK
        uuid location_integration_config_id FK
        uuid integration_job_id FK
        string status "e.g. SUCCESS, FAILED, RETRYING"
        text request_payload "sent payload details"
        text response_payload "response details from ERP"
        text error_message
        integer retry_count
        timestamp created_at
        timestamp last_attempt_at
    }
```

---

## 3. Asenkron Entegrasyon Akışı: Outbox Pattern

İşlem anında WMS uygulamasının performansının düşmemesi ve ERP sisteminin kesintili olduğu durumlarda veri kaybı yaşanmaması için **Outbox Pattern** uygulanır.

```mermaid
sequenceDiagram
    autonumber
    actor Picker as Depo Görevlisi
    participant WMS as WMS Core Application
    participant DB as Veri Tabanı (Outbox Table)
    participant Worker as Integration Outbox Worker
    participant Factory as Adapter Factory
    participant ERP as Dış ERP Sistemi (Örn: SAP)

    Picker->>WMS: Raf Yerleştirme Onayla (Stok Hareketi)
    WMS->>DB: Stok Hareketini Kaydet & Outbox Tablosuna İş Kaydı At (Atomik Transaction)
    DB-->>WMS: Transaction Başarılı
    WMS-->>Picker: İşlem Tamamlandı (Hızlı Geri Bildirim)

    Note over Worker: Background Worker her 5 saniyede<br/>Outbox tablosundaki "PENDING" kayıtları okur.

    Worker->>DB: "PENDING" Entegrasyon Kayıtlarını Oku
    DB-->>Worker: Liste (Örn: Lokasyon X'in Stok Hareketi)
    
    Worker->>Factory: GetAdapter(Location X)
    Factory-->>Worker: SapAdapter (REST API tabanlı adaptör)
    
    Worker->>ERP: SapAdapter.sendInventoryMovement(data)
    
    alt Entegrasyon Başarılı
        ERP-->>Worker: 200 OK / Success
        Worker->>DB: Log Durumunu "SUCCESS" olarak güncelle & Outbox'tan sil/arşivle
    else Entegrasyon Hatası (ERP Kapalı vb.)
        ERP-->>Worker: 500 Error / Timeout
        Worker->>DB: Log Durumunu "FAILED", hata mesajını kaydet, retry_count'u artır.
    end
```

---

## 4. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Gevşek Bağlılık (Decoupling):** WMS veritabanındaki stok hareketleri tablosu doğrudan ERP tablolarıyla ilişkili olmamalıdır. Veri alışverişi sadece DTO'lar (Data Transfer Objects) ve adaptör arayüzü (`IErpAdapter`) üzerinden yapılmalıdır.
2. **Hata ve Yeniden Deneme (Retry Mechanism):** Entegrasyon hatalarında, sistem log durumunu `FAILED` olarak işaretlemelidir. Admin panelinde bu hatalı kayıtlar için bir "Yeniden Gönder" (Resend/Retry) butonu bulunmalı ve tıklandığında ilgili outbox kaydı tekrar tetiklenmelidir. Ayrıca geçici ağ hataları için 3 kez otomatik yeniden deneme (exponential backoff) mekanizması bulunmalıdır.
3. **Payload Arşivleme:** Gönderilen verinin birebir kopyası (`request_payload`) ve alınan hata/başarı mesajı (`response_payload`) ileride yaşanabilecek uyuşmazlıkların denetimi (Audit/Traceability) için metin olarak veri tabanında saklanmalıdır.
4. **Yeni Adaptör Ekleme Kolaylığı:** Yeni bir ERP sistemi entegre edilmek istendiğinde (`IErpAdapter` arayüzünü implement eden yeni bir sınıf oluşturulup factory'e eklendiğinde) mevcut WMS kodlarında ve diğer adaptörlerin yapısında hiçbir değişiklik olmamalıdır (Open/Closed Principle).
