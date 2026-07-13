# 2.1 Lokalizasyon ve Çoklu Lokasyon Desteği — Gap Analizi Raporu

**Tarih:** 10.07.2026 · **Kaynak:** `2_1_Lokalizasyon ve Çoklu Lokasyon Desteği.pdf` · **İncelenen kod:** wms-core, wms-localization, wms-finance, wms-billing, wms-integration, wms-outbound, wms-inventory, wms-ui

## Yönetici Özeti

PDF'teki 16 ham isterin **10'u tam, 6'sı kısmen** karşılanıyor; tamamen eksik ana başlık yok. Dokümanın önerdiği 10 sistem bileşeninin tamamının kodda karşılığı mevcut. Kritik 3 eksik: **(1)** süreç motoru yalnız mal kabulde zorlanıyor, outbound/iade akışları bağlı değil; **(2)** faturalama vergi motorunu kullanmıyor (vergi oranı elle giriliyor); **(3)** ERP entegrasyon senaryolarının yarısı (cari, sipariş, iade, sayım, muhasebe fişi) yok.

## İster Bazında Durum

| # | Ham İster (PDF §19) | Durum | Kanıt / Not |
|---|---|---|---|
| 1 | Çok dilli arayüz ve raporlama | ✅ Tam | `Language`, `TranslationKey/Value`, eksik çeviri logu+raporu, Excel import/export (POI), otomatik çeviri (LibreTranslate/MyMemory), UI'da oturum kapatmadan dil değişimi, sunucu çevirisi gömülü sözlüğü ezer → kodsuz yeni dil |
| 2 | Lokasyon bazlı süreç konfigürasyonu | 🟡 Kısmen | Yapı tam: `ProcessDefinition/StepDefinition`, `LocationProcessStepConfig` (sıra, zorunlu, rol, onay, hata stratejisi), `@CheckWorkflowStep` aspect, audit. **Ama enforcement yalnız inbound'da** (bkz. E-1) |
| 3 | ERP / muhasebe / finans entegrasyonu | 🟡 Kısmen | Adaptör mimarisi (`ErpAdapterFactory`, SAP-REST, Logo-SFTP/CSV), lokasyon bazlı `LocationIntegrationConfig`, log + retry + resend + monitör. Senaryo ve yöntem kapsamı eksik (E-3, E-7) |
| 4 | Organizasyonel kırılım | ✅ Tam | `Organization→Company→Country→Region→Location(tip,timezone)→Zone→StorageLocation`, `UserAccess` (firma+lokasyon+rol), `BaseScopedEntity`+`TenantContext` tüm servislerde, `TransactionLog` firma/lokasyon kırılımlı |
| 5 | Lokasyon ve yetki bazlı ekran alanları | 🟡 Kısmen | `Screen/ScreenField/FieldBehaviorRule` (öncelik, firma, ülke, lokasyon, rol, operasyon tipi; regex validasyon), sunucu tarafı `DynamicFormValidationAspect`, tablo kolon motoru, UI `DynamicForm`. Kriterlerden 4'ü yok (E-6) |
| 6 | Timezone yönetimi | ✅ Tam | `Location.timezone`, `User.preferredTimezone`, UTC saklama, UI Intl API ile DST dahil lokal gösterim, raporda lokasyon saat dilimi (`TransactionLogReportService`) |
| 7 | Tarih ve saat formatları | ✅ Tam | `CountryFormatConfig` + `LocationFormatOverride` (tarih, saat 12/24, ondalık/binlik ayraç), UI `formatDate/formatNumber`, sunucu tarafı `ReportFormatterService` |
| 8 | Çoklu para birimi | ✅ Tam | `Currency` (kod, sembol, hassasiyet), sistem/firma/lokasyon/müşteri/sözleşme/fatura/muhasebe seviyeleri, `FinancialTransaction` orijinal tutar + kur + çevrilmiş tutar + fallback bayrağı. Raporlama PB hariç (E-5) |
| 9 | Müşteri bazlı para birimi | ✅ Tam | `FinanceCustomer` (varsayılan, faturalama PB, kur tipi, kur kaynağı, kur farkı tercihi), `CustomerPermittedCurrency`, izinsiz PB'de `InvalidCustomerCurrencyException` |
| 10 | Döviz kuru yönetimi | ✅ Tam | TCMB sync (job + startup + XML parser), manuel kur, ERP'den kur (`fetchExchangeRates`), banka/sözleşme kaynağı, alış/satış/efektif, kur audit logu, tarih bazlı sorgu, 5 güne kadar geçmiş kur + ters kur fallback |
| 11 | Çoklu para birimi ile faturalama | 🟡 Kısmen | `Invoice`: fatura + muhasebe PB, kur tarihi/değeri, ara toplam/vergi/genel toplam (orijinal + muhasebe karşılığı), `ExchangeDifferenceLog`, kur kilidi. Vergi motoruna bağlı değil (E-2), ayrı "işlem PB" alanı yok |
| 12 | Yerel adres formatları | ✅ Tam | `CountryAddressTemplate` (zorunluluk, sıra, regex — posta kodu dahil), TR/US formatter stratejileri, JSONB `addressDetails` ile Unicode |
| 13 | Şehir / il / ülke adres yapısı | ✅ Tam | `Country/StateProvince/City/District/Neighborhood` + aktif/pasif + `GeoAdminController` ile merkezi yönetim |
| 14 | Uluslararası adres formatları | ✅ Tam | Ülke bazlı şablon + alan farklılaştırma; state/ZIP (US) ve il/ilçe/mahalle (TR) örnekleri mevcut |
| 15 | Farklı vergi oranları | ✅ Tam | `TaxType` veri tabanlı (KDV/VAT/ÖTV... eklenebilir), `TaxRate` ülke/lokasyon/müşteri/ürün/operasyon + tarih versiyonlama (`TaxRateVersioningService`), `TaxRateAuditLog`. PB ve fatura tipi kriteri hariç (E-10) |
| 16 | Vergi hesaplama mekanizması | 🟡 Kısmen | Vergi motoru: hariç/dahil/bileşik stratejiler, muafiyet (`TaxRule.exempt` + kod), tam izlenebilirlik (`TaxCalculationLog`: matrah, oran, muafiyet, kaynak, referans). Kur farkı üzerinden vergi yok (E-8) |

## Eksikler (öncelik sırasıyla)

### Kritik

**E-1 · Outbound/iade süreçleri workflow motoruna bağlı değil** (PDF §6)
`OUTBOUND` ve `RETURN` süreç tanımları ve adımları seed'de var (PICKING, PACKING, QC, SHIPPING / iade adımları), ancak `@CheckWorkflowStep` yalnız core'daki `InboundWorkflowService`'te kullanılıyor. wms-outbound ve wms-inventory servislerinde `LocationProcessStepConfig`'e hiçbir referans yok — sevkiyat onayı, paketleme, sayım gibi adımlar lokasyon bazlı aç/kapa edilemiyor. Kabul kriteri 6.5 ("aynı işlem farklı lokasyonlarda farklı adımlardan geçmeli") outbound için sağlanmıyor.
*Öneri:* Outbound servisine core'daki workflow doğrulamasını çağıran bir client/aspect eklenmeli veya adım kontrolü Kafka/REST üzerinden core'a delege edilmeli.

**E-2 · Faturalama vergi motorunu kullanmıyor** (PDF §14.4, §18)
`InvoiceItemInputDto.taxRate` çağırandan elle geliyor; `InvoiceCalculationService` finance'taki `TaxEngineService`/`TaxResolutionService`'i hiç çağırmıyor. Lokasyon/müşteri/ürün bazlı otomatik oran çözümü ve muafiyet faturaya yansımıyor; vergi izlenebilirlik logu fatura akışında oluşmuyor.
*Öneri:* Billing → Finance'a tax-resolve/calculate HTTP çağrısı (mevcut `FinanceHttpCurrencyConversionService` deseniyle).

**E-3 · ERP entegrasyon senaryolarının kapsamı** (PDF §7.4)
`ErpAdapter` yalnız: malzeme kartı, stok hareketi, fatura, mal kabul, sevkiyat, kur çekme. Eksik: **cari hesap, satın alma siparişi, satış siparişi, iade, sayım sonucu, muhasebe fişi, vergi bilgisi** aktarımı.

### Orta

**E-4 · E-posta / bildirim şablonlarında dil desteği** (PDF §5.2) — Bildirim/şablon altyapısı hiç yok (`OutboxAlertService`'te TODO olarak duruyor).
**E-5 · Raporlama para birimi** (PDF §11.3, §11.5) — "Raporlar farklı para birimine çevrilerek alınabilmeli" karşılanmıyor; dönüşüm servisi var ama raporlama katmanına bağlı değil. Genel raporlama modülü de zayıf (yalnız `TransactionLogReportService`, firma+lokasyon filtreli; ülke/depo kırılımı yok — §4.4).
**E-6 · Dinamik alan kural kriterleri** (PDF §8.3) — `FieldBehaviorRule`'da **depo, müşteri tipi, ürün tipi, işlem durumu** kriterleri yok (firma/ülke/lokasyon/rol/operasyon tipi var).
**E-7 · Entegrasyon yöntemleri** (PDF §7.3) — `ConnectionType`: REST/SOAP/SFTP/DB. **Webhook, message queue (ERP'ye dışa), ESB** yok; SOAP enum'da var ama fiili SOAP adaptörü yazılmamış. Dosya formatlarından CSV var (Logo), XML/Excel/JSON dosya senaryosu yok.
**E-8 · Kur farkı üzerinden vergi hesaplama** (PDF §18.2) — `ExchangeDifferenceService` vergiye hiç dokunmuyor.
**E-9 · Kullanıcı yetki değişikliği audit'i** (PDF §20.3) — `UserManagementService`'te audit çağrısı yok; `ConfigurationAuditLog` süreç/UI/kur/vergi konfigürasyonlarını kapsıyor, yetki değişikliklerini kapsamıyor.

### Düşük

**E-10 · Vergi oranı kriterleri** (§17.3): `TaxRate`'te **para birimi** ve **fatura tipi** kriteri yok.
**E-11 · Org kırılımında şehir seviyesi** (§4.2): Location→Region→Country zinciri var; org ağacında ayrı "şehir" halkası yok (geo master'da City mevcut). "Operasyon tipi" kırılımı `LocationType` + kural motorundaki `operationType` ile dolaylı.
**E-12 · Süreç adım kataloğu** (§6.3): Lot/batch, SKT, etiketleme, sayım kontrolü, finansal kontrol, fatura kontrolü adımları seed'de tanımsız — yapı veri tabanlı, sadece tanım eklemek yeterli.
**E-13 · Adaptör kapsamı** (§7.2): Oracle/Dynamics/Netsis/Mikro adaptörleri yok. Kabul kriteri "yeni adaptör eklenebilir yapı" factory deseniyle sağlanıyor; bu bilinçli bir faz kararı olabilir.
**E-14 · RateSource'ta "özel müşteri kuru"** (§13.2): Ayrı kaynak tipi yok; `CustomerRateService` müşterinin kur tipi/kaynağı tercihiyle çözüyor — işlevsel olarak yakın.

## Önerilen Bileşen Eşleşmesi (PDF §21)

| PDF Bileşeni | Kodda | PDF Bileşeni | Kodda |
|---|---|---|---|
| Localization Service | ✅ wms-localization | Currency Mgmt | ✅ wms-finance |
| Organization Mgmt | ✅ wms-core | Tax Engine | ✅ wms-finance `tax/engine` |
| Workflow Config Engine | ✅ wms-core | Address Mgmt | ✅ wms-localization + core geo |
| Dynamic UI Engine | ✅ wms-core + wms-ui | Timezone Service | ✅ wms-core + UI Intl |
| Integration Framework | ✅ wms-integration | Audit Log Service | 🟡 Dağıtık (servis başına audit; merkezi servis yok) |

## Sonuç

Mimari yaklaşım (parametrik, lokasyon bazlı, adaptör tabanlı) PDF'in öngördüğü tasarımla birebir örtüşüyor. Kapatılması gereken asıl açık, mevcut motorların **uçtan uca bağlanması**: workflow motorunun outbound'a, vergi motorunun faturalamaya, entegrasyon çatısının kalan ERP senaryolarına bağlanması. Bunlar yapıldığında doküman kapsamı fiilen tamamlanmış olur.
