# İş İsteri 10: Döviz Kuru Yönetimi
Bu teknik tasarım, LLM modellerinin günlük döviz kurlarını farklı kaynaklardan (TCMB, ERP, banka vb.) asenkron olarak çekebilecek, manuel güncellemeleri audit log sistemine kaydedebilecek ve kur geçmişini sorgulayabilecek altyapıyı kodlaması için gereken mimariyi tanımlar.

---

## 1. Veri Modeli ve Kur Geçmişi / Denetim Şeması (ERD)

Kurlar ve kur güncellemelerinin takibi için tasarlanan veri tabanı şemasıdır.

```mermaid
erDiagram
    CURRENCY ||--o{ EXCHANGE_RATE : "source_currency"
    CURRENCY ||--o{ EXCHANGE_RATE : "target_currency"
    EXCHANGE_RATE ||--o{ EXCHANGE_RATE_AUDIT_LOG : "audits"
    USER ||--o{ EXCHANGE_RATE_AUDIT_LOG : "performed_by"

    EXCHANGE_RATE {
        uuid id PK
        date rate_date "YYYY-MM-DD"
        uuid source_currency_id FK
        uuid target_currency_id FK
        string rate_type "e.g. BUYING, SELLING, EFFECTIVE_BUYING, EFFECTIVE_SELLING"
        string rate_source "e.g. TCMB, MANUAL, ERP, BANK, CONTRACT"
        decimal rate "precision: 18, 6"
        timestamp created_at
        timestamp updated_at
    }

    EXCHANGE_RATE_AUDIT_LOG {
        uuid id PK
        uuid exchange_rate_id FK
        string action_type "e.g. INSERT, UPDATE, DELETE"
        decimal old_rate "precision: 18, 6 (null on insert)"
        decimal new_rate "precision: 18, 6"
        uuid user_id FK "user who made change (null if system job)"
        timestamp changed_at
    }
```

### Şema Tasarım Kuralları:
1. **Tekil Kur Kısıtı (Unique Rates Index):** `EXCHANGE_RATE` tablosunda `rate_date`, `source_currency_id`, `target_currency_id`, `rate_type` ve `rate_source` alanlarının birleşimi benzersiz (UNIQUE) olmalıdır. Bu, aynı tarihte aynı kaynaktan aynı tipte mükerrer kur girilmesini engeller.
2. **Hassas Ondalık Değerler:** Döviz kurları için `DECIMAL(18, 6)` veri tipi zorunludur.

---

## 2. Günlük Kur Entegrasyonu ve Manuel Güncelleme Akışı

TCMB'den otomatik kur çekimi ile yöneticilerin elle kur girme/güncelleme akışı aşağıdaki gibidir:

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Cron Job / Scheduler (Günlük 15:30)
    participant Admin as Sistem Yöneticisi (Admin Panel)
    participant API as Döviz Kuru Servisi (Backend)
    participant Ext as TCMB XML / Dış API Servisi
    participant DB as Veri Tabanı (PostgreSQL)
    participant Cache as Redis Cache

    %% AKIŞ A: Otomatik Çekim
    Scheduler->>API: Günlük Kurları Güncelle (Tetikle)
    API->>Ext: Günlük XML Döviz Kurlarını Çek (TCMB)
    Ext-->>API: XML Döviz Kurları (USD, EUR, GBP vb.)
    API->>API: XML Verisini Parse Et ve Doğrula
    API->>DB: Kurları Kaydet (RateSource: "TCMB")
    DB-->>API: Başarılı
    API->>Cache: Günün Kurlarını Cache'e Yaz (key: "rate:*:*:today")
    
    %% AKIŞ B: Manuel Güncelleme & Audit Log
    Admin->>API: Kuru Manuel Güncelle (USD/TRY = 35.50, RateSource: "MANUAL")
    API->>DB: Mevcut Kuru Sorgula (Eski Kuru Almak İçin)
    DB-->>API: Eski Kur Değeri: 35.00
    API->>DB: Kuru Güncelle ve EXCHANGE_RATE_AUDIT_LOG tablosuna yaz (Eski: 35.00, Yeni: 35.50)
    DB-->>API: Güncelleme Başarılı
    API->>Cache: Güncellenen Kuru Cache'ten Temizle (Cache Eviction)
    API-->>Admin: Güncelleme Başarılı Bildirimi
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Otomatik Entegrasyon Zamanlayıcısı (TCMB Crawler):** Sistemde her gün otomatik olarak çalışan bir asenkron arka plan görevi (Scheduler/Worker) olmalıdır. Bu görev, TCMB'nin kurları açıkladığı saatten hemen sonra (Türkiye saati ile 15:30) `https://www.tcmb.gov.tr/kurlar/today.xml` adresini sorgulamalı ve yeni kurları sisteme kaydetmelidir.
2. **Denetim İzi (Audit Logging):** Manuel olarak yapılan her kur ekleme, güncelleme veya silme işleminde `EXCHANGE_RATE_AUDIT_LOG` tablosuna eski değer, yeni değer, işlemi yapan kullanıcı (`user_id`) ve zaman damgasıyla birlikte zorunlu olarak kayıt atılmalıdır.
3. **Kur Kaynağı Önceliği (Rate Source Fallback):** İşlemlerde döviz kuru sorgulanırken sistem aşağıdaki öncelik sırasına göre kurları aramalıdır:
   * 1. Öncelik: Müşteri Sözleşmesine tanımlanmış olan **Sabit Kur** (Örn: `CONTRACT.fixed_rate`).
   * 2. Öncelik: Manuel girilmiş **Özel Müşteri Kuru** (`RateSource = 'MANUAL'`).
   * 3. Öncelik: ERP sisteminden aktarılan **ERP Kuru** (`RateSource = 'ERP'`).
   * 4. Öncelik: TCMB'den otomatik çekilen **Referans Kur** (`RateSource = 'TCMB'`).
4. **Resmi Tatil ve Hafta Sonu Fallback (Weekend Fallback):** Hafta sonları ve resmi tatillerde TCMB kur yayınlamaz. Kur motoru, kur bulunamayan bir gün için sorgulama yapıldığında, veri tabanındaki en yakın **geçmiş tarihe ait** aktif kuru bulup getirmelidir (Geriye doğru arama limiti maksimum 5 gün olmalıdır).
