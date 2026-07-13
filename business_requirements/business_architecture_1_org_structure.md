# İş İsteri 1: Organizasyonel Yapı ve Çoklu Lokasyon Mimarisi
Bu teknik tasarım, LLM modellerinin doğrudan kod üretebileceği düzeyde detaylandırılmış veri modellerini, iş kurallarını ve veri akış diyagramlarını içermektedir.

---

## 1. Veri Modeli ve Veri Tabanı Şeması (Entity Relationship Diagram - ERD)

Çoklu firma ve lokasyon yapısının esnek ve genişletilebilir olması için hiyerarşik bir veritabanı şeması tasarlanmıştır.

```mermaid
erDiagram
    ORGANIZATION ||--o{ COMPANY : "has"
    COMPANY ||--o{ LOCATION : "has"
    COUNTRY ||--o{ REGION : "has"
    REGION ||--o{ LOCATION : "has"
    LOCATION ||--o{ ZONE : "has"
    
    USER ||--o{ USER_ACCESS : "has"
    COMPANY ||--o{ USER_ACCESS : "authorizes"
    LOCATION ||--o{ USER_ACCESS : "authorizes"
    ROLE ||--o{ USER_ACCESS : "assigned_to"

    TRANSACTION_LOG }o--|| COMPANY : "associated_with"
    TRANSACTION_LOG }o--|| LOCATION : "associated_with"
    TRANSACTION_LOG }o--|| USER : "performed_by"

    ORGANIZATION {
        uuid id PK
        string name
        boolean is_active
        timestamp created_at
    }

    COMPANY {
        uuid id PK
        uuid organization_id FK
        string name
        string tax_number
        string tax_office
        timestamp created_at
    }

    COUNTRY {
        uuid id PK
        string iso_code "e.g. TR, DE, US"
        string name
    }

    REGION {
        uuid id PK
        uuid country_id FK
        string name "e.g. Marmara, Bayern, California"
    }

    LOCATION {
        uuid id PK
        uuid company_id FK
        uuid region_id FK
        string name "e.g. Istanbul Depo, Frankfurt Depo"
        string type "e.g. Central, Transit, Virtual"
        string timezone "e.g. Europe/Istanbul"
        boolean is_active
    }

    ZONE {
        uuid id PK
        uuid location_id FK
        string name "e.g. Zone A, Raf C1"
        string type "e.g. ColdRoom, Quarantine, Standard"
    }

    USER {
        uuid id PK
        string username
        string email
        string password_hash
        boolean is_active
    }

    ROLE {
        uuid id PK
        string name "e.g. WarehouseManager, Picker, Admin"
        jsonb permissions
    }

    USER_ACCESS {
        uuid id PK
        uuid user_id FK
        uuid company_id FK
        uuid location_id FK "nullable (null means access to all locations under company)"
        uuid role_id FK
    }

    TRANSACTION_LOG {
        uuid id PK
        uuid company_id FK
        uuid location_id FK
        uuid user_id FK
        string action_type "e.g. STOCK_IN, PICKING"
        jsonb payload
        timestamp created_at_utc
    }
```

### Şema Tasarım Kuralları:
1. **Yumuşak Silme (Soft Delete):** `is_active` alanı tüm ana tablolarda bulunmalı, fiziksel silme yapılmamalıdır.
2. **Denetim İzi (Audit Log):** Her kritik işlem kaydında (`TRANSACTION_LOG`) işlemin gerçekleştiği `company_id` ve `location_id` zorunlu (NOT NULL) olarak tutulmalıdır.
3. **Esnek Hiyerarşi:** `USER_ACCESS` tablosundaki `location_id` alanı boş (`null`) bırakılabilir. Eğer boş bırakılırsa, kullanıcı o şirkete ait tüm lokasyonlarda (depolarda) işlem yapma yetkisine sahip olur.

---

## 2. Teknik Akış ve Veri İzolasyon Mimarisi (Data Flow)

Kullanıcının sisteme giriş yapmasından veriye erişmesine kadar geçen süreçte **Çoklu Lokasyon İzolasyonu** (Multi-Location Isolation) aşağıdaki gibi uygulanır:

```mermaid
sequenceDiagram
    autonumber
    actor User as Kullanıcı (İstemci)
    participant Auth as Kimlik Doğrulama (Auth Service)
    participant Mid as Context Middleware (API Gateway)
    participant DB as Veri Tabanı (ORM / PostgreSQL)

    User->>Auth: Giriş Yap (Username, Password)
    Auth->>User: JWT Token Dön (Yetkili olduğu Şirket/Lokasyon listesi ile)
    
    Note over User: Kullanıcı işlem yapmak istediği<br/>Aktif Lokasyonu (Location Context) seçer.
    
    User->>Mid: API İsteği (GET /stocks) + Header [X-Active-Location-ID, X-Active-Company-ID]
    
    Mid->>Mid: Token ve Header Yetki Karşılaştırması yap.<br/>(Kullanıcı bu lokasyona yetkili mi?)
    alt Yetki Yok
        Mid-->>User: 403 Forbidden
    else Yetki Var
        Mid->>DB: Global Query Filter uygula:<br/>"SELECT * FROM stocks WHERE company_id = X AND location_id = Y"
        DB-->>Mid: Filtrelenmiş Veri Seti
        Mid-->>User: JSON Veri (Sadece yetkili olunan veriler)
    end
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Context Injector (Bağlam Enjektörü):** API katmanında her istek geldiğinde, HTTP Header'dan gelen `X-Active-Location-ID` ve `X-Active-Company-ID` bilgileri okunmalı ve isteğe özel bir thread-local context'e (veya request context) aktarılmalıdır.
2. **Global Query Filter (Küresel Sorgu Filtresi):** ORM (Entity Framework, Prisma, Hibernate vb.) seviyesinde, işlem gören tablolarda (Örn: Stok, Sipariş, Sayım) `company_id` ve `location_id` alanları için otomatik filtre uygulanmalıdır. Geliştirici manuel olarak `WHERE` koşulu yazmak zorunda kalmamalıdır.
3. **İşlem Kayıtlarında Loglama (Transaction Tracking):** Sisteme kaydedilen her stok hareketi veya sipariş kaydında, aktif kullanıcı ID'sinin yanı sıra aktif `company_id` ve `location_id` veri tabanına zorunlu alan olarak yazılmalıdır.
4. **Çapraz Depo Transferi İstisnası (Cross-Warehouse Transfer):** Depolar arası transferlerde, kaynak depo ve hedef depo olmak üzere iki farklı lokasyon bulunur. Bu özel senaryoda, kullanıcının hem kaynak hem de hedef depoya yetkisi olması gerektiği API katmanında doğrulanmalıdır.
