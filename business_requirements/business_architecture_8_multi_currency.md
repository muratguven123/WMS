# İş İsteri 8: Çoklu Para Birimi Yönetimi
Bu teknik tasarım, LLM modellerinin finansal işlemler, faturalama ve raporlamalarda kullanılacak çoklu para birimi altyapısını, kuruş/cent hassasiyetlerini ve çevrim algoritmalarını hatasız kodlayabilmesi için gereken mimariyi tanımlar.

---

## 1. Veri Modeli ve Seviye Bazlı Para Birimi Hiyerarşisi (ERD)

Para biriminin sistemin farklı seviyelerinde (Sistem, Şirket, Depo, Müşteri, Fatura, Muhasebe vb.) nasıl tanımlandığını ve finansal hareketlerin nasıl saklandığını gösteren şemadır.

```mermaid
erDiagram
    CURRENCY ||--o{ SYSTEM_CONFIG : "defines_default"
    CURRENCY ||--o{ COMPANY : "defines_base"
    CURRENCY ||--o{ LOCATION : "defines_local"
    CURRENCY ||--o{ CUSTOMER : "defines_default"
    CURRENCY ||--o{ CONTRACT : "defines_agreement"
    CURRENCY ||--o{ FINANCIAL_TRANSACTION : "original_currency"
    CURRENCY ||--o{ FINANCIAL_TRANSACTION : "base_currency"
    
    COMPANY ||--o{ FINANCIAL_TRANSACTION : "associated_with"
    LOCATION ||--o{ FINANCIAL_TRANSACTION : "associated_with"
    CUSTOMER ||--o{ CONTRACT : "has"
    CONTRACT ||--o{ FINANCIAL_TRANSACTION : "billed_under"

    SYSTEM_CONFIG {
        uuid id PK
        uuid default_currency_id FK
        string company_group_name
    }

    CURRENCY {
        uuid id PK
        string code "e.g. USD, EUR, TRY, JPY"
        string symbol "e.g. $, €, ₺, ¥"
        integer decimal_places "e.g. 2 for USD, 0 for JPY, 4 for fuel pricing"
        string name "e.g. US Dollar"
        boolean is_active
    }

    FINANCIAL_TRANSACTION {
        uuid id PK
        uuid company_id FK
        uuid location_id FK
        uuid contract_id FK "nullable"
        uuid original_currency_id FK
        decimal original_amount "precision: 18, 4"
        decimal exchange_rate "precision: 18, 6"
        uuid base_currency_id FK "usually company base currency"
        decimal converted_amount "original_amount * exchange_rate"
        timestamp transaction_date
    }
```

### Şema Tasarım Kuralları:
1. **Decimal Hassasiyeti (Exact Decimals):** Finansal hesaplamalarda yuvarlama hatalarını önlemek için veri tabanındaki tüm miktar (`amount`) ve oran (`rate`) alanları `DECIMAL` (örneğin SQL'de `DECIMAL(18, 4)` tutarlar için, `DECIMAL(18, 6)` döviz kurları için) veri tipinde tanımlanmalı, asla `float` veya `double` kullanılmamalıdır.
2. **Çift Para Birimi Depolama (Dual-Currency Storage):** Her finansal işlemde, işlemin yapıldığı orijinal tutar ve para birimi ile birlikte, o anki kur üzerinden hesaplanmış şirket ana para birimi (`base_currency_id`) cinsinden karşılığı (`converted_amount`) mutlaka saklanmalıdır.

---

## 2. Döviz Çevrimi ve Kur Sorgulama Akışı (Runtime Flow)

Bir finansal hareket oluşturulurken sistemin doğru kuru seçmesi ve tutarları hesaplama akışıdır:

```mermaid
sequenceDiagram
    autonumber
    actor System as Faturalama / Stok İşlem Servisi
    participant Calc as Currency Conversion Engine
    participant Cache as Redis Cache (Rates)
    participant DB as Veri Tabanı (Exchange Rates)

    System->>Calc: Convert(100 EUR ➔ TRY, Date: "2026-07-03", Type: "SELLING")
    
    Calc->>Cache: Kur Bilgisini Getir (key: "rate:EUR:TRY:2026-07-03:SELLING")
    
    alt Cache'de Kur Mevcut
        Cache-->>Calc: Kur Değeri (Örn: 35.000000)
    else Cache Boş (Cache Miss)
        Calc->>DB: Belirtilen tarihe ve kur tipine ait kuru sorgula
        alt Kur Bulundu
            DB-->>Calc: Kur Değeri
        else Kur Bulunamadı
            Calc->>DB: En yakın geçmiş tarihteki kuru sorgula (Fallback)
            DB-->>Calc: Geçmiş Kur Değeri
        end
        Calc->>Cache: Kur Değerini Cache'e yaz (TTL: 12h)
    end

    Calc->>Calc: Çevrim Hesabı Yap:<br/>100 * 35.000000 = 3500.0000
    Calc->>Calc: TRY Para birimi decimal_places=2 olduğu için yuvarla:<br/>3500.00
    
    Calc-->>System: Sonuç (ConvertedAmount: 3500.00 TRY, RateUsed: 35.000000)
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Yuvarlama Kuralları (Banker's Rounding):** Finansal yuvarlamalarda standart yukarı yuvarlama yerine, finans sektör standardı olan "Banker's Rounding" (MidpointRounding.ToEven) algoritması kullanılmalıdır.
2. **Tarih Bazlı Kur Geçmişi Sorgusu (Point-in-Time Rate):** Geriye dönük bir işlem yapıldığında (Örn: 3 gün önceki bir sevkiyat faturası), bugünün kuru değil, **işlemin gerçekleştiği tarihteki kur** (`transaction_date`) baz alınmalıdır.
3. **Eksik Kur Fallback Kuralı (Bypass Strategy):** Eğer işlem gününe ait bir kur bulunamazsa, sistem bir önceki günün kurunu kullanmalı ancak bu durumu işlem logunda "Alternatif Kur Kullanıldı" bayrağıyla işaretlemeli ve yöneticiye uyarı göndermelidir.
4. **Çoklu Seviye Para Birimi Çözümleme Hiyerarşisi:** Bir işlemin para birimi belirlenirken aşağıdaki öncelik sırası uygulanmalıdır:
   * 1. Öncelik: İşlemin doğrudan bağlı olduğu **Sözleşme/Fatura Para Birimi** (`CONTRACT.currency_id`).
   * 2. Öncelik: İlişkili **Müşteri Kartındaki Para Birimi** (`CUSTOMER.default_currency_id`).
   * 3. Öncelik: İşlemin yapıldığı **Depo Yerel Para Birimi** (`LOCATION.currency_id`).
   * 4. Öncelik: **Şirket Ana Para Birimi** (`COMPANY.currency_id`).
