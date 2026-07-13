Aşağıdaki değişiklikleri bu repoda yaptım; bunları baştan sona bir code review olarak incele. Ben bu ortamda Maven/Docker/JDK olmadığı için Java tarafını derleyemedim ve migration'ları gerçek bir Postgres'e karşı çalıştıramadım — bu yüzden en kritik ihtiyaç, derleme/migration/test'leri gerçekten çalıştırıp doğrulaman.

## Bağlam

Amaç: WMS'teki adres giriş formunu (`AddressMaster.tsx`), hardcoded TR/US ayrımından çıkarıp `CountryAddressTemplate` tablosundan beslenen tam generic bir yapıya çevirmek, ve bunu şu an desteklenen 15 dile/ülkeye (tr, en, es, fr, de, it, pt, ru, ar→Suudi Arabistan, zh→Çin Anakara, ja, ko, hi, nl, pl) göre ölçeklendirmek. Tasarım kararları ve ülke bazlı alan matrisi şu dosyalarda: `WMS_Dinamik_Adres_Plani_v2.md`, `v3.md`, `v4.md` (repo kökünde). Lütfen önce bunları oku, sonra kodun bu plana gerçekten sadık kalıp kalmadığını değerlendir.

## Mimari prensip (bunu özellikle doğrula)

`FieldType` (`TEXT`/`MASTER_SELECT`/`FIXED`) ve `MasterDataSource` (`NONE`/`DISTRICT`/`NEIGHBORHOOD`) enum'ları sabit ama içine giren "hangi ülkede hangi alan zorunlu/regex ne/dropdown seçenekleri ne" bilgisinin **tamamen veritabanından** geldiğinden, kodda hiçbir yerde ülke adı/kodu üzerinden dallanma olmadığından emin ol. Özellikle `wms-ui/src/views/AddressMaster.tsx` ve `wms-ui/src/components/DynamicAddressField.tsx` içinde `if (iso === "TR")` tarzı bir kalıntı kalmamış olmalı.

## Değiştirilen / eklenen dosyalar

**wms-localization-service (backend):**
- `src/main/resources/db/migration/V15__address_template_field_metadata.sql` — `address_template_field` tablosuna `field_type`/`master_data_source`/`parent_field_key` eklenir, mevcut `district`/`neighborhood`/`zip_code` backfill edilir, yeni `field_key`'ler (`house_no`, `korpus`, `additional_no`, `chome_banchi`, `road_name`, `locality`, `landmark`, `city`, `state`) eklenir.
- `src/main/resources/db/migration/V16__country_address_template_seed_15_countries.sql` — DE'yi tamamlar + ES/FR/IT/PT/RU/SA/CN/JP/KR/IN/NL/PL için `country_address_template` seed'i. `country_id`'yi `dblink` ile `wms_core_db`'den iso_code üzerinden çözüyor (host=localhost, dbname=wms_core_db, user=postgres, password=postgres) — **bu bağlantı stringinin gerçek ortamda doğru olup olmadığını mutlaka kontrol et**, ve bu migration'ın `wms-core-service`'in `V18` migration'ından SONRA çalışacağının deployment sırasıyla garanti edildiğini doğrula (aksi halde `tmp_core_countries` boş gelir ve seed sessizce hiçbir şey eklemez).
- `src/main/java/com/wms/localization/domain/address/AddressTemplateField.java` — yeni enum'lar ve alanlar.
- `src/main/java/com/wms/localization/dto/address/CountryAddressTemplateDto.java` — yeni DTO.
- `src/main/java/com/wms/localization/service/address/AddressService.java` — `getTemplateByCountry` eklendi.
- `src/main/java/com/wms/localization/controller/AddressController.java` — `GET /api/addresses/templates/{countryId}` eklendi.
- `src/test/java/com/wms/localization/service/address/AddressServiceTest.java` ve `.../controller/AddressControllerTest.java` — yeni testler eklendi.

**wms-core-service (backend):**
- `src/main/resources/db/migration/V18__address_countries_expansion.sql` — 12 yeni ülke + cascade testi için minimal (tam kapsamlı değil, kasıtlı olarak illüstratif) state/city/district demo verisi.

**wms-ui (frontend):**
- `src/api/services.ts` — `CountryAddressTemplateDto` tipi + `localizationService.getCountryTemplate`.
- `src/components/DynamicAddressField.tsx` — yeni generic bileşen.
- `src/views/AddressMaster.tsx` — `isTR` tamamen kaldırıldı, şablon-güdümlü render + generic client-side validasyon + parent/child (örn. neighborhood→district→city) bağımlılık çözümü.
- `src/i18n/translations.ts` — yeni `addr.*` ve `fields.*` çeviri anahtarları (tr/en).

## Senden istediklerim

1. **Gerçekten derle/çalıştır:** `wms-core-service` ve `wms-localization-service`'i Maven ile derle (`mvn -q compile` veya `mvn test`), `wms-ui`'de `npx tsc --noEmit` ve mevcut test suite'i (varsa) çalıştır. Ben `tsc`/`eslint`'i çalıştırabildim (0 hata) ama Java tarafını hiç derleyemedim — orada gözden kaçmış bir tip/import hatası olabilir.
2. **Migration'ları gerçek bir Postgres'e karşı dene** (varsa yerel/test DB'niz): `V15`→`V16` (localization) ve `V18` (core) sırayla çalıştırılıp idempotent mi, `ON CONFLICT`/`WHERE NOT EXISTS` kısıtları gerçekten çakışmaları önlüyor mu, dblink extension'ı gerçekten kurulabiliyor mu.
3. **AddressValidationService ile CountryAddressTemplateDto arasındaki simetriyi kontrol et:** `city`/`state`/`zip_code` alanlarının `FIXED` tipiyle işaretlenmesi, backend'in `AddressValidationService.extractFieldValue`'daki mevcut özel-durum mantığıyla (city/state/zip_code'u Address'in sabit sütunlarından okuma) hâlâ tutarlı mı.
4. **Frontend'de cascade reset mantığını incele:** `AddressMaster.tsx`'teki `handleFieldChange` içinde bir master-select alanı değiştiğinde ona bağımlı (`parentFieldKey` eşleşen) diğer alanların gerçekten sıfırlandığını, ve `resolveParentValue`'nun `__city__`/`__state__` sentinel'lerini doğru çözdüğünü doğrula.
5. **Var olan test coverage'ını genişlet:** Özellikle 15 ülkenin her biri için template seed'inin doğru sequence/mandatory/regex ile döndüğünü kontrol eden bir parametrize test (plan `v3.md` §4'te önerilmişti) henüz eklenmedi — istersen sen ekle.
6. **Genel kod kalitesi:** Naming, hata yönetimi, gereksiz kod tekrarı, ve benim gözden kaçırmış olabileceğim herhangi bir şeyi serbestçe eleştir.

Review sonunda: neyin çalıştığını doğruladığını, neyin hâlâ riskli/doğrulanmamış olduğunu, ve varsa somut düzeltmeleri net bir liste halinde özetle.
