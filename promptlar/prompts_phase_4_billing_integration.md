# Faz 4: Faturalama & ERP Entegrasyonları (LLM Kod Üretim Akışı)

Bu dosya, Faz 4 kapsamında yer alan modülleri sırasıyla Claude 3.5 Sonnet'e kodlatabilmeniz için tek bir akışta birleştirilmiştir.

---

## AŞAMA 0: MAKRO YÖNLENDİRME PROMPTU
*Bu promptu Faz 4'e başlarken yeni bir chat penceresinde Claude'a gönderin.*

```text
Rolün: Lead Integration Architect & Senior Backend Engineer.
Proje Bağlamı: Java 21, Spring Boot 3.x, JPA ve PostgreSQL kullanan WMS projemizde finansal motor, kurallar katmanı ve çekirdek yapı başarıyla tamamlandı.

Görevimiz: Faz 4 kapsamında "Çoklu Para Birimli Faturalama" süreçlerini tamamlamak ve sistemi harici ERP'lerle entegre edecek güvenli "Integration Framework" yapısını kurmak.

Faz 4'de Yapılacak İşler:
1. Fatura başlık ve satır detaylarını orijinal döviz ve yerel para karşılıklarıyla hesaplayıp kaydeden, onaylanan faturada kur güncellenmesini engelleyen (Rate Lock) faturalama modülünü kodlamak.
2. Fatura tarihi ile ödeme tarihi kurları arasındaki farktan kaynaklanan kur farklarını hesaplayıp loglayan modülü yazmak.
3. Farklı depolar için farklı ERP entegrasyon tiplerini (SAP, Oracle, Logo vb.) Spring context'ten dinamik çözen 'ErpAdapterFactory' ve 'IErpAdapter' arayüzlerini (Adapter Pattern) yazmak.
4. Ağ kesintilerinde veya ERP arızalarında veri kaybı yaşanmaması için işlemleri stok hareketiyle tek transaksiyonda 'Outbox' tablosuna yazan, ardından bir Spring Scheduler yardımıyla kilitli okuma (Pessimistic Lock) ve katlanarak artan yeniden deneme süreleriyle (Exponential Backoff) asenkron gönderen 'Outbox Worker' altyapısını kurmak.
5. Başarısız entegrasyon işlerinin listelenebileceği ve elle zorlayarak yeniden denetilebileceği (Force Retry) API'leri ve 30 günlük log temizleme job'ını yazmak.

Bu aşamada senden beklenen: Entegrasyon altyapısının hata tolerans mimarisini (Outbox & Retry) ve gevşek bağlı adaptör yapısını (Adapter Pattern) kurgulamandır.
```

---

## AŞAMA 1: PARA BİRİMLİ FATURALAMA VE KUR FARKI (Prompt 11.1 - 11.3)

### Prompt 11.1: Fatura ve Kalemleri (Invoice & Invoice Items) JPA Modelleri ve DB Şeması
```text
Rolün: Senior Java & Database Designer.
Görev: Faturaların başlık (Header) ve satır (Item) detaylarını, işlem para birimi ve yerel muhasebe para birimi karşılıklarıyla birlikte hatasız saklayacak veri tabanı şemasını ve JPA modellerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. Invoice (id UUID PK, invoiceNumber String (Unique), customerId UUID, locationId UUID, issueDate LocalDateTime, invoiceCurrency FK (EUR, USD vb.), accountingCurrency FK (TRY, EUR vb.), exchangeRateDate LocalDate, exchangeRateValue BigDecimal (Precision: 18, 6), subtotalOriginal BigDecimal (Precision: 18, 4), taxAmountOriginal BigDecimal (Precision: 18, 4), grandTotalOriginal BigDecimal (Precision: 18, 4), grandTotalAccounting BigDecimal (Precision: 18, 4), status String (Enum: DRAFT, APPROVED, SENT_TO_ERP, CANCELLED), createdAt LocalDateTime)
2. InvoiceItem (id UUID PK, invoice FK, itemDescription String, quantity BigDecimal (Precision: 18, 4), unitPriceOriginal BigDecimal (Precision: 18, 4), discountOriginal BigDecimal (Precision: 18, 4), taxRate BigDecimal (Precision: 5, 2 - Örn: 20.00), taxAmountOriginal BigDecimal (Precision: 18, 4), lineTotalOriginal BigDecimal (Precision: 18, 4))
3. ExchangeDifferenceLog (id UUID PK, invoice FK, calculationDate LocalDateTime, originalPaidAmount BigDecimal, rateAtPayment BigDecimal (Precision: 18, 6), exchangeDifferenceAmount BigDecimal (Precision: 18, 4), actionTaken String)

Özel Kurallar:
- Decimal Formatı: Yuvarlama kayıplarını engellemek için tüm tutar alanlarını `BigDecimal` (numeric(18,4)), kur değerlerini ise `BigDecimal` (numeric(18,6)) olarak tanımla.
- Kur Değeri Dondurma (Rate Locking): Faturalama anındaki kur değerini (`exchangeRateValue`) faturanın kendi tablosuna kalıcı olarak kopyala (Döviz tablosuna doğrudan referans verme).
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 11.2: Fatura Hesaplama ve Döviz Çevrim Servisi (Billing Calculation Engine)
```text
Rolün: Senior Java Backend Developer & Finance Specialist.
Görev: Fatura oluşturulurken, satır kalemlerinin ara toplamlarını ve vergilerini orijinal para biriminde hesaplayan, ardından fatura genel toplamını kilitli kur üzerinden yerel muhasebe para birimine çeviren hesaplama motorunu kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Aşağıdaki iş kurallarına uygun `InvoiceCalculationService` sınıfını kodla:

Hesaplama Kuralları ve Metotlar:
1. Fatura Hesaplama Metodu:
   `InvoiceDto calculateInvoice(List<InvoiceItemInputDto> items, UUID customerId, UUID locationId, String invoiceCurrencyCode, LocalDate rateDate)`
2. Kur Çözümleme:
   - `CurrencyConversionService` aracılığıyla, belirtilen `rateDate` tarihindeki ve `SELLING` (Satış) tipindeki kur değerini oku ve faturanın `exchangeRateValue` alanına ata.
3. Orijinal Para Birimi Hesaplamaları (EUR/USD vb.):
   - Her bir fatura satırı için:
     `lineTotalOriginal = (quantity * unitPriceOriginal) - discountOriginal`
     `taxAmountOriginal = lineTotalOriginal * (taxRate / 100)`
     (Bu işlemleri BigDecimal kullanarak, Banker's Rounding standardına göre satır bazında yuvarla).
   - Fatura genel toplamları için:
     `subtotalOriginal = Satırların lineTotalOriginal toplamı`
     `taxAmountOriginal = Satırların taxAmountOriginal toplamı`
     `grandTotalOriginal = subtotalOriginal + taxAmountOriginal`
4. Muhasebe Para Birimi Çevrimi (TRY vb.):
   - `grandTotalAccounting = grandTotalOriginal * exchangeRateValue` (Şirketin ana para birimi hassasiyetine göre yuvarla).
5. Durum Kontrolü (Rate Lock):
   - Fatura onaylandığında (`status = 'APPROVED'`), kur değeri ve kur tarihi alanlarının güncellenmesini engelleyecek kısıtı (JPA PreUpdate listener veya service validation) ekle.

Bana `InvoiceCalculationService` implementasyonunu ve bir JUnit 5 test sınıfını temiz Java kodu olarak üret.
```

### Prompt 11.3: Fatura Kur Farkı Hesaplama ve Loglama Yardımcı Servisi
```text
Rolün: Senior Java Core Developer.
Görev: Cari hesap tahsilatları veya ödemeler yapıldığında, fatura kuru ile ödeme tarihi kuru arasındaki kur farklarını hesaplayıp kaydeden yardımcı servisi yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Kurallar ve Hesaplama Mantığı:
1. Kur Farkı Hesaplama Servisi: `ExchangeDifferenceService` sınıfını oluştur.
2. Servis metodu imzasını şu şekilde tanımla:
   `ExchangeDifferenceLog calculateAndLogExchangeDifference(UUID invoiceId, BigDecimal paidAmountOriginal, BigDecimal rateAtPayment)`
3. Hesaplama Algoritması:
   - `Invoice` tablosundan faturayı ve faturanın kesildiği andaki kur değerini (`exchangeRateValue`) oku.
   - Ödeme tarihi kuru ile fatura kuru arasındaki farkı bul: `rateDifference = rateAtPayment - invoice.getExchangeRateValue()`.
   - Kur farkı tutarını hesapla: `exchangeDifferenceAmount = paidAmountOriginal * rateDifference` (BigDecimal ve Banker's Rounding ile).
   - Çıkan farkı `ExchangeDifferenceLog` tablosuna ilgili fatura ID'siyle asenkron veya transaksiyonel olarak kaydet.
4. Bu hesaplamanın doğruluğunu test eden (artı kur farkı / eksi kur farkı senaryolarıyla) bir JUnit 5 test sınıfı yaz.

Bana bu kur farkı hesaplama servisini ve test sınıfını temiz Java kodu standartlarında üret.
```

---

## AŞAMA 2: ENTEGRASYON VE OUTBOX ALTYAPISI (Prompt 4.1 - 4.4)

### Prompt 4.1: Adaptör Tasarım Deseni Arayüzleri ve Spring Bean Fabrikası (Adapter Pattern)
```text
Rolün: Senior Java Architect & Integration Expert.
Görev: Sistemin farklı depolarda kullanılan farklı ERP sistemleriyle (SAP, Oracle, Logo vb.) gevşek bağlı (loose coupling) çalışmasını sağlayacak Adaptör Desenini (Adapter Pattern) Spring Boot standartlarında kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring WebClient (HTTP çağrıları için).

Aşağıdaki kurallara ve sınıf yapılarına göre kodları oluştur:

Arayüz ve Sınıf Yapısı:
1. `ErpAdapter` (Interface):
   - `ErpResponse sendMaterialCard(MaterialDto material)`
   - `ErpResponse sendInventoryMovement(MovementDto movement)`
   - `ErpResponse sendInvoice(InvoiceDto invoice)`
   - `List<ExchangeRateDto> fetchExchangeRates()`
2. `SapAdapter` (Class - implements ErpAdapter):
   - Spring WebClient kullanarak SAP REST API servislerine JSON istekleri göndersin.
3. `LogoAdapter` (Class - implements ErpAdapter):
   - Verileri XML veya CSV formatına dönüştürüp, bir SFTP sunucusuna (Örn: JSch veya Spring Integration SFTP kullanarak) dosya bazlı aktarım yapsın.
4. `ErpAdapterFactory` (Class):
   - Spring uygulama bağlamını (ApplicationContext) enjekte etsin.
   - Parametre olarak gelen lokasyon ID'sine göre veritabanından aktif ERP entegrasyon tipini sorgulasın ve Spring Container'dan ilgili adaptör bean'ini (`SapAdapter`, `LogoAdapter` vb.) dinamik olarak çözüp (`context.getBean`) geri dönsün.

Bana `ErpAdapter` arayüzünü, somut adaptör sınıflarını (mock HTTP ve SFTP çağrılarıyla) ve dinamik `ErpAdapterFactory` yapısını içeren temiz Java kodlarını üret.
```

### Prompt 4.2: Entegrasyon Yapılandırması ve Loglama JPA Modelleri (Database & JPA Entities)
```text
Rolün: Senior Java & Database Developer.
Görev: Entegrasyon tanımlarını, lokasyon bazlı bağlantı bilgilerini ve entegrasyon hareket günlüğünü tutacak JPA modellerini ve PostgreSQL DDL şemasını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. IntegrationSystem (id UUID PK, code String (Unique, örn: SAP, ORACLE, LOGO, MIKRO), name String, isActive boolean)
2. LocationIntegrationConfig (id UUID PK, locationId UUID, integrationSystem FK, connectionType String (Enum: REST, SOAP, SFTP, DB), connectionParams Jsonb (Bağlantı URL'leri, API Keyler ve Klasör yolları için JSONB), isActive boolean, updatedAt LocalDateTime)
3. IntegrationJob (id UUID PK, code String (Unique, örn: MAT_SYNC, STOCK_MOVE, INVOICE_SYNC), name String, direction String (Enum: INBOUND, OUTBOUND))
4. IntegrationLog (id UUID PK, locationIntegrationConfig FK, integrationJob FK, status String (Enum: SUCCESS, FAILED, RETRYING), requestPayload Text, responsePayload Text, errorMessage Text, retryCount int, createdAt LocalDateTime, lastAttemptAt LocalDateTime)

Özel Kurallar:
- JPA Annotations: connectionParams alanını PostgreSQL JSONB tipiyle eşleştirmek için Hibernate 6 uyumlu `@JdbcTypeCode(SqlTypes.JSON)` annotasyonunu kullan.
- Performans: `IntegrationLog` tablosundaki `status`, `createdAt` ve `locationIntegrationConfig` alanları üzerinde veritabanı indeksleri tanımla.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 4.3: İşlemsel Outbox Deseni ve Spring Scheduler ile Güvenilir Entegrasyon
```text
Rolün: Senior Spring Boot Developer & Messaging Specialist.
Görev: WMS sistemi üzerinde bir işlem gerçekleştiğinde (Örn: Stok hareketi) bunu veritabanına ve bir outbox tablosuna atomik olarak yazıp, arka planda çalışan bir Scheduler (Outbox Worker) vasıtasıyla güvenli bir şekilde ERP'ye gönderen Outbox Desenini (Outbox Pattern) kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, `@Scheduled` (Zamanlayıcı).

İsterler ve Kurallar:
1. `OutboxMessage` Entity sınıfı oluştur: (id UUID PK, aggregateType String, aggregateId UUID, payload Text (JSON formatında DTO verisi), status String (Enum: PENDING, PROCESSING, COMPLETED, FAILED), retryCount int, createdAt LocalDateTime, nextAttemptAt LocalDateTime).
2. Bir stok hareketi kaydedildiğinde, Spring'in `@Transactional` anotasyonunu kullanarak stok hareketi kaydı ile `OutboxMessage` kaydını tek bir veritabanı transaksiyonu içinde atomik olarak yaz.
3. `IntegrationOutboxWorker` (Scheduler) sınıfı oluştur:
   - Her 5 saniyede bir tetiklensin (`@Scheduled(fixedDelay = 5000)`).
   - Durumu `PENDING` veya `FAILED` olan ve `nextAttemptAt` zamanı gelmiş en eski 50 kaydı veritabanından kilitli olarak (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) çeksin.
   - Her kayıt için `ErpAdapterFactory` üzerinden ilgili lokasyonun adaptörünü alsın ve asenkron olarak ERP sistemine göndersin.
4. Hata ve Yeniden Deneme (Exponential Backoff):
   - Gönderim başarılıysa outbox durumunu `COMPLETED` olarak güncelle.
   - Gönderim başarısızsa (HTTP timeout, ERP kapalı vb.), `retryCount` değerini artır. Eğer limit aşılmadıysa (Örn: max 3 retry) durumunu `FAILED` olarak bırak, `nextAttemptAt` süresini katlanarak artır (Örn: 2^retryCount dakika sonrasına ata). Limit aşıldıysa durumunu kalıcı hata `FAILED_MAX_RETRIES` yap ve admin paneli için alarm oluştur.

Bana OutboxMessage Entity sınıfını, atomik transaksiyon kaydedici örnek servis metodunu ve Outbox Worker Scheduler kodlarını temiz Java diliyle üret.
```

### Prompt 4.4: Manuel Yeniden Deneme (Retry API) ve İzleme Endpointleri
```text
Rolün: Senior Web API Developer.
Görev: Sistem yöneticilerinin entegrasyon hatalarını görebileceği, log detaylarını sorgulayabileceği ve başarısız olmuş entegrasyon işlerini elle yeniden tetikleyebileceği (Force Retry) API endpoint'lerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Kurallar ve API Tasarımı:
1. Log Listeleme API (GET /api/integrations/logs):
   - `status`, `locationId` ve `integrationJobCode` parametrelerine göre filtrelenebilir ve sayfalama (Pageable) destekleyen bir entegrasyon log sorgusu çalıştır.
   - Logların request ve response payload verilerini DTO olarak dönsün.
2. Manuel Yeniden Tetikleme API (POST /api/integrations/logs/{logId}/retry):
   - Belirtilen `logId`'ye sahip başarısız entegrasyon kaydını bul.
   - Bu loga bağlı orijinal veriyi okuyarak, ilişkili OutboxMessage kaydını bul ve durumunu yeniden `PENDING`, `nextAttemptAt` değerini ise `LocalDateTime.now()` olarak güncelle.
   - Outbox worker zamanlayıcısının bu kaydı hemen işlemesini sağla.
3. Log Silme/Arşivleme Job'ı:
   - 30 günden eski `SUCCESS` durumundaki entegrasyon loglarını veritabanından temizleyen aylık çalışan bir temizlik görevi (Scheduled Task) ekle.

Bana bu servis metodunu, REST Controller sınıfını ve veritabanı arşivleme scheduler kodlarını temiz Java kodu olarak üret.
```
