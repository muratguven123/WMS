# İş İsteri 7: Uluslararası Tarih ve Saat Formatları
Bu teknik tasarım, LLM modellerinin kullanıcı girişlerinde (input) ve ekran/rapor çıktılarında ülkelere göre değişen tarih/saat biçimlendirmelerini (Örn: `31.12.2026` vs. `12/31/2026`) esnek bir şekilde yönetebilmesi için gereken biçimlendirme mimarisini tanımlar.

---

## 1. Veri Modeli ve Veri Tabanı Şeması (ERD)

Ülke bazında tarih, saat ve sayısal format kurallarını (ondalık/binlik ayraçlar dahil) tanımlamak için kullanılan veritabanı şemasıdır.

```mermaid
erDiagram
    COUNTRY ||--o{ COUNTRY_FORMAT_CONFIG : "has"
    LOCATION ||--o{ LOCATION_FORMAT_OVERRIDE : "can_override"

    COUNTRY {
        uuid id PK
        string iso_code "e.g. TR, US, DE, GB"
        string name
    }

    COUNTRY_FORMAT_CONFIG {
        uuid id PK
        uuid country_id FK
        string date_format "e.g. dd.MM.yyyy, MM/dd/yyyy"
        string time_format "e.g. HH:mm, hh:mm a"
        string decimal_separator "e.g. ',' or '.'"
        string thousand_separator "e.g. '.' or ','"
        timestamp updated_at
    }

    LOCATION_FORMAT_OVERRIDE {
        uuid id PK
        uuid location_id FK
        string date_format "override default country format"
        string time_format
        string decimal_separator
        string thousand_separator
        timestamp updated_at
    }
```

### Şema Tasarım Kuralları:
1. **Varsayılan ve Özel Ayarlar:** Lokasyon seviyesindeki `LOCATION_FORMAT_OVERRIDE` tablosu, eğer bir deponun bağlı olduğu ülkeden farklı özel bir format gereksinimi varsa kullanılır. Boş ise ülkenin varsayılanı geçerlidir.
2. **Sayısal Format Desteği:** Sayısal değerler (Örn: Miktar, vergi tutarları, fiyatlar) için `decimal_separator` ve `thousand_separator` eklenmiştir. Bu alanlar tarih/saat biçimlendirmesi ile doğrudan ilişkilidir (Lokalizasyon bütünlüğü).

---

## 2. Giriş ve Çıktı Biçimlendirme Akışı (Data Flow)

Arayüzde kullanıcının kendi formatıyla girdiği tarihin doğrulanması ve sunucuda basılan raporlarda formatın uygulanması akışıdır:

```mermaid
sequenceDiagram
    autonumber
    actor User as Kullanıcı (ABD Arayüzü)
    participant Client as Arayüz (Frontend)
    participant API as WMS Backend API
    participant DB as Veri Tabanı
    participant Report as PDF/Report Generator

    Note over User: Kullanıcı Tarih Alanına değer girer:<br/>"12/31/2026 11:59 PM" (ABD Formatı)

    Client->>Client: Aktif lokasyon formatını ("MM/dd/yyyy hh:mm a") kullanarak veriyi doğrula.
    alt Geçersiz Tarih Girişi
        Client-->>User: "Lütfen MM/dd/yyyy hh:mm a formatında giriniz" uyarısı ver.
    else Geçerli Giriş
        Client->>Client: Girişi standart ISO 8601 formatına parse et:<br/>"2026-12-31T23:59:00Z"
        Client->>API: API Kayıt İsteği gönder (JSON formatında UTC zamanı)
        API->>DB: UTC zamanını kaydet ("2026-12-31 23:59:00")
    end

    %% RAPORLAMA AKIŞI
    User->>Report: PDF Rapor İndir (İngiltere Deposu - Format: "dd/MM/yyyy HH:mm")
    Report->>DB: UTC zamanını oku ("2026-12-31 23:59:00")
    Report->>Report: Tarihi İngiltere formatına biçimlendir:<br/>"31/12/2026 23:59"
    Report-->>User: PDF Raporunu biçimlendirilmiş tarihle teslim et.
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **Standardize API İletişimi (ISO 8601):** API katmanı (JSON istek ve yanıtları) asla lokal formatlarda tarih verisi taşımamalıdır. İletişim her zaman `YYYY-MM-DDTHH:mm:ss.sssZ` formatında olmalıdır. Lokal formatlama sadece UI render anında veya server-side dosya (PDF/Excel) oluşturma anında yapılmalıdır.
2. **Kullanıcı Giriş Doğrulaması (Frontend Parsing):** Kullanıcı arayüzündeki tarih seçim bileşenleri (date-picker) ve elle giriş maskeleri, aktif lokasyonun konfigürasyonundan gelen `date_format` ve `time_format` desenlerini dinamik olarak almalı ve bu desenlere göre input validation yapmalıdır.
3. **Raporlama Kütüphanesi Entegrasyonu:** Rapor üretme servisleri (Örn: JasperReports, iText, Puppeteer PDF) tarih basarken, ilgili veritabanındaki format tablosundan sorgulanan format parametresini alarak tarih/saat/sayı dönüşümlerini yapmalıdır.
4. **Sayı Biçimlendirme Kuralları:** Fiyat ve miktarların gösteriminde de ilgili lokasyonun ondalık (`decimal_separator`) ve binlik (`thousand_separator`) ayıraç kurallarına uyulmalıdır (Örn: Türkiye için `1.250,50 TRY`, ABD için `1,250.50 USD`).
