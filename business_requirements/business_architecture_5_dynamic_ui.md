# İş İsteri 5: Dinamik Ekran Alanı ve Yetki Yönetimi
Bu teknik tasarım, LLM modellerinin ekranlardaki alanların (veri giriş kutuları, butonlar vb.) lokasyona, kullanıcı rolüne ve işlem tipine göre dinamik olarak görünürlüğünü, zorunluluğunu ve salt okunurluğunu yönetebileceği bir **Dinamik UI Motoru** (Dynamic UI Engine) oluşturmasını sağlar.

---

## 1. Veri Modeli ve Veri Tabanı Şeması (ERD)

Ekranlar, bu ekranlardaki alanlar ve alanların farklı koşullardaki davranış kurallarını saklamak için tasarlanan veri şemasıdır.

```mermaid
erDiagram
    SCREEN ||--o{ SCREEN_FIELD : "contains"
    SCREEN_FIELD ||--o{ FIELD_BEHAVIOR_RULE : "defines"
    ROLE ||--o{ FIELD_BEHAVIOR_RULE : "applies_to"
    LOCATION ||--o{ FIELD_BEHAVIOR_RULE : "applies_to"
    COMPANY ||--o{ FIELD_BEHAVIOR_RULE : "applies_to"
    COUNTRY ||--o{ FIELD_BEHAVIOR_RULE : "applies_to"

    SCREEN {
        uuid id PK
        string code "e.g. MAT_CARD_FORM, REC_CONTROL_FORM"
        string name
        boolean is_active
    }

    SCREEN_FIELD {
        uuid id PK
        uuid screen_id FK
        string field_key "e.g. tax_number, district, zip_code"
        string default_behavior "e.g. OPTIONAL, HIDDEN, READ_ONLY"
        string data_type "e.g. STRING, NUMBER, DATE, SELECT"
    }

    FIELD_BEHAVIOR_RULE {
        uuid id PK
        uuid screen_field_id FK
        integer priority "rules evaluation order (higher priority overrides)"
        uuid company_id FK "nullable"
        uuid country_id FK "nullable"
        uuid location_id FK "nullable"
        uuid role_id FK "nullable"
        string operation_type "nullable"
        string behavior "e.g. MANDATORY, HIDDEN, READ_ONLY, OPTIONAL"
        string default_value "nullable"
        string validation_regex "regex pattern for validation (nullable)"
        string validation_error_message_key "translation key for regex fail"
        timestamp updated_at
    }
```

### Şema Tasarım Kuralları:
1. **Esnek Koşullar (Flexible Conditions):** `FIELD_BEHAVIOR_RULE` tablosundaki tüm bağlamsal alanlar (`company_id`, `country_id`, `location_id`, `role_id`, `operation_type`) boş bırakılabilir (`nullable`). Boş bırakılan alanlar "Herhangi bir durum/Herkes için geçerli" anlamına gelir.
2. **Öncelik Yönetimi (Priority):** Çakışan kuralları çözmek için `priority` değeri kullanılır (Örn: Ülke seviyesinde "İlçe zorunlu" kuralının önceliği `10` iken, belirli bir lokasyonda "İlçe gizli" kuralının önceliği `20` yapılarak lokasyon kuralının ülke kuralını ezmesi sağlanır).

---

## 2. Dinamik Ekran Şeması Çözümleme Akışı (Runtime Flow)

İstemci (Arayüz) bir formu yüklemek istediğinde arka planda çalışan kural değerlendirme akışıdır:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Arayüz (Frontend - React/Vue/Angular)
    participant API as UI Metadatas Servisi (Backend)
    participant Evaluator as Rule Evaluator Engine
    participant DB as Veri Tabanı (PostgreSQL)

    Client->>API: Form Şemasını Getir (ScreenCode: "REC_CONTROL_FORM") + Context [LocationId, RoleId, OpType]
    
    API->>DB: Ekran Alanlarını ve Kural Listesini Sorgula
    DB-->>API: ScreenFields & FieldBehaviorRules Listesi
    
    API->>Evaluator: Kuralları Değerlendir (Context, Rules)
    
    Note over Evaluator: 1. Gelen Context parametreleri ile eşleşen kuralları filtrele.<br/>2. Alan bazında eşleşen kuralları öncelik sırasına (Priority) göre sırala.<br/>3. En yüksek öncelikli kuralın davranışını ('MANDATORY', 'HIDDEN' vb.) belirle.
    
    Evaluator-->>API: Çözümlenmiş Alan Şeması (Resolved Fields Schema)
    
    API-->>Client: Dinamik Form Şeması (JSON Schema)
    
    Note over Client: Arayüz, gelen JSON şemasına göre alanları çizer.<br/>Örn:<br/>- tax_number: Zorunlu (Regex validasyonlu)<br/>- district: Gizli (Görsel olarak render edilmez)
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **JSON Şeması Standardı (Form Schema Generation):** Backend, çözümlenmiş formu standart bir JSON Schema (veya React Hook Form / Formik ile uyumlu bir JSON yapısı) olarak dönmelidir. Bu sayede frontend üzerinde statik form kodlaması yapılmasına gerek kalmaz.
2. **Çift Yönlü Doğrulama (Server-side Validation):** Dinamik alan kuralları sadece frontend üzerinde görsel olarak çalışmamalıdır; veritabanına kayıt atılırken de (`POST /save-data`) backend aynı kuralları (Örn: `validation_regex` ve `MANDATORY` alanları) tekrar değerlendirmeli ve kurala uymayan verileri `400 Bad Request` ile reddetmelidir.
3. **Kural Çakışması Engelleme (Conflict Prevention):** Admin panelinde yeni kural eklenirken, aynı öncelik değerine (`priority`) sahip çakışan bağlamların oluşması engellenmeli veya sistem varsayılan öncelik hiyerarşisi atamalıdır (Hiyerarşi: Lokasyon > Rol > Şirket > Ülke > Varsayılan).
4. **Çeviri Entegrasyonu:** Alan etiketleri (label) doğrudan statik metin olarak değil, `SCREEN_FIELD` tablosundaki `field_key` değeri `TranslationKey` tablosuyla ilişkilendirilerek dille uyumlu şekilde dönmelidir.
