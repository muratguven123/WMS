# İş İsteri 12: Yerel ve Uluslararası Adres Formatları
Bu teknik tasarım, LLM modellerinin ülkeye göre değişen dinamik adres alanlarını (Türkiye için Mahalle/İlçe, ABD için Eyalet/ZIP vb.) veritabanı şemasını bozmadan yönetebileceği JSONB tabanlı esnek bir adres mimarisini ve regex doğrulama altyapısını kodlaması için gereken tasarımı tanımlar.

---

## 1. Veri Modeli ve JSONB Tabanlı Adres Şeması (ERD)

Yeni sütun eklemeden her ülkenin adres alanlarını destekleyebilmek için adres bileşenleri **JSONB** tipinde saklanır, kuralları ise şablon tablosu tanımlar.

```mermaid
erDiagram
    COUNTRY ||--o{ COUNTRY_ADDRESS_TEMPLATE : "defines_fields"
    COUNTRY ||--o{ ADDRESS : "associated_with"
    ADDRESS_TEMPLATE_FIELD ||--o{ COUNTRY_ADDRESS_TEMPLATE : "maps_to"

    COUNTRY_ADDRESS_TEMPLATE {
        uuid id PK
        uuid country_id FK
        uuid address_template_field_id FK
        boolean is_mandatory
        integer sequence "display order on UI"
        string validation_regex "optional regex format check (e.g. for ZIP)"
        string error_message_key "translation key for failed validation"
    }

    ADDRESS_TEMPLATE_FIELD {
        uuid id PK
        string field_key "e.g. district, state, zip_code, neighborhood, door_no"
        string field_label_key "translation key for label (e.g. fields.district)"
    }

    ADDRESS {
        uuid id PK
        uuid country_id FK
        string city "standard search/filter field"
        string state "standard search/filter field (state/region)"
        string zip_code "standard search/filter field"
        jsonb address_details "stores dynamic fields: {neighborhood: 'Huzur', street: 'Fatih', door_no: '12'}"
        text formatted_address "computed/generated full address string"
        timestamp updated_at
    }
```

### Şema Tasarım Kuralları:
1. **JSONB Esnekliği (Dynamic Fields):** İlçe, Mahalle, Sokak, Kapı No, Daire No gibi ülkeye göre değişebilen dinamik alanlar `address_details` içinde JSONB formatında saklanır. Şehir (`city`), Eyalet/Bölge (`state`) ve Posta Kodu (`zip_code`) gibi tüm ülkelerde ortak ve arama/filtreleme için kritik alanlar ise performans için ayrı sütunlarda indeksli (Indexed) olarak tutulur.
2. **Unicode Desteği (Cyrillic, Arabic etc.):** Veritabanı karakter seti kesinlikle **UTF-8 (veya UTF-16)** (PostgreSQL'de varsayılan, SQL Server'da `NVARCHAR`) olarak ayarlanmalıdır, böylece uluslararası karakterlerin bozulması önlenir.

---

## 2. Dinamik Adres Giriş ve Formatlama Akışı (Runtime Flow)

Kullanıcı arayüzünde ülke seçildiğinde adres formunun dinamik çizilmesi, doğrulanması ve kaydedilmesi akışıdır:

```mermaid
sequenceDiagram
    autonumber
    actor User as Kullanıcı (Operatör)
    participant Client as Arayüz (Frontend)
    participant API as Adres API Servisi
    participant DB as Veri Tabanı (PostgreSQL)

    User->>Client: Müşteri/Depo Kaydında Ülke Seç: "USA"
    Client->>API: Adres Şablonunu Getir (CountryID: "USA")
    API->>DB: Şablon Detaylarını Sorgula (COUNTRY_ADDRESS_TEMPLATE)
    DB-->>API: Şablon Listesi (Eyalet: Zorunlu, ZIP: Zorunlu [regex: ^\d{5}(-\d{4})?$])
    API-->>Client: Şablon Detayları (JSON)

    Note over Client: Arayüz şablona göre form alanlarını oluşturur.<br/>Kullanıcı verileri doldurur: State="CA", ZIP="94105"

    User->>Client: Kaydet Butonuna Bas
    Client->>Client: ZIP Kodu Regex Kontrolü Yap (94105 eşleşti)
    Client->>API: Adres Verisini Gönder (Fields JSON)
    
    API->>API: Sunucu Tarafı Doğrulamaları Yap (Server-side check)
    API->>API: Adres Metnini Biçimlendir (Formatted Address Builder):<br/>"Street Address, City, State ZIP Code, USA"
    
    API->>DB: Adres Kaydını SQL'e yaz (city, state, zip ve details JSONB)
    DB-->>API: Kayıt Başarılı
    API-->>Client: Adres Kaydedildi
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Dinamik Regex Validasyonu (Regex Engine):** Adres kaydedilirken backend, `COUNTRY_ADDRESS_TEMPLATE` tablosundaki `validation_regex` kurallarını dinamik olarak okuyup gelen JSONB alanlarına uygulamalıdır. Regex doğrulamasından geçmeyen kayıtlar veri tabanına yazılmamalıdır.
2. **Adres Biçimlendirici (Address Formatter Utility):** Her ülke için adresin belgelerde (fatura, çeki listesi, etiket) nasıl yazılacağını tanımlayan bir biçimlendirme kuralı olmalıdır. Sistem, girilen JSONB verisinden otomatik olarak tek satırlık `formatted_address` alanını üretip veritabanına kaydetmelidir (Örn: TR için `Mahalle, Sokak No:X D:Y İlçe/İl`, US için `Street, City, State ZIP`).
3. **UTF-8 Uyumluluğu (Non-Latin Characters):** Unicode karakterlerin (Örn: Çince, Arapça, Rusça karakterler) arayüzde bozulmadan gösterilmesi ve veritabanına kaydedilmesi için API isteklerinde `Content-Type: application/json; charset=utf-8` header kullanımı zorunlu tutulmalıdır.
4. **Adres Arama İndeksleri (Gin Index):** PostgreSQL kullanılacaksa, `address_details` JSONB sütunu üzerinde hızlı arama yapabilmek için **GIN Index** (`CREATE INDEX idx_address_details ON addresses USING gin (address_details);`) tanımlanmalıdır.
