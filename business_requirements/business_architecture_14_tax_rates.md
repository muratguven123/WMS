# İş İsteri 14: Farklı Vergi Oranları Yönetimi
Bu teknik tasarım, LLM modellerinin ülkeden ülkeye veya üründen ürüne değişen vergi oranlarını (KDV, VAT, GST, Stopaj, ÖTV vb.) tarih bazlı sürümleyerek yönetebileceği ve işlemlere doğru vergi oranını atayabilecek dinamik vergi çözümleme altyapısını kodlaması için gereken mimariyi tanımlar.

---

## 1. Veri Modeli ve Sürüm Kontrollü Veri Tabanı Şeması (ERD)

Vergi oranlarının tarih bazlı değişebilmesi ve geçmiş işlemlerin bozulmaması için tasarlanan sürümlemeli veri tabanı şemasıdır.

```mermaid
erDiagram
    TAX_TYPE ||--o{ TAX_RATE : "defines"
    COUNTRY ||--o{ TAX_RATE : "applies_to"
    LOCATION ||--o{ TAX_RATE : "optionally_applies_to"
    CUSTOMER ||--o{ TAX_RATE : "optionally_applies_to"
    TAX_RATE ||--o{ TAX_RATE_AUDIT_LOG : "audits"
    USER ||--o{ TAX_RATE_AUDIT_LOG : "performed_by"

    TAX_TYPE {
        uuid id PK
        string code "e.g. KDV, VAT, GST, ÖTV, STOPAJ"
        string name "e.g. Katma Değer Vergisi"
        boolean is_active
    }

    TAX_RATE {
        uuid id PK
        uuid tax_type_id FK
        uuid country_id FK
        uuid location_id FK "nullable"
        uuid customer_id FK "nullable"
        string product_type "nullable (e.g. FOOD, ELECTRONIC, SERVICE)"
        string operation_type "nullable (e.g. INBOUND, OUTBOUND)"
        decimal rate "precision: 5, 2 (e.g. 20.00)"
        date start_date "validity start date"
        date end_date "validity end date (nullable)"
        boolean is_active
        timestamp created_at
    }

    TAX_RATE_AUDIT_LOG {
        uuid id PK
        uuid tax_rate_id FK
        string action_type "e.g. INSERT, UPDATE_RATE, EXPIRE"
        decimal old_rate "precision: 5, 2 (nullable)"
        decimal new_rate "precision: 5, 2"
        uuid user_id FK
        timestamp changed_at
    }
```

### Şema Tasarım Kuralları:
1. **Zaman Serisi Yapısı (Temporal Data):** Vergi oranları doğrudan güncellenmemeli, tarih aralıklarıyla (`start_date` - `end_date`) versiyonlanmalıdır. Bir vergi oranı değiştiğinde mevcut oran kaydının `end_date` değeri sonlandırılmalı ve yeni oranı içeren yeni bir satır eklenmelidir.
2. **Çok Boyutlu Kurallar:** Vergi oranı; Ülke, Lokasyon, Müşteri, Ürün Tipi ve Operasyon Tipi boyutlarına göre en özel kuralı bulacak şekilde sorgulanabilir olmalıdır.

---

## 2. Dinamik Vergi Oranı Çözümleme Akışı (Tax Resolution Flow)

Bir fatura satırı hesaplanırken sistemin en doğru ve güncel vergi oranını çözme (resolve) süreci:

```mermaid
sequenceDiagram
    autonumber
    actor Billing as Billing / Transaction Service
    participant Resolver as Tax Resolution Engine
    participant DB as Veri Tabanı (PostgreSQL)

    Billing->>Resolver: GetTaxRate(TaxCode: "KDV", Date: "2026-07-03", Context: [Country: "TR", Location: "LOC-A", Cust: "Cust-A", Prod: "FOOD"])
    
    Resolver->>DB: Koşullarla eşleşen aktif vergi oranlarını sorgula
    Note over DB: SQL sorgusu geçerlilik tarihini kontrol eder:<br/>"WHERE start_date <= '2026-07-03' AND (end_date IS NULL OR end_date >= '2026-07-03')"
    DB-->>Resolver: Eşleşen Vergi Oranları Listesi
    
    Resolver->>Resolver: Öncelik Hiyerarşisini Uygula (Best-Match Algorithm)
    Note over Resolver: En özel eşleşmeden (Location+Customer+Product) en genel eşleşmeye (Country Default) doğru filtreleme yapılır.
    
    alt Eşleşen Özel Oran Bulundu
        Resolver-->>Billing: Seçilen Özel Oran (Örn: FOOD için %1.00 KDV)
    else Özel Oran Yoksa
        Resolver-->>Billing: Ülke Standart Oranı (Örn: Standart %20.00 KDV)
    end
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Öncelik Çözümleme Hiyerarşisi (Best Match Rule):** Vergi oranı çözümlenirken çakışmaları çözmek için sistem en spesifik kuraldan genel kurala doğru şu sıralamayı takip etmelidir:
   * 1. Sıra (En Özel): `Location` + `Customer` + `Product Type` eşleşmesi.
   * 2. Sıra: `Location` + `Product Type` eşleşmesi.
   * 3. Sıra: `Customer` + `Product Type` eşleşmesi.
   * 4. Sıra: `Product Type` eşleşmesi.
   * 5. Sıra (En Genel): `Country` standart vergi oranı.
2. **Tarih Geçerlilik Kontrolü (Temporal Validation):** Sistem geçmişe dönük fatura ürettiğinde, faturanın **düzenlendiği tarihteki** geçerli vergi oranı kullanılmalıdır. Kurallar üzerinde `start_date` ve `end_date` alanları ile kesintisiz bir tarih dizisi yönetilmeli, tarih çakışmaları (Örn: Aynı vergi tipi için bir ülkede aynı tarihte iki standart oran bulunması) engellenmelidir.
3. **Değişikliklerin Audit Loglanması:** Bir vergi oranının güncellenmesi veya yeni oran tanımlanması durumunda `TAX_RATE_AUDIT_LOG` tablosuna eski oran, yeni oran, işlemi yapan kullanıcı ve zaman damgası bilgileriyle birlikte kayıt atılması zorunludur.
4. **Vergi Muafiyet İstisnası (Tax Exemption):** Eğer müşterinin veya işlemin vergi muafiyeti durumu varsa, vergi motoru vergi oranını otomatik olarak `%0` çözmeli ve vergi muafiyet kodunu (`tax_exemption_code`) fatura satırına eklemelidir.
