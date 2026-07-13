# İş İsteri 3: Lokasyon Bazlı Parametrik Süreç Konfigürasyonu
Bu teknik tasarım, LLM modellerinin yazılım geliştirmeye (kod değişikliğine) gerek kalmadan depo iş akışı adımlarını (Mal Kabul, Kalite Kontrol, Paketleme vb.) lokasyon bazlı parametrik olarak çalıştırabilen bir iş akışı motoru (Workflow Engine) tasarlamasını sağlar.

---

## 1. Veri Modeli ve Veri Tabanı Şeması (ERD)

İş akışı adımlarının dinamik olarak sıralanması, zorunlu/opsiyonel durumları ve onay mekanizmaları için aşağıdaki şema tasarlanmıştır.

```mermaid
erDiagram
    PROCESS_DEF ||--o{ PROCESS_STEP_DEF : "defines"
    LOCATION ||--o{ LOCATION_PROCESS_CONFIG : "has"
    PROCESS_DEF ||--o{ LOCATION_PROCESS_CONFIG : "configured_by"
    LOCATION_PROCESS_CONFIG ||--o{ LOCATION_PROCESS_STEP_CONFIG : "contains"
    PROCESS_STEP_DEF ||--o{ LOCATION_PROCESS_STEP_CONFIG : "overrides"
    ROLE ||--o{ LOCATION_PROCESS_STEP_CONFIG : "responsible_role"

    PROCESS_DEF {
        uuid id PK
        string code "e.g. INBOUND, OUTBOUND, COUNTING"
        string name
        boolean is_active
    }

    PROCESS_STEP_DEF {
        uuid id PK
        uuid process_def_id FK
        string code "e.g. QC, SERIAL_CONTROL, CUSTOMS_CONTROL"
        string name
        integer default_sequence
    }

    LOCATION_PROCESS_CONFIG {
        uuid id PK
        uuid location_id FK
        uuid process_def_id FK
        boolean is_active
        timestamp updated_at
    }

    LOCATION_PROCESS_STEP_CONFIG {
        uuid id PK
        uuid location_process_config_id FK
        uuid process_step_def_id FK
        integer sequence "custom order for this location"
        boolean is_active "is this step enabled?"
        boolean is_mandatory "can this step be skipped?"
        uuid responsible_role_id FK "nullable"
        boolean requires_approval "does it need manager approval?"
        string error_strategy "e.g. BLOCK, BYPASS, ROUTE_TO_QUARANTINE"
        timestamp updated_at
    }
```

### Şema Tasarım Kuralları:
1. **Sıralama Garantisi (Sequence Control):** `LOCATION_PROCESS_STEP_CONFIG` tablosunda `location_process_config_id` ve `sequence` alanları üzerinde benzersizlik kontrolü (Unique Constraint) veya dinamik bir doğrulama bulunmalıdır; aynı iş akışında iki adım aynı sırada olamaz.
2. **Hata Stratejileri (Error Strategy):** `error_strategy` alanı, bir adımda hata veya tolerans dışı durum oluştuğunda sistemin ne yapacağını tanımlar (`BLOCK`: Süreci kilitle, `ROUTE_TO_QUARANTINE`: Malı karantinaya yönlendir ve devam et, `BYPASS`: Uyarı verip devam et).

---

## 2. Dinamik Süreç Doğrulama Akışı (Runtime Flow)

İşlem anında (Örn: Mal Kabul esnasında) sistemin lokasyon bazlı kuralları nasıl sorguladığı ve doğruladığı aşağıdaki süreç akışında gösterilmiştir:

```mermaid
sequenceDiagram
    autonumber
    actor Picker as Depo Görevlisi (Terminal/UI)
    participant Engine as Workflow Validator (Backend)
    participant Cache as Redis Cache
    participant DB as Veri Tabanı (PostgreSQL)

    Picker->>Engine: Adım Tamamlama İsteği (Örn: Mal Kabul Kaydı yapıldı)
    
    Engine->>Cache: Lokasyon İş Akış Konfigürasyonunu Getir (location_id, process_code: "INBOUND")
    
    alt Cache Boş (Cache Miss)
        Engine->>DB: Aktif Lokasyon Süreç ve Adım Konfigürasyonunu Sorgula
        DB-->>Engine: Konfigürasyon Verisi
        Engine->>Cache: Konfigürasyonu Cache'e yaz (TTL: 1h)
    end
    
    Engine->>Engine: Sonraki Adımı Belirle<br/>(Sequence sırasına göre en küçük aktif adım)
    
    Note over Engine: Sonraki aktif adım "QC" (Kalite Kontrol) ve "is_mandatory = true" olarak ayarlanmış.

    alt Sonraki Adım Zorunlu (QC)
        Engine-->>Picker: "Kalite Kontrol Adımına geçmeniz gerekmektedir." yönlendirmesi yap.
    else Sonraki Adım Opsiyonel ve Atlandıysa
        Engine->>Engine: Sonraki adımı (Örn: Raf Yerleştirme) tetikle.
        Engine-->>Picker: "Raf Yerleştirme Adımına geçiniz." yönlendirmesi yap.
    end
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Yazılımsız Akış Değişikliği (No-Code Flow Customization):** Lokasyon adminleri, kod yazmaya gerek kalmadan adımların sırasını (`sequence` değerini değiştirerek) güncelleyebilmeli veya bir adımı kapatabilmelidir (`is_active = false`).
2. **Audit Logging (Denetim İzi):** `LOCATION_PROCESS_STEP_CONFIG` tablosunda yapılan herhangi bir güncelleme (Örn: Kalite kontrolün pasif yapılması veya onay gereksiniminin açılması) kimin tarafından yapıldığı, eski değer ve yeni değer bilgileriyle birlikte **System Audit Log** tablosuna kaydedilmelidir.
3. **Rol Bazlı Yetki Kontrolü:** Eğer bir adım için `responsible_role_id` tanımlanmışsa, o adımı sadece o role sahip kullanıcılar gerçekleştirebilmelidir. Aksi takdirde API `403 Unauthorized` hatası vermelidir.
4. **Onay Mekanizması Tetikleyicisi (Approval Interceptor):** `requires_approval = true` olan adımlarda, işlem tamamlanmaya çalışıldığında sistem işlemi "Aşama Onayı Bekliyor" (Pending Approval) durumuna almalı ve ilgili yöneticilere bildirim/onay isteği göndermelidir.
