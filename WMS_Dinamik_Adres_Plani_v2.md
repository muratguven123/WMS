# WMS Dinamik ve Generic Adres Giriş Sistemi — Tasarım Planı (v2)

Bu plan, hardcoded adres alanları (TR/US ayrımları) yerine, veri tabanındaki ülke şablonlarına
(`CountryAddressTemplate`) göre dinamik olarak şekillenen generic bir adres giriş formu geliştirmeyi
hedefler. v1 planın kod tabanı incelemesinde tespit edilen boşlukları (alan tipi/bağımlılık metadata'sı
eksikliği, city/state'in şablon dışı kalması, iki paralel adres altyapısının birleştirilmemesi) kapatır.

## Mevcut Durum Tespiti (Codebase İncelemesi)

- Backend veri modeli (`Address`, `CountryAddressTemplate`, `AddressTemplateField`) generic yapıyı
  destekliyor; `AddressValidationService` zaten `CountryAddressTemplateRepository` üzerinden
  mandatory/regex kontrolü yapıyor. Şablonu **dışarı açan bir API yok** — bu gerçek bir eksiklik.
- `country_id` tipi eskiden UUID/Long uyumsuzdu, `V14__uuid_to_bigint.sql` ile çözülmüş; entity'ler
  zaten `Long`/bigint. Bu konuda ek işlem gerekmiyor.
- **Kritik boşluk:** `city` ve `state`, `AddressTemplateField` sisteminde tanımlı değil (seed'de sadece
  `district`, `neighborhood`, `zip_code`, `street` var). `Address` entity'sinde bu ikisi sabit sütun.
  Şablon, sadece city/state'in **altındaki** ülkeye özgü alanları tanımlıyor.
- **Kritik boşluk:** Şablonda alan tipi (metin/seçim) ve bağımlılık (parent alan) bilgisi yok. Bunu
  eklemeden UI'da "`fieldKey` district/neighborhood ise select render et" yazmak, `isTR` kontrolünü
  field-key bazlı bir switch'e taşımaktan ibaret kalır — gerçek generic'lik sağlanmaz.
- İki paralel adres altyapısı var: core-service'in sabit 5 seviyeli master-data hiyerarşisi
  (Country → StateProvince → City → District → Neighborhood, `business_architecture_13`) ve
  localization-service'in esnek per-country template sistemi. Bugünkü `AddressMaster.tsx` ikisini
  karıştırıyor. Bu plan, birini diğerinin üstüne netçe oturtuyor (bkz. "Mimari Karar" bölümü).

## Mimari Karar

- `country`, `state`, `city` üçlüsü **her zaman sabit** kalır ve core-service'in mevcut cascade
  master-data API'lerinden (`/api/address/countries|states|cities`) beslenir. Bunlar zaten generic
  çalışıyor (state seçici, sadece o ülke için state listesi doluysa gösteriliyor — ek değişiklik gerekmez).
- `city`'nin **altındaki** her şey (district, neighborhood, street, door_no, zip_code, po_box, vb.)
  `CountryAddressTemplate` şablonundan gelir ve tamamen dinamik render edilir. Bu, bugünkü DB seed
  verisiyle (TR: district+neighborhood+zip_code, US: street+zip_code) zaten örtüşüyor.
- Şablon alanlarından bazıları (district, neighborhood) core-service'in master-data cascade'ine bağlı
  select'ler, bazıları (street, door_no, po_box) serbest metin. Bu ayrım artık **field-key ismine bakarak
  UI'da hardcode edilmez**, backend'den gelen `fieldType` + `masterDataSource` + `parentFieldKey`
  metadata'sıyla belirlenir.

---

## Proposed Changes

### Backend (wms-localization-service) Değişiklikleri

#### [NEW] [V15__address_template_field_metadata.sql](file:///c:/Users/murat/Desktop/WMS/wms-localization-service/src/main/resources/db/migration/V15__address_template_field_metadata.sql)
- `localization.address_template_field` tablosuna üç yeni sütun eklenir:
  - `field_type VARCHAR(20) NOT NULL DEFAULT 'TEXT'` — `TEXT` | `MASTER_SELECT`
  - `master_data_source VARCHAR(20)` — `DISTRICT` | `NEIGHBORHOOD` (nullable; core-service cascade
    endpoint'lerinden hangisinin çağrılacağını belirtir)
  - `parent_field_key VARCHAR(100)` — bu select'in hangi üst alanın seçili id'sine bağlı olduğunu
    belirtir (`district` alanı için `city`, `neighborhood` alanı için `district`; sabit alanlara referans
    için özel anahtarlar `__city__`/`__state__` kullanılır)
- Backfill: `district` → `MASTER_SELECT` / `DISTRICT` / `parent_field_key='__city__'`;
  `neighborhood` → `MASTER_SELECT` / `NEIGHBORHOOD` / `parent_field_key='district'`;
  diğer tüm alanlar (`street`, `zip_code`, `door_no`, `apartment_no`, `floor`, `building_name`, `po_box`,
  `province`, `prefecture`) varsayılan `TEXT` olarak kalır.
- Additive migration; mevcut satırlar `DEFAULT 'TEXT'` ile güvenle dolar, veri kaybı riski yok.

#### [MODIFY] [AddressTemplateField.java](file:///c:/Users/murat/Desktop/WMS/wms-localization-service/src/main/java/com/wms/localization/domain/address/AddressTemplateField.java)
- `fieldType` (enum `FieldType { TEXT, MASTER_SELECT }`), `masterDataSource`
  (enum `MasterDataSource { NONE, DISTRICT, NEIGHBORHOOD }`), `parentFieldKey` (`String`, nullable)
  alanları eklenir.

#### [NEW] [CountryAddressTemplateDto.java](file:///c:/Users/murat/Desktop/WMS/wms-localization-service/src/main/java/com/wms/localization/dto/address/CountryAddressTemplateDto.java)
- Alanlar: `fieldKey`, `fieldLabelKey`, `mandatory`, `sequence`, `validationRegex`, `errorMessageKey`,
  `fieldType`, `masterDataSource`, `parentFieldKey`.
- `CountryAddressTemplate` + ilişkili `AddressTemplateField`'dan bu DTO'ya statik `from(...)` mapper.

#### [MODIFY] [AddressService.java](file:///c:/Users/murat/Desktop/WMS/wms-localization-service/src/main/java/com/wms/localization/service/address/AddressService.java)
- `CountryAddressTemplateRepository` enjekte edilir (aynı repository `AddressValidationService`
  tarafından zaten kullanılıyor — ekstra sorgu maliyeti yok).
- `List<CountryAddressTemplateDto> getTemplateByCountry(Long countryId)` eklenir: sıralı şablon
  listesini `CountryAddressTemplateDto.from(...)` ile map'ler. Şablon boşsa boş liste döner (UI bunu
  "bu ülke için ek alan yok, sadece sabit alanları göster" olarak yorumlar).

#### [MODIFY] [AddressController.java](file:///c:/Users/murat/Desktop/WMS/wms-localization-service/src/main/java/com/wms/localization/controller/AddressController.java)
- `GET /api/addresses/templates/{countryId}` eklenir → `AddressService.getTemplateByCountry`.

---

### UI (wms-ui) Değişiklikleri

#### [MODIFY] [services.ts](file:///c:/Users/murat/Desktop/WMS/wms-ui/src/api/services.ts)
- `CountryAddressTemplateDto` tipi eklenir (backend DTO'suyla birebir: `fieldKey`, `fieldLabelKey`,
  `mandatory`, `sequence`, `validationRegex`, `errorMessageKey`, `fieldType`, `masterDataSource`,
  `parentFieldKey`).
- `localizationService.getCountryTemplate(countryId: number)` eklenir →
  `GET /api/addresses/templates/{countryId}`.

#### [MODIFY] [AddressMaster.tsx](file:///c:/Users/murat/Desktop/WMS/wms-ui/src/views/AddressMaster.tsx)
- `isTR` sabiti tamamen kaldırılır.
- Sabit kısım korunur: country → state (varsa) → city cascade'i, bugünkü gibi
  `addressMasterService.states/cities` ile generic kalır (state, listesi boşsa zaten gizleniyor —
  değişiklik gerekmiyor).
- City seçildiğinde `localizationService.getCountryTemplate(countryId)` çağrılır, dönen liste
  `sequence`'e göre state'e (`template: CountryAddressTemplateDto[]`) alınır.
- Şablon alanları döngüyle render edilir:
  - `fieldType === "MASTER_SELECT"` → generic bir `DynamicMasterSelect` bileşeni: `masterDataSource`'a
    göre `addressMasterService.districts(parentValue)` ya da `.neighborhoods(parentValue)` çağrılır;
    `parentFieldKey` üzerinden hangi alanın değerinin (city id, ya da başka bir template alanının seçili
    id'si) parametre olarak geçileceği çözülür. `neighborhood` seçilince zip otomatik dolar (mevcut
    davranış korunur, artık field-key hardcode yerine `masterDataSource === "NEIGHBORHOOD"` kontrolüyle).
  - `fieldType === "TEXT"` → standart `input`; `validationRegex` varsa `pattern` olarak da bağlanır
    (tarayıcı seviyesinde erken uyarı), asıl kontrol submit'te yapılır.
  - Dinamik alan değerleri tek bir `Record<string, string>` state'inde (`fieldValues`) tutulur; anahtar
    `fieldKey`. `addressDetails` payload'ı bu map'ten üretilir — artık `district`/`neighborhood` özel
    olarak koda yazılmıyor, herhangi bir yeni `fieldKey` otomatik akar.
- **Şablon boş dönerse** (ülke için hiçbir ek alan tanımlı değilse): sadece sabit alanlar (city/state/zip)
  gösterilir, bilgi bandında "Bu ülke için ek adres alanı tanımlı değil" mesajı çıkar — silent hiçbir
  alan kaybı olmaz.
- Client-side doğrulama: form submit öncesi `template` listesi döngüyle gezilir, `mandatory` ve
  `validationRegex` kontrolleri `fieldValues`'a uygulanır; hata mesajı `errorMessageKey` üzerinden i18n
  ile çözülür (yoksa `validation.<fieldKey>.required|invalid` fallback anahtarı kullanılır — backend'deki
  `AddressValidationService` ile birebir aynı fallback mantığı, tek kaynaktan regex/i18n).
- Statik bilgi bandı (`isTR ? ... : ...`) kaldırılır; yerine şablonun `fieldLabelKey`'lerinden üretilen
  generic bir özet ("Bu ülke için: İlçe, Mahalle, Posta Kodu") gösterilir.

#### [NEW] `components/DynamicAddressField.tsx`
- `CountryAddressTemplateDto` + mevcut değer + `onChange` alan tek bir generic bileşen; `MASTER_SELECT`
  ve `TEXT` render dallarını izole eder, `AddressMaster.tsx`'i sadeleştirir ve ileride başka ekranlarda
  (örn. sevkiyat adresi formu) tekrar kullanılabilir hale getirir.

---

## Verification Plan

### Automated Tests
- `AddressServiceTest`: `getTemplateByCountry` için (a) şablonu olan ülke → doğru sırada/alanlarda DTO
  listesi, (b) şablonu olmayan ülke → boş liste.
- `AddressControllerTest`: yeni endpoint için 200 + doğru JSON şekli; bilinmeyen `countryId` için boş
  liste (404 değil — var olmayan şablon hata değildir).
- `AddressTemplateFieldRepositoryTest` / migration testi: yeni sütunların backfill değerlerinin doğru
  olduğu (district/neighborhood için `MASTER_SELECT`, diğerleri `TEXT`).
- UI: `DynamicAddressField` için birim testi — `MASTER_SELECT` + `parentFieldKey` verildiğinde doğru
  `addressMasterService` fonksiyonunun çağrıldığı; `TEXT` + regex verildiğinde hatalı girişte validasyon
  mesajının tetiklendiği.
- UI: `AddressMaster` entegrasyon testi — TR şablonu mock'landığında İlçe/Mahalle select'lerinin,
  US şablonu mock'landığında Street text + ZIP regex'in, boş şablon mock'landığında sadece sabit
  alanların render edildiği.

### Manual Verification
- Türkiye seçildiğinde İl → İlçe → Mahalle → Posta Kodu dinamik yüklenmesi (mahalle seçilince otomatik
  posta kodu).
- Amerika seçildiğinde Eyalet → Şehir ve Street/ZIP alanlarının dinamik yüklenmesi.
- Şablonu olmayan üçüncü bir ülke seçildiğinde sadece sabit alanların gösterilip bilgilendirme
  mesajının çıkması.
- Hatalı format (örn. yanlış ZIP) girildiğinde client-side regex uyarısının backend mesajıyla tutarlı
  çıkması.
- Yeni bir `fieldKey` (örn. `po_box`) sadece DB seed'e eklenerek, **hiçbir kod değişikliği olmadan**
  formda otomatik göründüğünün doğrulanması (generic'liğin nihai kanıtı).
- Kaydedilen adresin `formattedAddress` alanına doğru yazıldığının izlenmesi (mevcut
  `TurkeyAddressFormatterStrategy`/`UsAddressFormatterStrategy` bu planın kapsamı dışında, değişmiyor).

## Kapsam Dışı / Not
- Çıktı formatlama (`AddressFormatterStrategy` implementasyonları) bilinçli olarak ülkeye özel
  kalıyor — bu, "generic giriş formu" hedefiyle çelişmiyor, çünkü adres string'inin nasıl
  biçimlendirileceği ayrı bir iş kuralı (yazım sırası, virgül kullanımı vb.) ve şu an değişmesi
  istenmiyor.
- Master-data yönetimi (yeni il/ilçe/mahalle ekleme) admin panel işidir, bu plan kapsamında değildir.
