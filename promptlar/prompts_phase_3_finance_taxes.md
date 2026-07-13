# Faz 3: Finansal Altyapı, Döviz & Vergi Motoru (LLM Kod Üretim Akışı)

Bu dosya, Faz 3 kapsamında yer alan modülleri sırasıyla Claude 3.5 Sonnet'e kodlatabilmeniz için tek bir akışta birleştirilmiştir.

---

## AŞAMA 0: MAKRO YÖNLENDİRME PROMPTU
*Bu promptu Faz 3'e başlarken yeni bir chat penceresinde Claude'a gönderin.*

```text
Rolün: Lead Financial Software Architect & Tax System Expert.
Proje Bağlamı: Java 21, Spring Boot 3.x, JPA ve PostgreSQL kullanan WMS projemizde hiyerarşik yapı ve kurallar motorları kuruldu.

Görevimiz: Faz 3 kapsamında sistemin "Çoklu Para Birimi, Döviz Kuru ve Vergi Hesaplama Motoru" modüllerini kodlamak.

Faz 3'de Yapılacak İşler:
1. Finansal işlemleri BigDecimal formatında original_amount, exchange_rate ve converted_amount kırılımıyla saklayan veri modelini kurmak.
2. Çevrimlerde Banker's Rounding (HALF_EVEN) uygulamak. Kurlar bulunamadığında geçmiş tarihe fallback (max 5 gün) yapan motoru yazmak.
3. Müşteri kartlarında varsayılan ve izin verilen döviz kısıtlamalarını tanımlayıp sipariş anında API seviyesinde doğrulamak.
4. Her gün 15:35'te TCMB XML servisinden kurları otomatik çeken Scheduled Crawler yazmak. Manuel kur güncellemelerini audit log'a kaydetmek.
5. Vergi oranlarını geçerlilik tarihleriyle versiyonlayıp çakışmaları önlemek. Ülke, Lokasyon, Müşteri ve Ürün bazlı dinamik çözümlenen vergi oranını bulmak.
6. Strategy Pattern kullanarak vergi dahil, hariç ve katmanlı (ÖTV+KDV) vergi hesaplayan ve matrah/hesaplama detaylarını izlenebilirlik (log) tablosuna yazan 'Tax Engine' modülünü geliştirmek.

Bu aşamada senden beklenen: Finansal hesaplamalarda yuvarlama hassasiyet standartlarını ve vergi motorunun tasarım desenini (Strategy Pattern) projemize entegre etmendir.
```

---

## AŞAMA 1: ÇOKLU PARA BİRİMİ VE MÜŞTERİ DÖVİZ KISITLARI (Prompt 8.1 - 8.3 & 9.1 - 9.3)

### Prompt 8.1: Çok Seviyeli Para Birimi ve Finansal İşlemler JPA Modelleri (JPA & DB Schema)
```text
Rolün: Senior Java & Database Designer.
Görev: Sistem genelinde para birimi tanımlarını ve tüm finansal işlemleri (stok hareket maliyetleri, faturalar, teklifler vb.) orijinal ve yerel para birimleriyle çift yönlü saklayacak veri tabanı şemasını ve JPA modellerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. Currency (id UUID PK, code String (Unique, örn: USD, EUR, TRY), symbol String (örn: $, €, ₺), decimalPlaces int (örn: USD için 2, JPY için 0), name String, isActive boolean)
2. SystemConfig (id UUID PK, defaultCurrency FK)
3. Company (id UUID PK, baseCurrency FK, ...)
4. Location (id UUID PK, localCurrency FK, ...)
5. Customer (id UUID PK, defaultCurrency FK, ...)
6. Contract (id UUID PK, customer FK, currency FK, contractCode String, ...)
7. FinancialTransaction (id UUID PK, companyId UUID, locationId UUID, contract FK (nullable), originalCurrency FK, originalAmount BigDecimal (Precision: 18, 4), exchangeRate BigDecimal (Precision: 18, 6), baseCurrency FK, convertedAmount BigDecimal (Precision: 18, 4), transactionDate Instant)

Özel Kurallar:
- Decimal Hassasiyeti (BigDecimal): Finansal tutarlar ve oranlar için float/double kullanılmamalıdır. Tutar alanları için `BigDecimal` (PostgreSQL'de `numeric(18,4)`), kur oranları için `BigDecimal` (PostgreSQL'de `numeric(18,6)`) kullanılmalıdır.
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 8.2: Banker's Rounding ve Geçmiş Kur Arama Destekli Kur Çevrim Motoru
```text
Rolün: Senior Java Developer & Financial Calculations Expert.
Görev: Finansal işlemlerde yuvarlama hatalarını önlemek için Banker's Rounding (HALF_EVEN) algoritması uygulayan ve belirtilen tarihe ait kur bulunamadığında geçmişe dönük kur sorgulaması (fallback) yapan kur çevrim motorunu yazmak.

Teknoloji Stack'i: Java 21 (BigDecimal, RoundingMode.HALF_EVEN), Spring Boot 3.x, Spring Data JPA, Redis.

Aşağıdaki kurallara uygun `CurrencyConversionService` sınıfını kodla:

Metot ve Kurallar:
1. Çevrim Metodu:
   `ConversionResultDto convert(BigDecimal amount, String sourceCurrency, String targetCurrency, Instant transactionDate, String rateType)`
   (rateType: BUYING, SELLING, EFFECTIVE_BUYING, EFFECTIVE_SELLING).
2. Kur Arama & Caching:
   - Kur çevrimi için öncelikle Redis cache'e bak (Cache key deseni: "rate:source:target:date:rateType").
   - Cache Miss durumunda: Veritabanındaki `ExchangeRate` tablosundan ilgili günün kurunu çek.
3. Geçmiş Gün Kuru Arama (Weekend / Holiday Fallback):
   - Eğer işlem tarihindeki gün için kur verisi bulunamazsa (Örn: hafta sonları veya resmi tatillerde kur açıklanmaz), kur motoru en fazla **5 gün geriye** giderek en yakın geçmiş tarihteki kuru sorgulamalıdır. 5 gün boyunca kur bulunamazsa exception fırlatılmalıdır.
4. Banker's Rounding (HALF_EVEN):
   - Çevrilen tutarı (`originalAmount * rate`), hedef para biriminin `decimalPlaces` değerine göre (Örn: TRY/USD için 2 hane, JPY için 0 hane) Java `RoundingMode.HALF_EVEN` kullanarak yuvarla.
5. Sonuç DTO:
   - Metot; orijinal tutar, kur değeri, çevrilmiş tutar ve kullanılan kur tarihini içeren bir `ConversionResultDto` dönmelidir.

Bana `CurrencyConversionService` implementasyonunu ve doğruluğu kanıtlayan JUnit 5 test sınıfını temiz Java kodu olarak üret.
```

### Prompt 8.3: Hiyerarşik Para Birimi Belirleme Servisi (Currency Resolver Service)
```text
Rolün: Senior Backend & Domain Logic Developer.
Görev: Bir envanter hareketinde veya faturalandırma işleminde hangi para biriminin geçerli olacağını projenin seviye hiyerarşisine (Sözleşme > Müşteri > Depo > Şirket) göre dinamik çözen servisi kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Kurallar ve Hiyerarşi Mantığı:
1. Bir işlem için geçerli aktif para birimini belirlemek üzere `CurrencyResolverService` sınıfı yaz.
2. Servisin çözümleme yöntemi şu hiyerarşik sırayı takip etmelidir:
   - 1. Adım: Eğer işlem doğrudan bir **Sözleşmeye (Contract)** bağlıysa, sözleşmenin para birimi (`Contract.currency`) geçerlidir.
   - 2. Adım: Sözleşme yoksa, ilişkili **Müşteri Kartındaki (Customer)** varsayılan para birimi (`Customer.defaultCurrency`) geçerlidir.
   - 3. Adım: Müşteri kartında tanımlı değilse, işlemin yapıldığı **Deponun (Location)** yerel para birimi (`Location.localCurrency`) geçerlidir.
   - 4. Adım: Depoda da yoksa, ilgili **Şirketin (Company)** ana para birimi (`Company.baseCurrency`) veya sistem genelindeki varsayılan para birimi geçerlidir.
3. Çözümleme metodunun imzası:
   `Currency resolveTransactionCurrency(UUID companyId, UUID locationId, UUID customerId, UUID contractId)`

Bana bu hiyerarşik mantığı koşan servis sınıfını ve örnek bir entegrasyon metodunu temiz Java kodu olarak üret.
```

### Prompt 9.1: Müşteri Kartı Döviz Limitleri ve İzinleri JPA Modelleri ve DB Şeması
```text
Rolün: Senior Java & Database Designer.
Görev: Müşteri bazında varsayılan ve izin verilen alternatif para birimi kısıtlamalarını, kur tiplerini ve kur kaynak kurallarını tutacak ilişkisel veri tabanı şemasını ve JPA modellerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. Customer (id UUID PK, name String, defaultCurrency FK (Customer default currency), invoicingCurrency FK (Target billing currency), rateType String (Enum: BUYING, SELLING etc.), rateSource String (Enum: TCMB, MANUAL etc.), exchangeDiffPreference String (Enum: PER_INVOICE, MONTHLY, NONE), isActive boolean)
2. CustomerPermittedCurrency (id UUID PK, customer FK, currency FK)
3. CustomerContract (id UUID PK, customer FK, contractCode String, currency FK (Contract fixed currency), startDate LocalDateTime, endDate LocalDateTime)

Özel Kurallar:
- Benzersiz Kısıt: `CustomerPermittedCurrency` tablosunda `customer` ve `currency` alanlarının birleşimi benzersiz (UniqueConstraint) olmalıdır.
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 9.2: Müşteri Para Birimi Doğrulama Servisi ve API Validasyon Katmanı
```text
Rolün: Senior Java Core Developer & Security Architect.
Görev: Sipariş (Order) veya Fatura (Invoice) girişi yapıldığında, seçilen para biriminin o müşteri için izin verilen listesinde olup olmadığını doğrulayan ve yetkisiz işlemleri engelleyen API doğrulama katmanını kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Validation.

Aşağıdaki iş akışına göre doğrulama mekanizmasını kodla:

Akış ve Kurallar:
1. Bir doğrulama servisi (`CustomerCurrencyValidator`) oluştur:
   - `void validateCurrencyPermission(UUID customerId, UUID currencyId)`
   - Servis, müşterinin varsayılan para birimini (`Customer.defaultCurrency`) ve izin verilen diğer para birimleri tablosunu (`CustomerPermittedCurrency`) kontrol etmelidir.
   - Müşterinin varsayılan para birimi, izinli para birimleri tablosunda tanımlı olmasa dahi otomatik olarak izinli kabul edilmelidir.
   - Eğer seçilen para birimi izinli listesinde yoksa, custom bir `InvalidCustomerCurrencyException` fırlat.
2. Spring `@ControllerAdvice` (Global Exception Handler) sınıfı yaz:
   - `InvalidCustomerCurrencyException` hatasını yakalasın.
   - HTTP 400 Bad Request durumu ve standart bir hata JSON formatı (timestamp, message, details) ile istemciye yanıt dönsün.
3. Bu doğrulamayı tetikleyen örnek bir `OrderService.createOrder(OrderRequestDto request)` metodunu ve controller çağrısını kodla.

Bana doğrulama servisini, exception sınıfını, controller advice kodunu ve örnek servis metodunu içeren Java kodlarını üret.
```

### Prompt 9.3: Sözleşme Uyum ve Kur Farkı Hesaplama Kuralları (Business Rules Service)
```text
Rolün: Senior Spring Boot Developer.
Görev: Yeni müşteri sözleşmeleri eklenirken para birimi uyumunu denetleyen ve faturalardaki kur farkı hesaplama tercihlerini yöneten iş kuralı servislerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Kurallar ve İş Mantığı:
1. Sözleşme Para Birimi Validasyonu (Contract Currency Check):
   - Yeni bir `CustomerContract` kaydedilirken, sözleşmenin para biriminin müşterinin izin verilen para birimleri listesinde (`CustomerPermittedCurrency`) veya varsayılan para biriminde olduğunu doğrula. Yetkisizse sözleşme kaydını engelle ve hata fırlat.
2. Müşteri Özel Kur Parametreleri:
   - Bir müşteri için kur bilgisi çekileceğinde, müşterinin kartında (`Customer`) tanımlı olan `rateSource` (Örn: TCMB, MANUAL vb.) ve `rateType` (Örn: BUYING, SELLING) alanlarına göre dinamik olarak kur sorgulayan yardımcı metodu yaz:
     `ExchangeRateDto getCustomerRate(UUID customerId, String sourceCurrency, String targetCurrency, Instant date)`
3. Kur Farkı Tercihi Kontrolü (Exchange Difference Rule Resolver):
   - Müşterinin `exchangeDiffPreference` ayarına (PER_INVOICE, MONTHLY, NONE) göre kur farkı hesaplamasının çalışıp çalışmayacağını kontrol eden basit bir durum yönetim metodunu yaz.

Bana bu iş kurallarını koşan servis sınıfını ve doğrulama metotlarını içeren Java kodlarını üret.
```

---

## AŞAMA 2: DÖVİZ KURU VE ENTEGRASYON YÖNETİMİ (Prompt 10.1 - 10.3)

### Prompt 10.1: Kur Kayıtları ve Audit Log JPA Modelleri ve DB Şeması
```text
Rolün: Senior Java & Database Designer.
Görev: Günlük döviz kurlarını ve yetkili kullanıcılar tarafından yapılan manuel kur güncellemelerinin denetim tarihçesini (Audit Log) saklayacak veritabanı şemasını ve JPA modellerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. ExchangeRate (id UUID PK, rateDate LocalDate, sourceCurrency FK, targetCurrency FK, rateType String (Enum: BUYING, SELLING, EFFECTIVE_BUYING, EFFECTIVE_SELLING), rateSource String (Enum: TCMB, MANUAL, ERP, BANK, CONTRACT), rate BigDecimal (Precision: 18, 6), createdAt LocalDateTime, updatedAt LocalDateTime)
2. ExchangeRateAuditLog (id UUID PK, exchangeRate FK, actionType String (Enum: INSERT, UPDATE, DELETE), oldRate BigDecimal (Precision: 18, 6, nullable), newRate BigDecimal (Precision: 18, 6), userId UUID (nullable), changedAt LocalDateTime)

Özel Kurallar:
- Benzersiz Kısıt: `ExchangeRate` tablosunda `rateDate`, `sourceCurrency`, `targetCurrency`, `rateType` ve `rateSource` alanlarının birleşimi benzersiz (UniqueConstraint) olmalıdır. Aynı tarihte aynı kaynaktan mükerrer kur girilmesini engelle.
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 10.2: Otomatik Günlük TCMB Döviz Kuru Çekim Görevi (Crawler Sync Job)
```text
Rolün: Senior Java Integration Developer.
Görev: Her gün belirlenen saatte (Türkiye saati ile 15:30) TCMB'nin XML servisinden kurları çekip parse eden ve veritabanına otomatik kaydeden planlanmış arka plan görevini (Scheduled Task) kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, WebClient veya RestTemplate, Spring @Scheduled.

Aşağıdaki entegrasyon kurallarına göre sınıfı kodla:

Akış ve Kurallar:
1. Bir `@Component` veya `@Service` sınıfı oluştur: `TcmbRateSyncJob`.
2. Her gün Türkiye saati ile 15:35'te çalışacak bir cron zamanlayıcı ekle (`@Scheduled(cron = "0 35 15 * * *", zone = "Europe/Istanbul")`).
3. Çalıştığında, `https://www.tcmb.gov.tr/kurlar/today.xml` adresine HTTP GET isteği gönder.
4. Gelen XML verisini Java standart XML parser (DocumentBuilder/XPath veya Jackson XML Dataformat) ile parse et.
5. XML içerisindeki USD, EUR, GBP, SAR, AED gibi dövizlerin Döviz Alış, Döviz Satış, Efektif Alış ve Efektif Satış değerlerini oku.
6. Bu değerleri `ExchangeRate` tablosuna `rateSource = 'TCMB'` olacak şekilde ekle veya güncellenmişse güncelle (Upsert mantığı).
7. İstek sırasında ağ hatası veya XML ayrıştırma hatası alınırsa, bu hatayı logla (Logger) ve yöneticiye bildirim gönderilmesi için e-posta servisine (veya mock bildirim servisine) sinyal gönder.

Bana bu otomatik entegrasyon işini koşan `TcmbRateSyncJob` kodlarını temiz Java kodu olarak üret.
```

### Prompt 10.3: Manuel Kur Yönetim API'leri, Audit Loglama ve Cache Temizliği
```text
Rolün: Senior Web API Developer.
Görev: Sistem yöneticilerinin döviz kurlarını manuel olarak ekleyip güncelleyebileceği API'leri, bu güncellemelerin audit log kaydını ve Redis cache temizliğini (Eviction) gerçekleştiren REST controller servislerini yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Data Redis.

Kurallar ve API Tasarım:
1. Kur Ekleme/Güncelleme API (POST /api/rates/manual):
   - İstek gövdesinde `sourceCurrency`, `targetCurrency`, `rateDate`, `rateType` ve `rate` (BigDecimal) bilgilerini al.
   - Veritabanında daha önce bu tarihte ve kaynakta bir kur olup olmadığını sorgula.
   - Eğer varsa güncelleme yap ve `ExchangeRateAuditLog` tablosuna `actionType = 'UPDATE'` olacak şekilde eski kur ve yeni kur değerlerini, işlemi yapan kullanıcı UUID'siyle birlikte yaz.
   - Eğer yeni ekleniyorsa, `ExchangeRateAuditLog` tablosuna `actionType = 'INSERT'` olacak şekilde `oldRate = null` olacak şekilde kayıt at.
2. Redis Cache Eviction:
   - Manuel kur güncellemesi yapıldıktan sonra, bu kurla ilişkili Redis üzerindeki tüm önbellek verilerini temizle (Örn: "rate:USD:TRY:2026-07-03:BUYING" anahtarını sil).
3. Haftasonu Fallback Arama Sorgusu Entegrasyonu:
   - `ExchangeRateRepository` içinde, belirli bir tarihe ait kur bulunamadığında geriye doğru en yakın kur kaydını bulan SQL veya JPQL sorgusunu yaz:
     `Optional<ExchangeRate> findLatestRateBeforeDate(LocalDate date, UUID sourceCurrencyId, UUID targetCurrencyId, String rateType)`

Bana kural yönetim servis metodunu, REST Controller endpoint'ini ve geçmiş kur arama (fallback) JPQL sorgusunu içeren Java kodlarını üret.
```

---

## AŞAMA 3: VERGİ ORANLARI VE VERSİYONLAMA (Prompt 14.1 - 14.3)

### Prompt 14.1: Tarih Versiyonlamalı Vergi Oranları JPA Modelleri (JPA & DB Schema)
```text
Rolün: Senior Java & Database Designer.
Görev: Farklı ülkelerdeki vergi tiplerini (KDV, VAT, GST vb.) ve bu vergilere ait Ülke, Lokasyon, Müşteri ve Ürün bazlı dinamik atanabilen ve tarih aralıklarıyla (temporal) versiyonlanan vergi oranlarını saklayacak veri tabanı şemasını ve JPA modellerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. TaxType (id UUID PK, code String (Unique, örn: KDV, VAT, GST, ÖTV, STOPAJ), name String, isActive boolean)
2. TaxRate (id UUID PK, taxType FK, countryId UUID, locationId UUID (nullable), customerId UUID (nullable), productType String (nullable, örn: FOOD, ELECTRONIC), operationType String (nullable), rate BigDecimal (Precision: 5, 2 - Örn: 20.00), startDate LocalDate, endDate LocalDate (nullable), isActive boolean, createdAt LocalDateTime)
3. TaxRateAuditLog (id UUID PK, taxRate FK, actionType String (Enum: INSERT, UPDATE_RATE, EXPIRE), oldRate BigDecimal (Precision: 5, 2, nullable), newRate BigDecimal (Precision: 5, 2), userId UUID, changedAt LocalDateTime)

Özel Kurallar:
- Zaman Serisi Yapısı (Temporal Data): Vergi oranı değişikliklerinde mevcut oran doğrudan güncellenmez. Eski oranın geçerlilik süresi `endDate` değeri verilerek sonlandırılır ve yeni oran için yeni bir `TaxRate` satırı (yeni start/end tarihlerine sahip) eklenir.
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 14.2: En Yakın Eşleşme (Best-Match) Öncelikli Vergi Oranı Çözümleme Servisi
```text
Rolün: Senior Java Developer & Domain Logic Architect.
Görev: Bir fatura veya operasyonel işlem gerçekleştiğinde, işlem tarihi ve bağlam parametrelerini (Lokasyon, Müşteri, Ürün Tipi vb.) kullanarak veritabanından en uygun ve geçerli vergi oranını çözüp (resolve) getiren motoru yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Aşağıdaki kurallara uygun `TaxResolutionService` sınıfını kodla:

Metot ve Kurallar:
1. Çözümleme Metodu:
   `BigDecimal resolveTaxRate(String taxTypeCode, LocalDate transactionDate, TaxResolutionContext context)`
   (TaxResolutionContext: countryId, locationId, customerId, productType, operationType parametrelerini taşır).
2. SQL/JPQL Sorgusu:
   - Veritabanından, işlem tarihinde geçerli olan (`startDate <= transactionDate` ve `endDate == null` veya `endDate >= transactionDate` olan) aktif vergi oranı kurallarını sorgula.
3. Öncelik Çözümleme Algoritması (Best Match):
   - Çekilen kuralları en özelden (en çok parametre eşleşen) en genele (sadece ülke varsayılanı) doğru şu hiyerarşiyle süz ve ilk eşleşen oranı dön:
     - 1. Öncelik: `Location` + `Customer` + `Product Type` eşleşmesi.
     - 2. Öncelik: `Location` + `Product Type` eşleşmesi.
     - 3. Öncelik: `Customer` + `Product Type` eşleşmesi.
     - 4. Öncelik: `Product Type` eşleşmesi.
     - 5. Öncelik (En Genel): `Country` standart vergi oranı.
4. Bu çözümleme algoritmasının doğruluğunu test eden JUnit 5 test sınıfını yaz.

Bana bu vergi çözümleme motorunu (`TaxResolutionService`), context sınıfını ve test kodlarını temiz Java kodu olarak üret.
```

### Prompt 14.3: Tarih Güvenlikli Vergi Sürüm Yönetimi ve Audit Log API'leri
```text
Rolün: Senior Backend Developer.
Görev: Sistem yöneticilerinin vergi oranlarını güncellerken geçmişe dönük kayıtları bozmayacak şekilde tarih güvenlikli sürüm yönetimi yapan ve değişiklikleri loglayan API'leri kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

İş Akışı ve Kurallar:
1. Vergi Oranı Güncelleme API (POST /api/taxes/rates/update):
   - İstek parametreleri: `taxRateId` (güncellenecek kural), `newRate` (BigDecimal) ve `effectiveDate` (yeni oranın geçerli olacağı başlangıç tarihi).
   - Servis içi güncelleme adımları:
     - Güncellenecek eski `TaxRate` kaydını bul.
     - Eski kaydın `endDate` alanını `effectiveDate.minusDays(1)` olarak set et (Eski kaydı kapat).
     - Yeni oran için yeni bir `TaxRate` satırı oluştur: `startDate = effectiveDate`, `endDate = null` ve `rate = newRate`.
     - `TaxRateAuditLog` tablosuna eski oran ve yeni oran bilgiyle audit kaydı at.
     - Bu iki işlemi tek bir `@Transactional` metot içinde çalıştır.
2. Tarih Çakışma Kontrolü (Conflict Validation):
   - Yeni oran eklenirken veya güncellenirken, aynı vergi tipi ve bağlam için mükerrer/çakışan tarih aralıklarının oluşmasını engelle (Örn: Aynı deponun aynı tarihte iki farklı KDV oranı olamaz).

Bana bu tarih güvenlikli güncelleme metodunu ve REST Controller sınıfını temiz Java kodu olarak üret.
```

---

## AŞAMA 4: VERGİ HESAPLAMA MOTORU (Prompt 15.1 - 15.3)

### Prompt 15.1: Strateji Tasarım Deseni ile Vergi Hesaplama Algoritmaları (Strategy Pattern)
```text
Rolün: Senior Java Software Architect & Financial Calculations Expert.
Görev: Sistemdeki farklı vergi hesaplama modellerini (Vergi dahil, Vergi hariç, Katmanlı/Çoklu vergi vb.) esnek ve genişletilebilir şekilde yönetebilecek Strateji Desenini (Strategy Pattern) kodlamak.

Teknoloji Stack'i: Java 21 (BigDecimal, MathContext), Spring Boot 3.x.

Aşağıdaki arayüz ve sınıf tasarımlarına göre kodları oluştur:

Sınıf ve Arayüz Tasarımı:
1. `TaxCalculationStrategy` (Interface):
   - `TaxCalculationResult calculateTax(BigDecimal baseAmount, BigDecimal rate)`
2. `TaxExclusiveStrategy` (Class - implements TaxCalculationStrategy):
   - Formül: `TaxAmount = BaseAmount * (Rate / 100)`, `GrandTotal = BaseAmount + TaxAmount`.
3. `TaxInclusiveStrategy` (Class - implements TaxCalculationStrategy):
   - Formül: `BaseAmount = GrandTotal / (1 + (Rate / 100))`, `TaxAmount = GrandTotal - BaseAmount`.
4. `CompoundTaxStrategy` (Class - implements TaxCalculationStrategy):
   - Bir ürüne katmanlı olarak birden fazla vergi uygulanması durumunu (Örn: ÖTV'li tutar üzerinden KDV hesaplama) destekleyecek şekilde iç içe nested strateji listesi çalıştırsın.
5. `TaxCalculationResult` (DTO Record):
   - `BigDecimal baseAmount` (Matrah)
   - `BigDecimal taxAmount` (Vergi Tutarı)
   - `BigDecimal grandTotal` (Genel Toplam)

Özel Kural:
- Yuvarlama kurallarında finansal kayıpları engellemek için sadece `BigDecimal` kullan ve yuvarlama modu olarak `RoundingMode.HALF_EVEN` (Banker's Rounding) tercih et.

Bana bu strateji arayüzünü, somut strateji implementasyonlarını ve test senaryolarını içeren temiz Java kodlarını üret.
```

### Prompt 15.2: Vergi Hesaplama İzlenebilirliği (Audit Log) JPA Modelleri ve DB Şeması
```text
Rolün: Senior Java & Database Designer.
Görev: Yapılan her vergi hesaplama işleminin matrahını, oranını, hesaplanan vergi tutarını ve yasal muafiyet kodlarını denetime uygun şekilde izlenebilir olarak saklayacak veri tabanı şemasını ve JPA modellerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. TaxCalculationLog (id UUID PK, transactionType String (örn: INVOICE_LINE, TRANSACTION_FEE), transactionReferenceId UUID (İlgili işlemin PK'sını tutacak generic ID), taxType FK, taxRate BigDecimal (Precision: 5, 2), taxBaseAmount BigDecimal (Precision: 18, 4), calculatedTaxAmount BigDecimal (Precision: 18, 4), isInclusive boolean, isExempt boolean, exemptionCode String (nullable), calculationSource String (Örn: TAX_ENGINE_V1), calculationDate LocalDateTime)

Özel Kurallar:
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Performans: Hızlı raporlama ve filtreleme yapabilmek için `transactionType` ve `transactionReferenceId` alanlarının birleşimi üzerinde veritabanı indeksi (Index) tanımla.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 15.3: Vergi Hesaplama Motoru (Tax Engine Service) ve Kur Farkı Vergisi
```text
Rolün: Senior Java Backend Developer & Tax Expert.
Görev: Faturalandırma veya stok hareketlerinde araya girerek, çözümlenmiş vergi oranlarına göre stratejileri tetikleyen, hesaplanan tutarları loglayan ve kur farkları üzerinden vergi hesaplayan ana vergi motorunu kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Aşağıdaki kurallara uygun `TaxEngine` servisini yaz:

İş Akışı ve Kurallar:
1. Hesaplama Metodu:
   `List<TaxCalculationResultDto> calculate(TaxEngineContext context)`
   - Metot, işlem kalemlerini (Items) ve uygulanacak vergi kurallarını almalı, `TaxCalculationStrategy` factory üzerinden doğru stratejiyi çözüp hesaplamayı yapmalıdır.
2. Hesaplama İzleme (Write Logs):
   - Hesaplanan her satır vergisini veritabanındaki `TaxCalculationLog` tablosuna asenkron veya transaction içinde kaydet.
3. Kur Farkı Vergisi Hesaplama (Forex Tax Calculation):
   - Kur farkından kaynaklı oluşan finansal kazançlar üzerinden vergi hesaplamak için bir alt metot yaz:
     `TaxCalculationResultDto calculateExchangeDifferenceTax(BigDecimal exchangeDifferenceAmount, String taxTypeCode, LocalDate date, UUID locationId)`
   - Bu işlemde vergi matrahı (`taxBaseAmount`) olarak kur farkı tutarı (`exchangeDifferenceAmount`) kullanılmalı ve ilgili vergi oranı bu farka uygulanmalıdır.
4. Muafiyet Yönetimi:
   - Eğer kuralda muafiyet (`isExempt = true`) tanımlıysa, vergi tutarını otomatik sıfır (`0.00`) olarak hesapla ve veritabanına yasal muafiyet kodu (`exemptionCode`) ile birlikte kaydet.

Bana bu ana vergi motorunu (`TaxEngine`) ve test senaryolarını içeren Java kodlarını üret.
```
