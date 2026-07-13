# WMS Dinamik ve Generic Adres Giriş Sistemi — Tasarım Planı (v3)

v2'nin mimarisi (sabit country/state/city + `CountryAddressTemplate` tabanlı dinamik alt alanlar,
`fieldType`/`masterDataSource`/`parentFieldKey` metadata'sı) temel alınmıştır. Bu sürüm, sistemin
gerçekten desteklenen 15 dile/ülkeye (tr, en + es, fr, de, it, pt, ru, ar, zh, ja, ko, hi, nl, pl)
göre ölçeklenip ölçeklenmediğini somut olarak doğrular ve gerekli ek işleri tanımlar.

## Sonuç (Önce Kısa Cevap)

**Mimaride yeni bir seviye/enum değeri gerekmiyor.** 15 ülkenin adres yapısını tek tek çıkardım
(aşağıdaki matris): hiçbiri mevcut iki cascade seviyesinin (`DISTRICT`, `NEIGHBORHOOD`) dışına
çıkmıyor. Gerekli olan şey kod değil **veri**: her ülke için `country_address_template` satırları,
birkaç yeni `address_template_field` (field_key) tanımı, ve `DISTRICT` seviyesi kullanılan ülkeler
için gerçek master-data içeriği (core-service `districts` tablosuna il/bölge bazlı gerçek liste).

---

## 1. Ülke Bazlı Adres Yapısı Matrisi

> Varsayım/karar gerektiren noktalar **kalın** işaretlendi — onay gerekiyor (bkz. Bölüm 3).

| Dil | Referans Ülke | State/Province | District (reuse `DISTRICT`) | Neighborhood (reuse `NEIGHBORHOOD`) | Zip Regex (taslak — resmi kaynakla doğrulanmalı) | Ek TEXT alanlar |
|---|---|---|---|---|---|---|
| tr | Türkiye | Hayır | **Evet, zorunlu** | **Evet, zorunlu** | `^[0-9]{5}$` (opsiyonel) | street, door_no, apartment_no |
| en | ABD | Evet, zorunlu | Hayır | Hayır | `^[0-9]{5}(-[0-9]{4})?$` | street (zorunlu) |
| es | İspanya | Evet, zorunlu (provincia) | Hayır | Hayır | `^[0-9]{5}$` | street+no (zorunlu), floor/door (opsiyonel, mevcut `floor`/`apartment_no`) |
| fr | Fransa | Hayır (region posta adresinde yok) | Hayır | Hayır | `^[0-9]{5}$` | street (zorunlu) |
| de | Almanya | Hayır (Bundesland adreste yok) | Hayır | Hayır | `^[0-9]{5}$` | street+no (zorunlu) |
| it | İtalya | Evet, zorunlu (il kısaltması, örn. RM) | Hayır | Hayır | `^[0-9]{5}$` | street (zorunlu) |
| pt | Portekiz | Hayır | Hayır | Hayır | `^[0-9]{4}-[0-9]{3}$` | street (zorunlu) |
| ru | Rusya | Evet, zorunlu (oblast) | **Opsiyonel** (rayon — büyük şehirlerde var, kapsam azaltmak için başlangıçta TEXT önerilir) | Hayır | `^[0-9]{6}$` | street, house, korpus, apartment |
| **ar** | **Suudi Arabistan (varsayım)** | Evet, zorunlu (region) | **Evet, zorunlu** (Hayy) | Hayır | `^[0-9]{5}$` (+opsiyonel 4 haneli ek kod) | building_no, street, additional_no |
| **zh** | **Çin (Anakara, varsayım)** | Evet, zorunlu (province) | **Evet, zorunlu** | Hayır | `^[0-9]{6}$` | street/detay adres (zorunlu) |
| ja | Japonya | Evet, zorunlu (prefecture) | Hayır (ward, "city" olarak core'da modellenir — bkz. not) | Hayır | `^[0-9]{3}-[0-9]{4}$` | chome_banchi (zorunlu), building_name (opsiyonel) |
| ko | Güney Kore | Evet, zorunlu (do/si) | **Evet** (gu) | Hayır | `^[0-9]{5}$` | road_name+no (zorunlu), detail (opsiyonel) |
| hi | Hindistan | Evet, zorunlu | Hayır (district posta adresinde nadiren kullanılır → TEXT) | Hayır | `^[0-9]{6}$` | house/building, street, locality (TEXT), landmark (opsiyonel) |
| nl | Hollanda | Hayır | Hayır | Hayır | `^[0-9]{4}\s?[A-Z]{2}$` | street+no (zorunlu) |
| pl | Polonya | Hayır (voivodeship posta adresinde nadiren gerekli) | Hayır | Hayır | `^[0-9]{2}-[0-9]{3}$` | street+no (zorunlu) |

**Gözlem:** `DISTRICT` seviyesi TR, AR, ZH, KO'da (ve opsiyonel olarak RU'da) tekrar kullanılıyor —
yeni bir enum değeri gerekmiyor, sadece bu ülkeler için core-service `districts` tablosuna gerçek
veri girilmesi gerekiyor. `NEIGHBORHOOD` seviyesi hiçbir başka ülkede gerekmiyor, sadece TR'ye özel
kalıyor (bu da beklenen bir durum — sistemin "her ülke her seviyeyi kullanmak zorunda değil" tasarımı
zaten bunu destekliyor).

---

## 2. Gerekli Ek İşler (v2'ye Ek)

### 2.1 Yeni `address_template_field` kayıtları (V15 migration'a eklenecek)
Mevcut katalog (`district`, `neighborhood`, `street`, `door_no`, `apartment_no`, `floor`,
`building_name`, `po_box`, `province`, `prefecture`) şu yeni `field_key`'lerle genişletilir:
`house_no`, `korpus` (Rusya blok/giriş no), `additional_no` (Suudi ek posta kodu),
`chome_banchi` (Japonya blok/sokak no), `road_name` (Kore yol adı), `locality` (Hindistan mahalle —
serbest metin), `landmark` (Hindistan referans nokta). Hepsi `fieldType=TEXT`,
`masterDataSource=NONE`.

### 2.2 15 ülke için `country_address_template` seed verisi
V15 migration'a (ya da ayrı bir `V16__address_template_seed_15_countries.sql`'e) her ülke için
Bölüm 1'deki matrise birebir uyan satırlar eklenir: `is_mandatory`, `sequence`, `validation_regex`,
`error_message_key`. Bu adım **kod değişikliği gerektirmiyor**, sadece veri; v2'deki
`AddressService.getTemplateByCountry` ve UI'daki `DynamicAddressField` bunu otomatik olarak alıp
render eder.

### 2.3 Master-data içerik yüklemesi (ayrı iş kalemi, kod dışı)
`DISTRICT` seviyesi kullanılan ülkeler (TR zaten var; AR, ZH, KO, opsiyonel RU) için core-service
`districts` tablosuna gerçek il/bölge listelerinin girilmesi gerekir — aksi halde dropdown boş
kalır. Bu bir admin-panel/veri-yükleme işidir, bu planın kod kapsamının dışındadır ama **ön koşuldur**;
gerçek kullanıcı bu ülkeleri seçtiğinde district listesi boşsa, UI (v2'de tanımlandığı gibi) şablonu
"tanımlı ama veri yok" durumuna düşer — bunun için ayrı bir "listede kayıt yok" mesajı eklenmesi
önerilir (bkz. 2.5).

### 2.4 `AddressFormatterStrategy` — 13 yeni implementasyon (yeni iş kalemi, önceki planda kapsam dışıydı)
v2'de "formatlama bu planın kapsamı dışında" denmişti; 15 ülkeye çıkınca bu artık gerçek bir eksiklik
haline gelir çünkü örneğin Çin adres sırası büyükten küçüğe yazılır (`Ülke, İl, Şehir, İlçe, Sokak`),
Japonya benzer şekilde tersine, Portekiz/Hollanda ise farklı noktalama kullanır. Öneri: her dil için
mevcut `AddressFormatterStrategy` interface'ini implemente eden birer strateji sınıfı eklensin
(`SpainAddressFormatterStrategy`, `ChinaAddressFormatterStrategy`, vb.) — bu mekanik ama gerekli bir
iş, mevcut Strategy pattern'i zaten buna göre tasarlanmış, yeni bir soyutlama gerekmiyor. Bu, ayrı bir
alt görev olarak plana eklenmeli.

### 2.5 UI: "şablon var ama master-data boş" durumu
v2'de sadece "şablon boş" durumu ele alınmıştı. Şimdi üçüncü bir durum eklenmeli: şablon `district`
alanını zorunlu gösteriyor ama `addressMasterService.districts(cityId)` boş dönüyor (henüz veri
yüklenmemiş ülke). Bu durumda select'i disabled + "Bu şehir için ilçe verisi henüz tanımlı değil"
uyarısı gösterilmeli, kullanıcı kilitlenmemeli (belki serbest metin fallback'e izin verilmeli — iş
kararı gerektirir, bkz. Bölüm 3).

---

## 3. Onay Gereken Kararlar

1. **`ar` ve `zh` için referans ülke seçimi:** Bu iki dil kodu birden fazla ülkeye karşılık gelebilir
   (Arapça: Suudi Arabistan/BAE/Mısır — adres kuralları birbirinden oldukça farklı; Çince: Anakara
   Çin/Tayvan). Yukarıdaki matriste Suudi Arabistan ve Anakara Çin varsayıldı — onaylanmalı ya da
   gerçek hedef pazar belirtilmeli.
2. **Rusya ve Hindistan'da `district`/`locality` seviyesi:** Master-data yükü azaltmak için bunları
   başlangıçta serbest metin (`TEXT`) önerdim; ileride gerçek bir dropdown'a yükseltilebilir. Bu kabul
   edilebilir mi, yoksa gün 1'den `MASTER_SELECT` mi istenir?
3. **Regex'ler taslak:** Yukarıdaki tüm posta kodu regex'leri genel bilgiye dayanıyor, ülke ülke resmi
   posta idaresi kaynağıyla doğrulanmadı. Üretime almadan önce her ülke için ayrı doğrulama
   önerilir — özellikle Suudi Arabistan'ın ek kodu ve Hindistan PIN kuralları gibi daha az bilinen
   formatlarda.
4. **Master-data yükleme sorumluluğu:** District/state/city gerçek listelerinin kim tarafından ve
   hangi kaynaktan (resmi GeoNames/posta idaresi verisi mi, manuel mi) yükleneceği ayrı bir iş
   kalemi olarak sahiplendirilmeli — bu plan sadece şemanın/akışın buna hazır olduğunu garanti eder.

---

## 4. Verification Plan'a Ek

- Her 15 ülke için: template seed'i doğru sequence/mandatory/regex ile döndüğünü kontrol eden
  parametrize `AddressServiceTest` (tek test, `@ParameterizedTest` ile 15 satır).
- `district` seviyesi olan 4 ülke (TR/AR/ZH/KO) için: master-data boşken UI'nin kilitlenmeden
  uyarı gösterdiğinin manuel doğrulaması.
- Yeni eklenen 13 `AddressFormatterStrategy` implementasyonu için: her biri için örnek adresle
  `formattedAddress` çıktısının beklenen sırada olduğunu doğrulayan birim testi.
