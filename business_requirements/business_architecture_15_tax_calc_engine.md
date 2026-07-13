# İş İsteri 15: Vergi Hesaplama Mekanizması
Bu teknik tasarım, LLM modellerinin vergi dahil/hariç hesaplama, çoklu vergi uygulama (Örn: ÖTV + KDV), muafiyetler ve kur farkı vergilerini hesaplayan, izlenebilirliği yüksek bir **Veri Tabanı Entegreli Vergi Hesaplama Motorunu** (Tax Calculation Engine) kodlamasını sağlar.

---

## 1. Yazılım Tasarım Deseni: Strateji Deseni (Strategy Pattern)

Farklı vergi hesaplama kurallarının (Dahil vergi, Hariç vergi, Katmanlı/Çoklu vergi vb.) esnek ve genişletilebilir şekilde yönetilmesi için **Strategy Pattern** kullanılır.

```mermaid
classDiagram
    class ITaxCalculationStrategy {
        <<interface>>
        +calculateTax(decimal baseAmount, decimal rate) TaxCalculationResult
    }

    class TaxExclusiveStrategy {
        +calculateTax(decimal baseAmount, decimal rate) TaxCalculationResult
    }

    class TaxInclusiveStrategy {
        +calculateTax(decimal baseAmount, decimal rate) TaxCalculationResult
    }

    class CompoundTaxStrategy {
        -List~ITaxCalculationStrategy~ _nestedStrategies
        +calculateTax(decimal baseAmount, decimal rate) TaxCalculationResult
    }

    class TaxCalculationResult {
        +decimal baseAmount "Matrah"
        +decimal taxAmount "Vergi Tutarı"
        +decimal grandTotal "Genel Toplam"
    }

    class TaxEngine {
        +calculate(TaxContext context) List~TaxCalculationLog~
    }

    ITaxCalculationStrategy <|.. TaxExclusiveStrategy : implements
    ITaxCalculationStrategy <|.. TaxInclusiveStrategy : implements
    ITaxCalculationStrategy <|.. CompoundTaxStrategy : implements
    TaxEngine --> ITaxCalculationStrategy : executes
    ITaxCalculationStrategy ..> TaxCalculationResult : produces
```

---

## 2. İzlenebilirlik Veri Modeli ve Log Şeması (ERD)

Her vergi hesaplamasının vergi denetimleri için detaylı bir şekilde saklanmasını sağlayan izlenebilirlik şemasıdır.

```mermaid
erDiagram
    TAX_TYPE ||--o{ TAX_CALCULATION_LOG : "referenced_in"
    TAX_CALCULATION_LOG {
        uuid id PK
        string transaction_type "e.g. INVOICE_LINE, TRANSACTION_FEE"
        uuid transaction_reference_id "foreign key to target table (UUID)"
        uuid tax_type_id FK
        decimal tax_rate "precision: 5, 2"
        decimal tax_base_amount "matrah (precision: 18, 4)"
        decimal calculated_tax_amount "vergi tutarı (precision: 18, 4)"
        boolean is_inclusive "tax inclusive or exclusive flag"
        boolean is_exempt "is tax exempted?"
        string exemption_code "exemption reason code (nullable)"
        string calculation_source "e.g. TAX_ENGINE_V1"
        timestamp calculation_date
    }
```

---

## 3. Katmanlı / Çoklu Vergi Hesaplama Akışı (Sequence Flow)

Bir ürüne hem ÖTV (Özel Tüketim Vergisi) hem de ÖTV'li tutar üzerinden KDV uygulanması (katmanlı vergi) senaryosunun hesaplanma akışıdır:

```mermaid
sequenceDiagram
    autonumber
    actor Billing as Billing Service / Invoice Creator
    participant Engine as Tax Engine
    participant Exclusive as TaxExclusiveStrategy (ÖTV)
    participant Compound as CompoundTaxStrategy (KDV)
    participant DB as Veri Tabanı (PostgreSQL)

    Note over Billing: Net Tutar: 1000.00 TRY<br/>Vergi Oranları: ÖTV (%10), KDV (%20) (Katmanlı)

    Billing->>Engine: Hesapla(Net: 1000.00, TaxRules: [ÖTV, KDV])
    
    %% ADIM 1: ÖTV HESAPLAMA
    Engine->>Exclusive: calculateTax(Base: 1000.00, Rate: 10)
    Exclusive-->>Engine: ÖTV Sonucu (Matrah: 1000.00, Vergi: 100.00, Toplam: 1100.00)
    
    %% ADIM 2: KDV HESAPLAMA (ÖTV'li tutar matrah kabul edilir)
    Engine->>Compound: calculateTax(Base: 1100.00, Rate: 20)
    Compound-->>Engine: KDV Sonucu (Matrah: 1100.00, Vergi: 220.00, Toplam: 1320.00)
    
    Note over Engine: Genel Hesaplama Özeti:<br/>Matrah: 1000.00 TRY<br/>ÖTV (%10): 100.00 TRY<br/>KDV (%20): 220.00 TRY<br/>Genel Toplam: 1320.00 TRY
    
    Engine->>DB: Hesaplama Loglarını Kaydet (TAX_CALCULATION_LOG)
    DB-->>Engine: Kayıt Başarılı
    
    Engine-->>Billing: Toplam Tutarları ve Log Detaylarını Dön
```

---

## 4. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Matematiksel Formül Standartları:**
   * **Vergi Hariç (Tax-Exclusive):**
     $$\text{Vergi Tutarı} = \text{Matrah} \times \frac{\text{Vergi Oranı}}{100}$$
     $$\text{Genel Toplam} = \text{Matrah} + \text{Vergi Tutarı}$$
   * **Vergi Dahil (Tax-Inclusive):**
     $$\text{Matrah} = \frac{\text{Genel Toplam}}{1 + \frac{\text{Vergi Oranı}}{100}}$$
     $$\text{Vergi Tutarı} = \text{Genel Toplam} - \text{Matrah}$$
2. **Hassas Yuvarlama Kuralı:** Hesaplamalar sırasında çıkabilecek kuruş küsuratları için her faturada tutarlı yuvarlama yapılması amacıyla dilin Decimal kütüphaneleri (Örn: C#'ta `decimal`, Python'da `decimal.Decimal`) kullanılmalıdır. Ara hesaplamalarda yuvarlama yapılmamalı, en son vergi tutarı hesaplandığında yuvarlama uygulanmalıdır.
3. **Kur Farkı Vergi Hesaplaması (Forex Tax Calculation):** Kur farkı faturası kesilmesi gerektiğinde, vergi matrahı kur farkı tutarı olarak kabul edilmeli ve vergi bu fark üzerinden hesaplanarak loglanmalıdır.
4. **Vergi Muafiyeti Durumu (Tax Exemption Logging):** Muafiyet durumunda (`is_exempt = true`), vergi tutarı sıfır (`0.00`) olarak kaydedilmeli ve vergi dairesi raporlamalarında kullanılmak üzere yasal muafiyet kodu (`exemption_code`) mutlaka loglanmalıdır.
