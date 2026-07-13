# İş İsteri 13: Hiyerarşik Adres Master Veri Yapısı
Bu teknik tasarım, LLM modellerinin adres verilerinin standart, tutarlı ve raporlanabilir şekilde girilmesini sağlayan hiyerarşik (Ülke ➔ Eyalet/Bölge ➔ Şehir ➔ İlçe ➔ Mahalle) master veri altyapısını ve ilişkili API akışlarını kodlamasını sağlar.

---

## 1. Veri Modeli ve Hiyerarşik Adres Master Tabloları (ERD)

Adres bileşenlerinin birbirine bağlı ilişkisel yapısını ve merkezi olarak nasıl yönetildiğini tanımlayan veri şemasıdır.

```mermaid
erDiagram
    COUNTRY ||--o{ STATE_PROVINCE : "contains"
    STATE_PROVINCE ||--o{ CITY : "contains"
    COUNTRY ||--o{ CITY : "direct_cities_if_no_state"
    CITY ||--o{ DISTRICT : "contains"
    DISTRICT ||--o{ NEIGHBORHOOD : "contains"
    
    ADDRESS }o--|| COUNTRY : "mapped_to"
    ADDRESS }o--|| CITY : "mapped_to"
    ADDRESS }o--|| DISTRICT : "mapped_to"

    COUNTRY {
        uuid id PK
        string iso_code "e.g. TR, US, DE"
        string name "e.g. Türkiye, United States"
        boolean is_active
    }

    STATE_PROVINCE {
        uuid id PK
        uuid country_id FK
        string name "e.g. California, Marmara Bölgesi"
        string code "e.g. CA, 34 (plate/code)"
        boolean is_active
    }

    CITY {
        uuid id PK
        uuid country_id FK
        uuid state_province_id FK "nullable (for countries without states)"
        string name "e.g. Istanbul, San Francisco"
        boolean is_active
    }

    DISTRICT {
        uuid id PK
        uuid city_id FK
        string name "e.g. Kadikoy, Manhattan"
        boolean is_active
    }

    NEIGHBORHOOD {
        uuid id PK
        uuid district_id FK
        string name "e.g. Caferaga, Soho"
        string zip_code "default zip for this neighborhood"
        boolean is_active
    }
```

### Şema Tasarım Kuralları:
1. **Esnek Hiyerarşi (State/Province Nullable):** Eyalet/Bölge yapısı olmayan ülkeler için `CITY` tablosundaki `state_province_id` boş bırakılabilir ve doğrudan `country_id` ile ilişkilendirilir.
2. **Merkezi Aktiflik Kontrolü (`is_active`):** Adres master verilerindeki herhangi bir birim (Örn: Deprem veya afet nedeniyle kapanan bir mahalle ya da ilçe) pasif yapıldığında, kullanıcılar yeni adres girerken o birimi seçememelidir.

---

## 2. Dinamik Bağımlı Seçim Akışı (Dropdown Cascade Flow)

Kullanıcı arayüzünde adres girilirken hiyerarşik olarak bir önceki seçime bağlı dinamik verilerin yüklenmesi süreci:

```mermaid
sequenceDiagram
    autonumber
    actor User as Kullanıcı (Operatör)
    participant Client as Arayüz (Dropdowns)
    participant API as Hiyerarşik Adres API'si
    participant DB as Veri Tabanı (PostgreSQL)

    User->>Client: Ülke Seçer: "Türkiye" (CountryId: "TR-UUID")
    Client->>API: GET /address/cities?countryId=TR-UUID
    API->>DB: is_active = true olan Şehirleri sorgula
    DB-->>API: Şehirler Listesi (İstanbul, Ankara, İzmir...)
    API-->>Client: 200 OK (Şehir Listesi)
    Client->>Client: Şehir Dropdown'unu doldur ve aktifleştir.

    User->>Client: Şehir Seçer: "İstanbul" (CityId: "IST-UUID")
    Client->>API: GET /address/districts?cityId=IST-UUID
    API->>DB: İstanbul'a bağlı aktif İlçeleri sorgula
    DB-->>API: İlçeler Listesi (Kadıköy, Beşiktaş, Üsküdar...)
    API-->>Client: 200 OK (İlçe Listesi)
    Client->>Client: İlçe Dropdown'unu doldur ve aktifleştir.

    User->>Client: İlçe Seçer: "Kadıköy" (DistrictId: "KDK-UUID")
    Client->>API: GET /address/neighborhoods?districtId=KDK-UUID
    API->>DB: Kadıköy'e bağlı aktif Mahalleleri sorgula
    DB-->>API: Mahalleler Listesi (Caferağa, Moda...)
    API-->>Client: 200 OK (Mahalle Listesi)
    Client->>Client: Mahalle ve varsayılan Posta Kodu alanlarını doldur.
```

---

## 3. Teknik İş Kuralları (Technical Business Rules)

LLM modelinin bu yapıyı oluştururken uyması gereken kurallar:

1. **İlişkili Arama ve Rapor Filtreleme (Denormalized Reporting Filters):** Stok raporlarında veya sevkıyat raporlarında adres bazlı filtreleme yapabilmek için, sorgular alt tablolara (district, city, country) join atılarak çalıştırılmalı ve rapor sorguları bu indeksli alanlar üzerinden filtrelenmelidir.
2. **Veri Giriş Tutarlılığı Kontrolü (Post-Back Validation):** API'ye yeni adres kaydı gönderildiğinde, gönderilen `district_id`'nin gerçekten gönderilen `city_id`'ye bağlı olup olmadığı, `city_id`'nin de seçilen `country_id`'ye ait olduğu sunucu tarafında doğrulanmalıdır (Çapraz manipülasyon engelleme).
3. **Merkezi Master Data Yönetimi (Admin Panel):** Adres verileri (il, ilçe, mahalle listeleri) kullanıcılar tarafından elle serbest metin olarak eklenemez. Sadece sistem yöneticileri (admin) panel üzerinden yeni il/ilçe/mahalle kaydı açabilir veya pasife alabilir.
4. **Posta Kodu Otomatik Doldurma:** Bir mahalle seçildiğinde, `NEIGHBORHOOD` tablosundaki `zip_code` değeri arayüzdeki Posta Kodu alanına otomatik yazılmalı ancak gerektiğinde kullanıcının değiştirebilmesi için alan düzenlenebilir (editable) olmalıdır.
