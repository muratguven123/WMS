# Faz 1: Çekirdek Sistem & Çoklu Lokasyon Temeli (LLM Kod Üretim Akışı)

Bu dosya, Faz 1 kapsamında yer alan modülleri sırasıyla Claude 3.5 Sonnet'e kodlatabilmeniz için tek bir akışta birleştirilmiştir. 

---

## AŞAMA 0: MAKR0 YÖNLENDİRME PROMPTU
*Bu promptu Faz 1'e başlarken yeni bir chat penceresinde Claude'a gönderin.*

```text
Rolün: Lead Software Architect & Backend Engineer.
Proje Bağlamı: Java 21, Spring Boot 3.x ve PostgreSQL kullanan çoklu firma (multi-tenant) ve çoklu lokasyon (multi-location) destekleyen bir Depo Yönetim Sistemi (WMS) geliştiriyoruz.

Görevimiz: Faz 1 kapsamında sistemin Çekirdek Çoklu Lokasyon ve Lokalizasyon altyapısını kurmak.

Faz 1'de Yapılacak İşler:
1. Organizasyon, Şirket, Ülke, Şehir, Depo, Yetkilendirme (UserAccess) veri modelini tasarlamak.
2. API isteklerinde Header'dan gelen 'X-Active-Location-ID' ve 'X-Active-Company-ID' bilgilerini JWT yetkileriyle doğrulayıp request-scoped (ThreadLocal) Context nesnesinde saklamak.
3. Zaman verilerini TIMESTAMPTZ (UTC) olarak saklayıp API JSON çıktısında ISO 8601 UTC formatına zorlamak. Sunucu taraflı raporlarda lokasyon timezone dönüşüm servisini yazmak.
4. Ülkelere göre tarih-saat maskelerini ve ondalık/binlik sayı ayıraçlarını API üzerinden arayüzle paylaşmak.
5. Hiyerarşik adres master verilerini (Ülke ➔ Eyalet ➔ Şehir ➔ İlçe ➔ Mahalle) kurup bağımlı seçim API'lerini kodlamak.

Bu aşamada senden beklenen: Her alt adımın kodlamasına geçmeden önce, bu mimarinin genel yapılandırma sınıflarını, temel paket yapısını ve bağımlılıklarını belirlemen. Hazır olduğunda alt modüllerin detaylı promptlarını (Prompt 1.1, 1.2, 6.1 vb.) sırasıyla uygulayarak temiz Java kodlarını üreteceğiz.
```

---

## AŞAMA 1: ORGANİZASYON YAPISI (Prompt 1.1 - 1.4)

### Prompt 1.1: Veri Tabanı Şeması ve JPA Entity Modellerinin Oluşturulması
```text
Rolün: Senior Java & Spring Boot Core Developer.
Görev: Çoklu firma (multi-tenant) ve çoklu depo/lokasyon (multi-location) yapısını destekleyen ilişkisel veritabanı şemasını, JPA Entity sınıflarını ve Spring Data JPA Repository arayüzlerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Hibernate, PostgreSQL, Lombok (isteğe bağlı).

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA Entity modellerini, Spring Data JPA Repository arayüzlerini ve Liquibase/Flyway (veya ham PostgreSQL DDL/SQL) veritabanı şemasını oluştur:

ERD İlişkileri ve Alanlar:
1. Organization (id UUID PK, name String, isActive boolean, createdAt LocalDateTime)
2. Company (id UUID PK, organization FK, name String, taxNumber String, taxOffice String, createdAt LocalDateTime)
3. Country (id UUID PK, isoCode String (Unique, örn: TR, US), name String)
4. Region (id UUID PK, country FK, name String (örn: Marmara, Bayern))
5. Location (id UUID PK, company FK, region FK, name String, type String (Enum), timezone String (IANA format), isActive boolean)
6. Zone (id UUID PK, location FK, name String, type String)
7. User (id UUID PK, username String, email String, passwordHash String, isActive boolean)
8. Role (id UUID PK, name String (örn: WarehouseManager, Picker), permissions String/Jsonb)
9. UserAccess (id UUID PK, user FK, company FK, location FK (nullable), role FK)

Özel Kurallar:
- JPA Annotations: Her Entity için @Entity, @Table, Primary Key'ler için @Id ve UUID üretimi için @GeneratedValue(strategy = GenerationType.UUID) kullan.
- İlişkiler: İlişkiler için @ManyToOne ve @OneToMany annotasyonlarını fetch=FetchType.LAZY olacak şekilde tanımla. Cascade davranışlarını veritabanını koruyacak şekilde ayarla.
- Soft Delete: Tüm ana tablolarda 'isActive' alanı bulunmalı ve veri fiziksel olarak silinmek yerine bu alan false yapılarak pasifleştirilmelidir. Hibernate'in @SQLDelete ve @Where (veya @SQLRestriction - Spring Boot 3 / Hibernate 6 standardı) yapılandırmalarını uygula.
- UserAccess tablosundaki 'location' alanı null olabilir. Eğer null ise, kullanıcı o şirkete bağlı tüm lokasyonlarda (depolarda) yetkili kabul edilecektir.
- Lombok Annotations: Sınıf temizliği için @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, Clean Code prensiplerine uygun, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 1.2: OncePerRequestFilter ve API Yetkilendirme Katmanı (Security Context)
```text
Rolün: Senior Spring Security & API Gateway Architect.
Görev: Spring Boot projesinde, API isteklerinde kullanıcının aktif olarak işlem yaptığı Lokasyon (Location Context) ve Şirket (Company Context) bilgilerini güvenli bir şekilde doğrulamak ve Spring Security Context veya ThreadLocal yapısında saklamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Security.

Aşağıdaki iş akışına göre Spring `OncePerRequestFilter` yapısını ve Context Yönetim servisini kodla:

Akış ve Kurallar:
1. Kullanıcı her API isteği gönderdiğinde HTTP Header'da şu bilgileri iletmelidir:
   - 'X-Active-Company-ID' (UUID formatında aktif firma ID'si)
   - 'X-Active-Location-ID' (UUID formatında aktif depo/lokasyon ID'si)
2. Bir custom Security Filter (`TenantContextFilter` veya benzeri OncePerRequestFilter) oluştur ve:
   - İstek geldiğinde SecurityContextHolder'dan (veya JWT claims üzerinden) aktif kullanıcı ID'sini al.
   - Veritabanındaki `UserAccess` tablosunu kontrol ederek, bu kullanıcının Header'dan gelen 'X-Active-Company-ID' ve 'X-Active-Location-ID' ikilisine yetkisi olup olmadığını sorgula.
   - Eğer yetkisi yoksa veya header parametreleri eksikse anında HTTP 403 Forbidden veya HTTP 401 Unauthorized hata yanıtı dön.
3. Yetkilendirme başarılı ise:
   - 'userId', 'companyId' ve 'locationId' bilgilerini o istek yaşam döngüsü boyunca (request-scope) güvenli şekilde saklamak üzere bir `TenantContextHolder` (InheritableThreadLocal kullanan yardımcı sınıf) içine yaz.
4. İstek tamamlandığında ThreadLocal sızıntılarını (memory leak) önlemek için filter'ın finally bloğunda context'i mutlaka temizle (`clear()`).
5. Bu Context nesnesine backend servislerinden kolayca erişebilmek için bir Java Bean veya static utility class yapısı oluştur.

Bana OncePerRequestFilter, ThreadLocal tabanlı Context Sınıfı ve örnek bir Spring REST Controller entegrasyonu içeren temiz Java kodlarını üret.
```

---

## AŞAMA 2: SAAT DİLİMİ (TIMEZONE) YÖNETİMİ (Prompt 6.1 - 6.3)

### Prompt 6.1: JPA Entity ve Jackson ObjectMapper Zaman Standartları (JPA & Jackson Config)
```text
Rolün: Senior Java Developer & Date-Time Expert.
Görev: Sistemde zaman kaymalarını (date-shift) önlemek amacıyla veritabanı zaman saklama formatlarını standarda bağlamak ve REST API JSON çıktılarını UTC formatında serialize edecek Jackson yapılandırmasını kurmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL (Hibernate 6).

Aşağıdaki entegrasyon kurallarını uygula:

İsterler ve Kurallar:
1. JPA Modellerinde Zaman Alanları:
   - Tüm tarih-saat alanları için Java 8+ standardı olan `java.time.Instant` veya `java.time.OffsetDateTime` tiplerini kullan.
   - Bu alanları PostgreSQL veritabanındaki `TIMESTAMPTZ` (timestamp with time zone) tipiyle Hibernate üzerinden eşleştir.
2. Saat Dilimi Alanları (Veri Tabanı Hiyerarşisi):
   - `Location` (Depo) Entity sınıfına `timezone` alanı ekle (Örn: VARCHAR, IANA formatında 'Europe/Istanbul', 'Asia/Dubai' saklayacak şekilde).
   - `User` (Kullanıcı) Entity sınıfına `preferredTimezone` alanı ekle (Kullanıcının profil tercihi için, nullable VARCHAR).
3. Jackson Serialization Konfigürasyonu:
   - Spring Boot application.yml veya bir custom `Jackson2ObjectMapperBuilderCustomizer` Spring Bean'i yazarak:
     - Zaman verilerini her zaman UTC (Z soneki ile) formatında yazdır (`WriteDatesAsTimestamps = false`).
     - JSON serialization sırasında tarih saat çıktısını şu formatta ver: `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`.

Bana veritabanı eşleşmelerini içeren örnek bir Entity sınıfını, Jackson konfigürasyon sınıfını ve yml ayarlarını temiz Java kodu olarak üret.
```

### Prompt 6.2: Sunucu Tarafı Saat Dilimi Dönüştürme Servisi (Timezone Utility Service)
```text
Rolün: Senior Java Core Developer.
Görev: Veritabanında UTC olarak saklanan zaman verilerini, PDF/Excel raporlarında veya e-posta şablonlarında basılmak üzere hedef lokasyonun veya kullanıcının yerel saat dilimine (IANA timezone) hatasız dönüştüren servis sınıfını kodlamak.

Teknoloji Stack'i: Java 21 (Java Time API - ZonedDateTime, ZoneId, Instant), Spring Boot 3.x.

Aşağıdaki kurallara uygun `TimezoneService` sınıfını kodla:

Metot ve Kurallar:
1. Servis metodu imzasını şu şekilde tanımla:
   `LocalDateTime convertToLocalTime(Instant utcInstant, String targetTimezone)`
2. Metot, veritabanından gelen `Instant` (UTC) verisini, IANA timezone string parametresini (`ZoneId.of(targetTimezone)`) kullanarak `ZonedDateTime` yapısına dönüştürmeli, ardından yaz saati (Daylight Saving Time - DST) farklarını da hesaba katarak lokal `LocalDateTime` nesnesi olarak geri dönmelidir.
3. Bağlamsal Çözümleme Metodu (Contextual Resolver):
   - Parametre olarak gelen aktif kullanıcı ve depo bağlamına göre hedef timezone'u dinamik çözen bir metot ekle:
     `String resolveTargetTimezone(UUID userId, UUID locationId)`
   - Eğer kullanıcının `preferredTimezone` değeri tanımlıysa onu dön. Tanımlı değilse (null ise) deponun (`Location.timezone`) saat dilimini geri dön.
4. Yaz Saati Değişim Testi:
   - Yazılan kodların yaz saati geçiş günlerinde (Örn: Mart sonu veya Ekim sonu geçişleri) saati doğru hesaplayıp hesaplamadığını doğrulayan bir JUnit 5 test sınıfı yaz.

Bana `TimezoneService` implementasyonunu ve JUnit 5 test sınıfını temiz Java kodu standartlarında üret.
```

### Prompt 6.3: Raporlama Sorguları için Gün Kayması Çözümleme Yardımcısı (Date Shift Resolver)
```text
Rolün: Senior SQL & Data Access Developer.
Görev: Tarih bazlı raporlama filtrelerinde (Örn: "2026-01-10" tarihindeki tüm işlemler), depo lokasyonunun yerel saatine göre gün başlangıcı (00:00:00) ve gün bitişini (23:59:59) UTC karşılıklarına çevirerek veritabanı sorgu parametrelerini hazırlayan yardımcı sınıfı yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Senaryo ve Kurallar:
1. Kullanıcı arayüzden (Örn: İstanbul Deposu - Europe/Istanbul, UTC+3) tarih filtresi gönderir:
   - `startDate = LocalDate.of(2026, 1, 10)`
   - `endDate = LocalDate.of(2026, 1, 10)`
2. Eğer bu tarih filtreleri veritabanına doğrudan UTC gün başlangıcı (`2026-01-10T00:00:00Z` - `2026-01-10T23:59:59Z`) olarak gönderilirse, İstanbul saatiyle 10 Ocak gecesi saat 00:00 ile 03:00 arasındaki işlemler 9 Ocak'a kayacak, saat dilimi farkı nedeniyle rapor hatalı çıkacaktır.
3. Bu hatayı önlemek için bir `DateRangeUtcQueryHelper` sınıfı yaz:
   - `InstantRange convertToUtcRange(LocalDate startDate, LocalDate endDate, String locationTimezone)`
   - Bu metot, lokasyonun yerel saat dilimini baz alarak, yerel gün başlangıcını (`2026-01-10T00:00:00` Europe/Istanbul ➔ UTC karşılığı: `2026-01-09T21:00:00Z`) ve yerel gün bitişini (`2026-01-10T23:59:59.999` Europe/Istanbul ➔ UTC karşılığı: `2026-01-10T20:59:59.999Z`) hesaplayıp bir `InstantRange` record/dto nesnesi dönmelidir.
4. Bu yardımcı sınıfı kullanan örnek bir Spring Data JPA Specification veya Custom Repository sorgusu kodla.

Bana bu yardımcı sınıfı, DTO nesnesini ve örnek JPA repository sorgusunu içeren Java kodlarını üret.
```

---

## AŞAMA 3: FORMAT BİÇİMLENDİRMELERİ (Prompt 7.1 - 7.3)

### Prompt 7.1: Ülke ve Lokasyon Format Ayarları JPA Modelleri ve DB Şeması
```text
Rolün: Senior Java & Database Designer.
Görev: Ülke ve lokasyon bazlı tarih, saat ve sayısal biçimlendirme kurallarını saklayacak ilişkisel veritabanı şemasını, JPA Entity sınıf yapılarını ve Spring Data JPA Repository arayüzlerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. CountryFormatConfig (id UUID PK, countryId UUID (Unique), dateFormat String (örn: dd.MM.yyyy, MM/dd/yyyy), timeFormat String (örn: HH:mm, hh:mm a), decimalSeparator String (örn: ',' veya '.'), thousandSeparator String (örn: '.' veya ','), updatedAt LocalDateTime)
2. LocationFormatOverride (id UUID PK, locationId UUID (Unique), dateFormat String, timeFormat String, decimalSeparator String, thousandSeparator String, updatedAt LocalDateTime)

Özel Kurallar:
- Hiyerarşik Yapı: Bir deponun (Location) özel format ayarları yoksa (LocationFormatOverride kaydı boşsa), bağlı olduğu ülkenin varsayılan `CountryFormatConfig` ayarları geçerli olmalıdır.
- Sayısal Biçimlendirme Desteği: Sayı ve tutar gösterimlerinin de lokalizasyonla uyumlu olması için ondalık (`decimalSeparator`) ve binlik (`thousandSeparator`) ayraç alanlarını şemaya dahil et.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 7.2: Sunucu Tarafı Belge ve Rapor Biçimlendirme Servisi (Format Builder)
```text
Rolün: Senior Java Developer & Report Localization Expert.
Görev: Veritabanındaki UTC tarih-saat verilerini ve büyük sayısal tutarları (BigDecimal), raporlarda (PDF/Excel) basılmak üzere ilgili lokasyonun biçimlendirme formatına (date/time/number format) dönüştüren Java servisini yazmak.

Teknoloji Stack'i: Java 21 (DateTimeFormatter, DecimalFormat, BigDecimal), Spring Boot 3.x, Spring Data JPA.

Aşağıdaki kurallara uygun `ReportFormatterService` sınıfını kodla:

Metot ve Kurallar:
1. Tarih Biçimlendirme Metodu:
   `String formatDateTime(Instant utcInstant, UUID locationId, String targetTimezone)`
   - Metot, `Instant` zaman damgasını parametre olarak gelen timezone'a çevirmeli, ardından o deponun `date_format` ve `time_format` kurallarını birleştirerek (Örn: `dd.MM.yyyy HH:mm`) `DateTimeFormatter` yardımıyla string formatında dönmelidir.
2. Sayı Biçimlendirme Metodu:
   `String formatNumber(BigDecimal amount, UUID locationId)`
   - Metot, lokasyonun ondalık (`decimalSeparator`) ve binlik (`thousandSeparator`) ayraç kurallarını okumalıdır.
   - Java `DecimalFormat` ve `DecimalFormatSymbols` sınıflarını kullanarak `BigDecimal` tutarını bu ayraçlara göre biçimlendirmelidir (Örn: `1250.50` tutarını TR için `1.250,50` , US için `1,250.50` formatına çevirmelidir).

Bana bu biçimlendirme fonksiyonlarını içeren `ReportFormatterService` implementasyonunu ve bir JUnit 5 test sınıfını temiz Java kodu olarak üret.
```

### Prompt 7.3: İstemci ve Arayüz Format Bilgi Paylaşım API'si (Format Configuration API)
```text
Rolün: Senior Web API Developer.
Görev: Kullanıcı arayüzünün (Frontend/Mobil) aktif depo lokasyonuna göre input maskelerini, tarih seçim (datepicker) bileşenlerini ve sayı alanlarını dinamik olarak biçimlendirebilmesi için gerekli olan format konfigürasyon API'sini yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Kurallar ve API Tasarımı:
1. Etkin Format API (GET /api/formats/active):
   - `TenantContextHolder` üzerinden aktif depo (`locationId`) ve bağlı olduğu `countryId` bilgilerini oku.
   - `LocationFormatOverride` tablosunda bu depoya özel bir ayar olup olmadığını sorgula. Varsa bu ayarı kullan.
   - Depoya özel ayar yoksa, ülkenin varsayılan `CountryFormatConfig` kaydını sorgula.
   - Sonuç olarak arayüze şu JSON DTO nesnesini dön:
     `{ "dateFormat": "dd.MM.yyyy", "timeFormat": "HH:mm", "decimalSeparator": ",", "thousandSeparator": "." }`
2. Formatta Standartlaştırma:
   - Dönülen format maskesi standart Java/C# DateTime format karakterlerine (örn: yyyy, MM, dd, HH, mm) uygun olmalıdır. Arayüz bu formatı kendi tarih kütüphanelerine (Örn: moment.js veya date-fns) mapleyerek input maskelerinde kullanacaktır.

Bana bu format belirleme mantığını yürüten servis metodunu, REST DTO nesnesini ve REST Controller sınıfını temiz Java kodu olarak üret.
```

---

## AŞAMA 4: HİYERARŞİK ADRES MASTER DATA (Prompt 13.1 - 13.3)

### Prompt 13.1: Hiyerarşik Adres Master Veri JPA Modelleri (JPA & DB Schema)
```text
Rolün: Senior Java & Database Designer.
Görev: Sistemde adres tutarlılığını sağlamak amacıyla kullanılacak ilişkisel hiyerarşik (Ülke ➔ Eyalet ➔ Şehir ➔ İlçe ➔ Mahalle) master adres tablolarını, JPA modellerini ve PostgreSQL DDL kodlarını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki hiyerarşik kurallara uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. Country (id UUID PK, isoCode String (Unique, örn: TR, US), name String, isActive boolean)
2. StateProvince (id UUID PK, country FK, name String, code String (nullable), isActive boolean)
3. City (id UUID PK, country FK, stateProvince FK (nullable, eyaleti olmayan ülkeler için), name String, isActive boolean)
4. District (id UUID PK, city FK, name String, isActive boolean)
5. Neighborhood (id UUID PK, district FK, name String, zipCode String, isActive boolean)

Özel Kurallar:
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Performans: Hiyerarşik aramalarda ve JOIN işlemlerinde hızı artırmak için her tablonun FK alanları ve 'name' / 'isActive' kolonları üzerinde indeksler (Index) tanımla.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 13.2: Bağımlı Seçim REST API Endpointleri (Cascading Dropdowns API)
```text
Rolün: Senior Backend & REST API Developer.
Görev: Kullanıcı arayüzünde (Dropdown seçim kutularında) kullanılmak üzere, bir önceki seçime bağlı alt adres kırılımlarını dinamik ve filtreleyerek çeken Spring MVC REST controller sınıflarını yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Aşağıdaki API standartlarına uygun endpoint'leri kodla:

REST API Tasarımı:
1. GET /api/address/countries: Aktif ülkelerin listesini dönsün.
2. GET /api/address/states?countryId={id}: Belirtilen ülkeye ait aktif eyalet/bölgeleri dönsün.
3. GET /api/address/cities?countryId={id}&stateId={id}: Eyalete bağlı (veya eyalet yoksa doğrudan ülkeye bağlı) aktif şehirlerin listesini dönsün.
4. GET /api/address/districts?cityId={id}: Belirtilen şehre bağlı aktif ilçeleri dönsün.
5. GET /api/address/neighborhoods?districtId={id}: Belirtilen ilçeye bağlı aktif mahalleleri (varsayılan posta kodlarıyla birlikte) dönsün.

Gereksinimler:
- API yanıtlarını hafif tutmak için sadece `id`, `name` ve varsa `code`/`zipCode` alanlarını içeren optimize edilmiş DTO (veya Spring JPA Projection) yapıları kullan.
- Verilerin sadece aktif (`isActive = true`) olanlarını listele.

Bana bu DTO/Projection yapılarını, Spring Data JPA Query/Specification metotlarını ve REST Controller sınıfını temiz Java kodu olarak üret.
```

### Prompt 13.3: Adres Master Verisi Çapraz Doğrulama Servisi (Hierarchy Validation Service)
```text
Rolün: Senior Java Core Developer.
Görev: API üzerinden yeni bir müşteri veya depo adresi kaydedilirken, gönderilen İlçe, Şehir ve Ülke ID'lerinin hiyerarşik olarak birbirine bağlı ve tutarlı olduğunu doğrulayan (manipülasyonu önleyen) servis katmanını yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Doğrulama Senaryosu ve Kurallar:
1. Bir adres kayıt isteğinde `countryId`, `cityId`, `districtId` ve `neighborhoodId` bilgileri API'ye ulaşır.
2. `AddressMasterValidationService` sınıfını oluştur ve doğrulama metodunu yaz:
   `void validateAddressHierarchy(UUID countryId, UUID cityId, UUID districtId, UUID neighborhoodId)`
3. Doğrulama Algoritması:
   - Veritabanından `Neighborhood` kaydını oku. Bu mahallenin bağlı olduğu `districtId` ile istekteki `districtId` eşleşiyor mu?
   - `District` kaydını oku. Bu ilçenin bağlı olduğu `cityId` ile istekteki `cityId` eşleşiyor mu?
   - `City` kaydını oku. Bu şehrin bağlı olduğu `countryId` ile istekteki `countryId` eşleşiyor mu?
   - Zincirleme olarak bu ilişkilerden herhangi biri bozuksa veya kayıtlar aktif değilse (`isActive = false`), bir `BusinessException` fırlatarak işlemi engelle.
4. Bu servisin doğruluğunu test eden JUnit 5 test sınıfını yaz.

Bana bu hiyerarşik doğrulama servisini, exception fırlatma mantığını ve JUnit 5 test sınıfını temiz Java kodu olarak üret.
```
