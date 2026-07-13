# WMS Dinamik ve Generic Adres Giriş Sistemi — Tasarım Planı (v4)

v3'ün ülke matrisi temel alınmıştır. Bu sürüm iki şeyi netleştirir: (1) `ar`→Suudi Arabistan,
`zh`→Çin (Anakara) kararları onaylanıp kilitlenmiştir; (2) "sabit enum, dinamik değer" ilkesi
mimarinin ayrılmaz bir kuralı olarak açıkça yazılmıştır.

## Onaylanan Kararlar

- **`ar` = Suudi Arabistan, `zh` = Çin (Anakara).** v3'teki matris bu haliyle kilitlenmiştir,
  değişiklik yok.
- v3 Bölüm 3'teki 2-4 numaralı kararlar (Rusya/Hindistan'da district→TEXT ile başlama, regex'lerin
  üretime geçmeden resmi kaynakla doğrulanması, master-data yükleme sorumluluğunun ayrı iş kalemi
  olması) **varsayılan olarak kabul edilip bu şekilde ilerleniyor**; itiraz olursa güncellenir.

## Temel İlke: Sabit Enum ≠ Sabit Değer

Bu, sistemin en kritik tasarım kuralıdır ve açıkça yazılmalıdır:

- **`FieldType` (`TEXT`/`MASTER_SELECT`) ve `MasterDataSource` (`NONE`/`DISTRICT`/`NEIGHBORHOOD`)
  enum'ları sabit kalabilir** — bunlar bir alanın *şeklini* tanımlar ("bu alan serbest metin mi,
  yoksa bir listeden mi seçilecek"), ülkeye özgü bir değer değildir. 3 sabit tip (metin / il-altı
  seçim / mahalle seçimi) her ülkeye yetiyor; enum'un kendisi "hardcode" sayılmaz çünkü içinde
  hiçbir ülke ismi, hiçbir ülkeye özgü kural yok.
- **Bu enum'ların içine girecek her şey — hangi ülkede hangi alanın zorunlu olduğu, hangi regex'in
  uygulanacağı, dropdown'daki seçeneklerin ne olduğu, kaç tane alan gösterileceği, sırası, etiketi —
  %100 veritabanından gelir, kodda hiçbir yerde sabitlenmez.** Somut karşılıkları:
  - Hangi ülkenin `district` kullanacağı → `country_address_template` tablosundaki satır varlığı
    (kod: "if country == TR" değil, "bu country_id için böyle bir satır var mı" sorgusu).
  - Dropdown'daki İlçe/Hayy/District/Gu **isimleri ve sayısı** → core-service `districts` tablosunun
    içeriği (kod: `SELECT name FROM districts WHERE city_id = ?`, hiçbir ülkeye özel liste kodda yok).
  - Zorunlu mu, regex ne → `country_address_template.is_mandatory` / `.validation_regex` (kod:
    generic okuma, ülke ismi geçmiyor).
  - Alan etiketi (örn. "İlçe" / "Hayy" / "地区" / "구") → `field_label_key` üzerinden i18n
    çeviri tablosu (kod: `t(fieldLabelKey)`, metin kodda yazılı değil).
- **Kontrol testi:** Yeni bir ülke eklerken (veya AR/ZH için gerçek il/ilçe verisi girerken) tek bir
  Java/TS dosyasına bile dokunmadan, sadece migration/seed ile yapılabiliyor olması gerekir. v2/v3
  planındaki `getTemplateByCountry`, `DynamicAddressField`, `addressMasterService.districts(...)` bu
  testi geçiyor — hiçbiri ülke adı/kodu üzerinden dallanmıyor, sadece gelen veriye göre render ediyor.
  Bunun tek istisnası formatlama katmanıdır (bkz. aşağıdaki "Bilinçli İstisna").

### Bilinçli İstisna: `AddressFormatterStrategy`
`formattedAddress` üretimi (adresin tek satırlık, doğru sıralı, doğru noktalamalı hali) her ülke
için gerçekten farklı bir **algoritma** gerektirir (Çin büyükten küçüğe yazar, TR mahalle/sokak/il
sırası kullanır) — bu, "veri" ile çözülecek bir şey değil, gerçek bir iş kuralı farkıdır. Bu yüzden
13 yeni `AddressFormatterStrategy` implementasyonu (v3 §2.4) kasıtlı olarak kod tarafında kalır.
Bunun dışındaki her şey (giriş formu, validasyon, dropdown içerikleri) veri tabanından gelir.

## Sonraki Adım

v3'teki §2.1–2.5 iş kalemleri aynen geçerli. Uygulamaya geçildiğinde önerilen sıra:

1. V15/V16 migration'ları (yeni `field_key`'ler + 15 ülke `country_address_template` seed'i).
2. `AddressService.getTemplateByCountry` + `GET /api/addresses/templates/{countryId}` (v2'den).
3. UI: `DynamicAddressField` + `AddressMaster.tsx` refactor (v2'den), + "master-data boş" uyarısı
   (v3 §2.5).
4. AR/ZH/KO için core-service `districts` gerçek veri yüklemesi (ayrı veri işi, kod değil).
5. 13 `AddressFormatterStrategy` implementasyonu (v3 §2.4) — istenirse ayrı bir faz olarak
   sona bırakılabilir, giriş formunun çalışmasını engellemez.
