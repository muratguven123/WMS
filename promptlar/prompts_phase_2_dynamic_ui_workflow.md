# Faz 2: Dinamik UI & Parametrik İş Akışı (LLM Kod Üretim Akışı)

Bu dosya, Faz 2 kapsamında yer alan modülleri sırasıyla Claude 3.5 Sonnet'e kodlatabilmeniz için tek bir akışta birleştirilmiştir.

---

## AŞAMA 0: MAKRO YÖNLENDİRME PROMPTU
*Bu promptu Faz 2'ye başlarken yeni bir chat penceresinde Claude'a gönderin.*

```text
Rolün: Lead Software Architect & Rules Engine Expert.
Proje Bağlamı: Java 21, Spring Boot 3.x, Spring Data JPA ve PostgreSQL kullanan WMS projemizde Faz 1 başarıyla tamamlandı. Artık sistem aktif lokasyon ve kullanıcı bilgilerini context'ten okuyabiliyor.

Görevimiz: Faz 2 kapsamında "Dinamik UI ve Parametrik İş Akışları" motorlarını (Rules Engine) inşa etmek.

Faz 2'de Yapılacak İşler:
1. Depo bazında iş akışı adımlarının sırasını (sequence) ve zorunluluğunu parametrik yöneten bir 'Workflow Engine' ve 'Validator' servisleri kurmak.
2. Arayüz formlarının (Örn: Mal Kabul Formu, Müşteri Kartı Formu) alanlarını ve doğrulamalarını, aktif lokasyon/rol/operasyon tipi gibi bağlamlara göre (Priority mantığıyla) dinamik çözümleyip arayüze JSON Şeması olarak dönen 'Dynamic UI Engine' yazmak.
3. API POST/PUT isteklerinde bu dinamik kuralları sunucu tarafında (Server-side validation) Aspect (AOP) ile doğrulayan kısıtları kodlamak.
4. Adres verilerini veritabanında JSONB olarak saklamak ve ülkelere göre dinamik zorunluluk/regex doğrulamalarını bu JSONB alanlar üzerinde uygulamak.

Bu aşamada senden beklenen: Faz 2 kurallar motorunun genel çalışma mimarisini, caching stratejisini (Redis) ve interceptor/AOP yapılandırmasını tasarlamandır. Sonrasında alt promptları çalıştıracağız.
```

---

## AŞAMA 1: SÜREÇ KONFİGÜRASYONU (Prompt 3.1 - 3.4)

### Prompt 3.1: Parametrik Süreç Hiyerarşisi JPA Entity Modelleri ve DB Şeması
```text
Rolün: Senior Java & Database Architect.
Görev: Depo iş akışı adımlarının lokasyon bazlı özelleştirilebilmesini sağlayan veri tabanı şemasını, JPA Entity sınıflarını ve Spring Data JPA Repository arayüzlerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. ProcessDefinition (id UUID PK, code String (Unique, örn: INBOUND, OUTBOUND), name String, isActive boolean)
2. ProcessStepDefinition (id UUID PK, processDefinition FK, code String (Unique, örn: QC, SERIAL_CONTROL, CUSTOMS_CONTROL), name String, defaultSequence int)
3. LocationProcessConfig (id UUID PK, locationId UUID, processDefinition FK, isActive boolean, updatedAt LocalDateTime)
4. LocationProcessStepConfig (id UUID PK, locationProcessConfig FK, processStepDefinition FK, sequence int, isActive boolean, isMandatory boolean, responsibleRoleId UUID (nullable), requiresApproval boolean, errorStrategy String (Enum: BLOCK, BYPASS, ROUTE_TO_QUARANTINE), updatedAt LocalDateTime)

Özel Kurallar:
- Benzersizlik Kontrolü (Unique Constraints): LocationProcessStepConfig tablosunda 'locationProcessConfig' ve 'sequence' alanlarının birleşimi benzersiz (UniqueConstraint) olmalıdır. Aynı akış içinde iki farklı adım aynı sıra numarasına (sequence) sahip olamaz.
- JPA Annotations: lazy loading (FetchType.LAZY) ilişkileri kur. UUID üretimi için @GeneratedValue(strategy = GenerationType.UUID) kullan.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 3.2: Runtime İş Akışı Doğrulama Motoru (Workflow Validator Engine)
```text
Rolün: Senior Backend Engineer & Workflow Specialist.
Görev: Depo içerisinde operasyonel bir adım tamamlandığında, o lokasyon için aktif süreç adımlarını kontrol edip sıradaki geçerli adımı belirleyen ve opsiyonel adımların atlanmasını sağlayan dinamik doğrulama motorunu kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Data Redis.

Aşağıdaki iş akışına göre `WorkflowValidatorService` sınıfını kodla:

Akış ve Kurallar:
1. Bir depo görevlisi bir adımı tamamladığında (Örn: Mal Kabul onaylandığında), sistem bir sonraki adımı belirlemek için şu metodu çağıracaktır:
   `ProcessStepDto determineNextStep(UUID locationId, String processCode, String currentStepCode)`
2. Bu metot, ilgili lokasyonun ve sürecin aktif adım konfigürasyonlarını (`LocationProcessStepConfig`) Redis cache veya veritabanından çekmelidir (Redis key deseni: "workflow:locationId:processCode").
3. Adımları `sequence` değerlerine göre sırala.
4. `currentStepCode` sonrasındaki en küçük `sequence` değerine sahip ilk **aktif (`isActive = true`)** adımı bul.
5. Eğer sıradaki adım **zorunlu değilse (`isMandatory = false`)** ve atlanmak isteniyorsa, sistem bir sonraki zorunlu veya aktif adımı bulmak için sıralamada ilerlemeye devam edebilmelidir.
6. Eğer sıradaki adım **zorunlu ise (`isMandatory = true`)**, sistem bu adımı zorunlu yönlendirme olarak dönmelidir.
7. Eğer süreçte başka aktif adım kalmadıysa süreci "COMPLETED" olarak işaretle.

Bana bu dinamik doğrulama motorunu (`WorkflowValidatorService`), Redis entegrasyonunu ve örnek servis test kodlarını temiz Java kodu standartlarında üret.
```

### Prompt 3.3: Konfigürasyon Değişiklikleri için Audit Logging Altyapısı
```text
Rolün: Senior Spring Boot & Security Developer.
Görev: Sistem yöneticilerinin depo süreç konfigürasyonlarında (`LocationProcessStepConfig`) yaptığı değişiklikleri izlemek ve eski-yeni değerleriyle birlikte veritabanı denetim günlüğüne (Audit Log) kaydetmek.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Security.

İsterler ve Kurallar:
1. Bir Audit Log tablosu oluştur: `ConfigurationAuditLog` (id UUID PK, entityName String, entityId UUID, actionType String (UPDATE, ACTIVATE, DEACTIVATE), changedFieldName String, oldValue String, newValue String, changedByUserId UUID, changedAt LocalDateTime).
2. JPA Entity Listener (`@EntityListeners`) veya Spring AOP kullanarak, `LocationProcessStepConfig` sınıfı üzerinde yapılan güncellemeleri otomatik olarak yakala.
3. Güncelleme anında:
   - Spring Security SecurityContextHolder'dan (veya TenantContextHolder) işlemi yapan kullanıcının UUID bilgisini (`changedByUserId`) al.
   - Hangi alanların (sequence, isActive, isMandatory vb.) değiştiğini reflection veya Hibernate/JPA Interceptor aracılığıyla tespit et.
   - Değişen her alan için eski değer (`oldValue`) ve yeni değer (`newValue`) bilgilerini içeren kayıtları `ConfigurationAuditLog` tablosuna yaz.
4. Bu loglama işlemini ana veritabanı işlemini yavaşlatmamak için asenkron (Spring `@Async`) veya event-driven (ApplicationEventPublisher) olarak kurgula.

Bana Audit Log Entity sınıfını, JPA Entity Listener veya Interceptor yapısını ve asenkron loglama servis kodlarını temiz Java kodu olarak üret.
```

### Prompt 3.4: Onay Mekanizması Interceptor Yapısı (Approval & Role Check)
```text
Rolün: Senior Spring Boot & Security Developer.
Görev: Bir adımın yürütülmesi için yetkili rol kontrolü yapan ve yönetici onayı gerektiren (`requiresApproval = true`) adımlarda işlemi onay beklemeye alan interceptor/aspect mekanizmasını yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring AOP.

Kurallar ve Senaryolar:
1. Rol Yetki Kontrolü:
   - Eğer `LocationProcessStepConfig` üzerinde `responsibleRoleId` tanımlanmışsa, işlemi gerçekleştiren kullanıcının rolünün bu rol ile eşleşip eşleşmediğini kontrol et. Eşleşmiyorsa `BusinessException` (HTTP 403) fırlat.
2. Yönetici Onay Kontrolü (Approval Logic):
   - Eğer işlem yapılacak adımda `requiresApproval = true` ise, sistem işlemi doğrudan kaydetmek yerine durumunu "PENDING_APPROVAL" (Onay Bekliyor) olarak işaretlemeli ve bir `ApprovalRequest` kaydı oluşturmalıdır.
   - Bu kontrolü metodun üzerinde çalışacak bir custom annotation (`@CheckWorkflowStep`) ve Spring AOP Aspect (`WorkflowAspect`) ile kurgula. Aspect, metot çalışmadan önce araya girip bu veritabanı ayarlarını okumalıdır.
3. Hata Stratejisi Yönetimi (Error Strategy):
   - Adım sırasında bir hata oluştuğunda `errorStrategy` değerini oku:
     - `BLOCK`: İşlemi durdur ve hata fırlat.
     - `ROUTE_TO_QUARANTINE`: İşlemi başarılı kıl ancak ilgili ürünü otomatik olarak "Quarantine Zone" (Karantina Alanı) içine yönlendiren bir alt metot tetikle.
     - `BYPASS`: Sadece uyarı logu yazıp işlemi normal akışında tamamla.

Bana `@CheckWorkflowStep` annotation yapısını, AOP Aspect sınıfını, onay istek mekanizmasını ve hata stratejilerini yöneten Java kodlarını üret.
```

---

## AŞAMA 2: DİNAMİK UI ALAN YÖNETİMİ (Prompt 5.1 - 5.4)

### Prompt 5.1: Dinamik Form Bileşenleri ve Kural Tabloları JPA Modelleri
```text
Rolün: Senior Java & Database Designer.
Görev: Arayüzdeki alanların dinamik davranışlarını (gizli, zorunlu, salt okunur vb.) saklayan ilişkisel veritabanı şemasını, JPA Entity sınıf yapılarını ve Spring Data JPA Repository arayüzlerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. Screen (id UUID PK, code String (Unique, örn: MAT_CARD_FORM, REC_CONTROL_FORM), name String, isActive boolean)
2. ScreenField (id UUID PK, screen FK, fieldKey String (örn: tax_number, district, zip_code), defaultBehavior String (Enum: OPTIONAL, HIDDEN, READ_ONLY), dataType String (Enum: STRING, NUMBER, DATE, SELECT))
3. FieldBehaviorRule (id UUID PK, screenField FK, priority int (Kuralların öncelik sırası), companyId UUID (nullable), countryId UUID (nullable), locationId UUID (nullable), roleId UUID (nullable), operationType String (nullable), behavior String (Enum: MANDATORY, HIDDEN, READ_ONLY, OPTIONAL), defaultValue String (nullable), validationRegex String (nullable), validationErrorMessageKey String (nullable), updatedAt LocalDateTime)

Özel Kurallar:
- JPA Annotations: lazy loading (FetchType.LAZY) ilişkileri kur. UUID üretimi için @GeneratedValue(strategy = GenerationType.UUID) kullan.
- Kurallar esnektir, bu yüzden companyId, countryId, locationId, roleId, ve operationType alanları nullable (boş bırakılabilir) olmalıdır. Boş bırakılan alanlar "tüm durumlar için geçerli" anlamına gelir.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 5.2: Dinamik Kural Çözümleme ve JSON Şeması Üretim Servisi (Evaluator)
```text
Rolün: Senior Core Java Developer & Rules Engine Expert.
Görev: Kullanıcı bir formu açmak istediğinde, aktif lokasyon, rol ve işlem tipi bilgilerini (Context) alarak veritabanındaki çakışan kuralları öncelik sırasına göre filtreleyen ve arayüze (Frontend) göndermek üzere çözümlenmiş bir JSON Şeması (DTO) üreten motoru kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Data Redis.

Aşağıdaki iş akışına göre `DynamicUiService` sınıfını kodla:

Akış ve Kurallar:
1. İstemci belirli bir ekranın form şemasını talep ettiğinde şu servis metodu çağrılmalıdır:
   `ResolvedScreenDto getResolvedScreen(String screenCode, UiContext context)`
   (UiContext nesnesi: locationId, roleId, companyId, countryId, operationType parametrelerini taşır).
2. Metot, veritabanından ilgili ekrana ait tüm `ScreenField` kayıtlarını ve bu alanlara bağlı aktif `FieldBehaviorRule` kurallarını çekmeli ve Redis cache üzerinde sorgulamalıdır (Cache key deseni: "ui:screenCode:locationId:roleId").
3. Kural Çözümleme Algoritması:
   - Her bir alan (ScreenField) için, gelen `UiContext` parametreleri ile tam veya kısmi eşleşen kuralları filtrele (Örn: Gelen context'teki locationId ile kuraldaki locationId eşleşmeli veya kuraldaki locationId null olmalı).
   - Eşleşen kuralları `priority` değerine göre büyükten küçüğe sırala.
   - En yüksek öncelikli (priority) kuralın davranışını (`MANDATORY`, `HIDDEN`, `READ_ONLY`, `OPTIONAL`), varsa `defaultValue` ve `validationRegex` değerlerini o alanın nihai davranışı olarak belirle.
   - Eğer hiçbir kural eşleşmiyorsa alanın `defaultBehavior` değerini uygula.
4. Çıktı olarak, arayüzün dinamik formu çizebilmesi için her alanın adını, veri tipini, nihai davranışını ve validasyon kurallarını içeren bir `ResolvedScreenDto` nesnesi dön.

Bana bu kural çözümleme motorunu (`DynamicUiService`), gerekli DTO sınıflarını ve örnek bir REST endpoint'ini temiz Java kodu olarak üret.
```

### Prompt 5.3: Sunucu Tarafı Dinamik Form Validasyon Katmanı (Server-Side Validator)
```text
Rolün: Senior Spring Boot Core & Security Developer.
Görev: Dinamik alan kurallarının sadece arayüzde (Frontend) değil, veriler veritabanına kaydedilirken de (POST/PUT) sunucu tarafında aynı kurallarla otomatik olarak doğrulanmasını sağlayan validasyon altyapısını kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring AOP.

İsterler ve Kurallar:
1. Bir `@ValidateDynamicForm(screenCode = "REC_CONTROL_FORM")` annotation yapısı oluştur. Bu annotation controller metodunun parametresi olan veri DTO'sunun üzerinde tetiklenecektir.
2. Spring AOP Aspect (`DynamicFormValidationAspect`) yaz:
   - Bu annotation'a sahip metot tetiklenmeden önce araya girsin.
   - İstek atan kullanıcının aktif context bilgilerini (`TenantContextHolder` üzerinden) alsın.
   - `DynamicUiService` sınıfını çağırarak o ekran için geçerli olan nihai çözümlenmiş alan kurallarını hesaplasın.
   - Gelen istek gövdesindeki (Request Body) JSON veri alanlarını (Map formatında veya Reflection ile) okusun.
3. Validasyon Kuralları:
   - Çözümlenmiş kurallara göre `MANDATORY` olan bir alan JSON gövdesinde gönderilmemişse veya boşsa hata fırlat.
   - Gelen alan değeri, kuralda tanımlı olan `validationRegex` deseniyle uyuşmuyorsa, kuraldaki `validationErrorMessageKey` çeviri anahtarını kullanarak hata fırlat.
4. Hata durumunda anlamlı bir validasyon hata DTO'su hazırlayarak `BusinessException` fırlat ve HTTP 400 Bad Request dönmesini sağla.

Bana validasyon annotation yapısını, AOP Aspect sınıfını ve küresel validation exception handling kodlarını temiz Java diliyle üret.
```

### Prompt 5.4: Kural Yönetim API'leri ve Cache Temizliği
```text
Rolün: Senior Backend & Integration Developer.
Görev: Sistem yöneticilerinin ekran alan kurallarını güncelleyebileceği, yeni kural ekleyebileceği ve bu güncellemelerden sonra Redis cache'ini temizleyen kural yönetim servislerini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, Spring Data Redis.

Kurallar ve API Tasarımı:
1. Kural Ekleme/Güncelleme API (POST /api/ui/rules):
   - Admin panelinden gönderilen yeni kural parametrelerini (`FieldBehaviorRule`) al ve veritabanına kaydet.
   - Çakışmaları önlemek için varsayılan öncelik sırasını doğrula: Eğer el ile priority girilmediyse hiyerarşik öncelik ata (Lokasyon = 50, Rol = 40, Şirket = 30, Ülke = 20, Varsayılan = 10).
2. Cache Temizleme (Cache Evict):
   - Kural güncellendiğinde veya silindiğinde, ilgili ekrana ait Redis üzerindeki tüm dinamik UI şema önbelleklerini temizle.
3. Kural Değişiklik Loglama:
   - Kural değişimlerini, hangi alanın hangi kuralının (Örn: "tax_number zorunluluk durumu MANDATORY yapıldı") güncellendiğini kullanıcı ve tarihçe bilgisiyle birlikte denetim günlüğüne (Audit Log) yaz.

Bana kural yönetim servis metodunu, REST Controller endpoint'ini ve cache temizleme mantığını içeren Java kodlarını üret.
```

---

## AŞAMA 3: JSONB TABLANLI DİNAMİK ADRES BİÇİMLENDİRME (Prompt 12.1 - 12.3)

### Prompt 12.1: JSONB Tabanlı Dinamik Adres Yapısı JPA Modelleri (JPA & DB Schema)
```text
Rolün: Senior Java & Database Designer.
Görev: Ülkeye göre değişebilen adres alanlarını veritabanı şemasını bozmadan dinamik olarak saklayabilmek için JSONB destekli JPA modellerini ve PostgreSQL DDL şemasını kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL (Hibernate 6), Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

ERD İlişkileri ve Alanlar:
1. CountryAddressTemplate (id UUID PK, countryId UUID, addressTemplateField FK, isMandatory boolean, sequence int, validationRegex String (nullable), errorMessageKey String (nullable))
2. AddressTemplateField (id UUID PK, fieldKey String (Unique, örn: district, neighborhood, door_no), fieldLabelKey String (örn: fields.district))
3. Address (id UUID PK, countryId UUID, city String, state String, zipCode String, addressDetails Map<String, Object> (JSONB), formattedAddress Text, updatedAt LocalDateTime)

Özel Kurallar:
- JSONB Alan Eşleşmesi: `Address` tablosundaki `addressDetails` alanını Map/POJO olarak tanımla ve Hibernate 6 standardı olan `@JdbcTypeCode(SqlTypes.JSON)` annotasyonuyla PostgreSQL JSONB sütununa eşleştir.
- Performans: `Address` tablosundaki arama kriterleri olan `city`, `state`, `zipCode` alanlarına veritabanı indeksleri tanımla.
- PostgreSQL GIN Index: JSONB tabanlı `addressDetails` üzerinde hızlı sorgu yapabilmek için PostgreSQL GIN Index DDL SQL kodunu (`CREATE INDEX ... USING gin`) hazırla.
- Unicode Desteği: Veritabanı karakter setinin UTF-8 olmasını sağlayan DDL tanımlarını ekle.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

### Prompt 12.2: Sunucu Tarafı Dinamik Adres Regex Doğrulayıcı (Server-Side Validator)
```text
Rolün: Senior Core Java Developer & Validation Specialist.
Görev: Bir adres kaydedilirken (POST/PUT), adresin ait olduğu ülkenin şablonundaki (`CountryAddressTemplate`) zorunlu alan ve regex (düzenli ifade) kurallarına göre `addressDetails` JSONB verisini doğrulayan servisi yazmak.

Teknoloji Stack'i: Java 21 (Pattern, Matcher), Spring Boot 3.x, Spring Data JPA.

Aşağıdaki kurallara uygun `AddressValidationService` sınıfını kodla:

Metot ve Kurallar:
1. Doğrulama Metodu:
   `void validateAddress(AddressDto addressDto)`
2. Doğrulama Algoritması:
   - `AddressDto` içindeki `countryId` değerine göre aktif olan `CountryAddressTemplate` kurallarını veritabanından çek.
   - Her bir şablon kuralı için:
     - Eğer alan zorunluysa (`isMandatory = true`), `addressDetails` JSONB haritasında (Map) bu anahtarın var olduğunu ve boş olmadığını doğrula.
     - Eğer alanda bir regex deseni tanımlıysa (`validationRegex != null`), girilen değerin bu regex deseniyle eşleştiğini doğrula (Örn: Posta Kodu format kontrolü).
3. Hata Yönetimi:
   - Doğrulama başarısız olursa, şablondaki `errorMessageKey` değerini içeren bir `AddressValidationException` fırlat ve Spring `@ControllerAdvice` üzerinden HTTP 400 Bad Request dönmesini sağla.

Bana bu doğrulama servisini, exception sınıfını ve doğruluğu test eden JUnit 5 test sınıfını temiz Java kodu olarak üret.
```

### Prompt 12.3: Adres Birleştirici ve Biçimlendirici Yardımcı Sınıf (Address Formatter)
```text
Rolün: Senior Java Developer.
Görev: Girilen dinamik JSONB adres bileşenlerini, fatura ve sevk irsaliyesi gibi belgelerde basılmak üzere tek satırlık temiz bir metin biçimine (`formattedAddress`) dönüştüren yardımcı servisi yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

İsterler ve Kurallar:
1. Biçimlendirme Metodu:
   `String generateFormattedAddress(UUID countryId, Map<String, Object> addressDetails, String city, String state, String zipCode)`
2. Biçimlendirme Kuralları:
   - Ülke bazlı biçimlendirme şablonunu kod içinde (Switch/Case veya Factory kullanarak) tanımla:
     - Türkiye (TR) için format: "[neighborhood] Mah. [street] Cad. No:[doorNo] D:[apartmentNo] [city]/[state]"
     - ABD (US) için format: "[street] St, [city], [state] [zipCode], USA"
   - JSONB içindeki değerleri bu formata göre birleştir. Eğer bir alan boşsa, geride çirkin virgül veya boşluklar bırakmayacak şekilde temizleme (string cleaning) yap.
3. JPA Lifecycle Entegrasyonu:
   - `@PrePersist` ve `@PreUpdate` annotasyonlarını kullanarak, adres kaydı veritabanına yazılmadan önce `formattedAddress` alanının bu metot aracılığıyla otomatik doldurulmasını sağla.

Bana bu biçimlendirme sınıfını, JPA Entity Listener sınıfını ve test senaryolarını içeren Java kodlarını üret.
```
