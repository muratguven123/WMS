# Faz 2 — Gerçek DB Entegrasyon Testleri + Coverage · Uygulama Planı

> Tarih: 2026-07-14 · Bağımlılık: Faz 1 (CI + Migration Smoke IT) · Süre: 1-2 hafta (kademeli)
> Amaç: H2'yi test yolundan çıkarmak, her serviste en az bir gerçek-PostgreSQL repository/JPA testi kurmak, ortak test altyapısını tek modülde toplamak ve coverage'ı CI artefaktı yapmak.

## 0. Mevcut Durum (kod üzerinden doğrulandı)

| Konu | Durum |
|---|---|
| Altın referans pattern | `wms-core-service/.../aspect/TenantFilterAspectIntegrationTest.java` — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + Testcontainers PostgreSQL. Bu birebir kopyalanacak model. |
| H2 hâlâ kullanan | `wms-inventory-service` ve `wms-outbound-service` → `src/test/resources/application-test.yml` içinde `jdbc:h2:mem:...;MODE=PostgreSQL`, `flyway.enabled=false`, `ddl-auto=create-drop`. |
| H2'ye bağlı testler | `InventoryRepositoryTest`, `OutboundOrderRepositoryTest` (`@DataJpaTest @ActiveProfiles("test")`), ayrıca bu iki serviste 3'er `@SpringBootTest`. |
| Testcontainers bağımlılığı | Faz 1'de 5 servise eklendi (billing, finance, integration, inventory, outbound); core + localization'da zaten vardı. Yani altyapı hazır. |
| Ortak helper kopyası | `MigrationSmokeSupport.java` billing/finance/integration/inventory/outbound'da **birebir kopya** (5 nüsha). `wms-test-support` modülü henüz yok. |
| Gerçek-PG repository testi olan servis | Yalnızca core (1 test). Diğer 6 serviste yok. |
| JaCoCo | Tanımsız. |

**Sonuç:** Altyapı büyük ölçüde hazır; asıl iş (a) H2'yi söküp Testcontainers'a geçmek, (b) kopya helper'ları modüle taşımak, (c) her servise gerçek-PG test eklemek, (d) JaCoCo kurmak.

## 1. Kabul Kriterleri (Faz 2 "bitti" tanımı)

1. `wms-inventory-service` ve `wms-outbound-service` test yolunda H2 kalmadı; `application-test.yml`'deki H2 datasource kaldırıldı, mevcut repository testleri gerçek PG'de yeşil.
2. Her 7 Java servisinde **en az bir** gerçek-PostgreSQL repository/JPA testi koşuyor (core'da zaten var).
3. Ortak Testcontainers altyapısı `wms-test-support` modülünde; 5 kopya `MigrationSmokeSupport` tek kaynağa indirildi.
4. JaCoCo parent pom'da; her `mvn verify` coverage raporu üretiyor, CI'da artefakt olarak yükleniyor. Başlangıç eşiği = ölçülen mevcut değer (build kırmayacak şekilde), hedef kademeli %60 satır.
5. Yeni hiçbir DB testi H2 ile yazılmıyor (strateji İlke: "H2 yasağı").

## 2. İş Kalemleri (uygulama sırası)

### 2.1 `wms-test-support` modülü (önce bu — diğerleri buna dayanır)
- Monorepo'ya yeni Maven modülü: `wms-test-support` (parent pom'a `<module>` olarak eklenir).
- İçerik:
  - `PostgresTestContainer` — singleton `PostgreSQLContainer<>("postgres:16-alpine")`, `reuse` açık; tüm servislerin paylaştığı tek container tanımı. (`postgres:16-alpine`, mevcut IT'lerle aynı imaj.)
  - `MigrationChainSupport` — bugünkü 5 kopya `MigrationSmokeSupport`'un tek gövdesi (`moduleMigrations`, `migrateChain`).
  - İsteğe bağlı `AbstractPostgresDataJpaTest` base sınıfı: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@ServiceConnection` ortak anotasyon yığını.
- Bağımlılık scope: `test` tarafında tüketilir; modülün kendisi `testcontainers`, `flyway-core`, `spring-boot-testcontainers` bağımlılıklarını `compile`/`provided` taşır.
- **Dikkat:** dblink zinciri filesystem location'a (`user.dir`'in parent'ı) bağlı; helper modüle taşınınca `user.dir` çağıran servis modülü olmaya devam eder — path mantığı korunur, davranış değişmez. Taşıma sonrası 5 `MigrationSmokeIT` hâlâ yeşil olmalı (regresyon kapısı).

### 2.2 H2 → Testcontainers geçişi (inventory + outbound)
- `application-test.yml`'den H2 datasource, `H2Dialect`, `ddl-auto=create-drop` çıkarılır. Flyway açılır (`enabled: true`) ki test gerçek şema üzerinde koşsun.
- `InventoryRepositoryTest` / `OutboundOrderRepositoryTest`: `@AutoConfigureTestDatabase(replace=NONE)` + `@ServiceConnection` PostgreSQLContainer (core'daki pattern). `@ActiveProfiles("test")` kalır ama profil artık H2 tanımlamaz.
- **Kritik nüans:** bu iki servisin migration'ları dblink ile upstream'e bağımlı (outbound→core+finance+localization, inventory→core). Saf repository testi için iki yol:
  - **A (önerilen, hızlı):** Test sadece kendi servisinin tablolarına dokunuyorsa, dblink migration'larını atlayacak bir test-only Flyway location ya da `V…__` filtreleme; container'a yalnız ilgili şema kurulur.
  - **B (tam gerçeklik):** `MigrationChainSupport` ile upstream DB'leri de aynı container'da kur (MigrationSmokeIT'nin yaptığı gibi), sonra `@ServiceConnection` yerine elle kurulan JDBC URL'i `@DynamicPropertySource` ile bağla.
  - İlk PR'da A ile başla; dblink'e gerçekten ihtiyaç duyan test çıkarsa B'ye geç. Kararı ilk testi yazarken migration'lara bakıp ver.
- Mevcut `@SpringBootTest` (inventory 3, outbound 3) testleri de aynı datasource'u kullanacağından H2 kalkınca kırılabilir → bunları da Testcontainers'a bağla veya Kafka gibi dış bağımlılıkları mock/disable tut (outbound zaten `KafkaAutoConfiguration` exclude ediyor).

### 2.3 Kalan 4 servise gerçek-PG repository testi (billing, finance, integration, localization)
- Öncelik sırası (stratejiden): **integration-service** (en riskli/en az testli) → **billing** → **inventory** (zaten 2.2'de) → finance/localization.
- Her serviste 1 anlamlı repository testi: en kritik entity'nin kaydet/sorgula + PG'ye özgü davranış (JSONB, TIMESTAMPTZ, `gen_random_uuid()`, partial index/filtre) doğrulaması. H2'nin yakalayamayacağı tam da bunlar.
- Base sınıf `wms-test-support`'tan; container reuse sayesinde ek testler build süresini az artırır.

### 2.4 JaCoCo + coverage raporu
- Parent pom'a `jacoco-maven-plugin`: `prepare-agent` + `report` (`verify` fazına bağlı). Aggregate rapor için `report-aggregate` `wms-test-support` veya ayrı bir `wms-coverage` modülünde toplanabilir.
- Başlangıç eşiği: önce `mvn verify` ile mevcut değeri ölç, `haltOnFailure` eşiğini o değerin biraz altına koy (build kırma). Hedef %60'a kademeli çıkış ayrı görev.
- CI (`.github/workflows/ci.yml`) backend job'ına: `jacoco` HTML/XML raporunu `actions/upload-artifact` ile yükle. (İleride PR yorumu için `jacoco-report` action opsiyonel.)

## 3. Riskler ve Önlemler

| Risk | Önlem |
|---|---|
| dblink migration'ları saf repository testinde patlar | 2.2'de A/B kararı; ilk testte migration bağımlılığını kontrol et. |
| Container reuse CI'da kapalı → yavaşlık | `~/.testcontainers.properties` yerine reuse'ı yalnız lokal aç; CI'da tek container/JVM fork ile idare et (`forkCount=1` test tarafında). |
| H2 kalkınca mevcut `@SpringBootTest`'ler kırılır | Geçişi test-test PR'ıyla yap; kırılanları aynı PR'da Testcontainers'a bağla. |
| `wms-test-support` bağımlılık döngüsü (servis→support→?) | support hiçbir servise bağımlı olmamalı; yalnız testcontainers/flyway/spring-test taşır. |
| Sandbox'tan mounted klasöre kopya yazımda kesik senkron | Kod yazımını Windows-side araçlarla yap, yazım sonrası Grep ile doğrula (bkz. memory). |

## 4. Uygulama Sırası (PR bazında)

1. **PR-1:** `wms-test-support` modülü + 5 `MigrationSmokeSupport` kopyasını modüle indir; 5 `MigrationSmokeIT` yeşil kalıyor (regresyon kapısı).
2. **PR-2:** inventory + outbound H2 sökümü → Testcontainers; mevcut repository + `@SpringBootTest` testleri yeşil.
3. **PR-3:** integration-service ilk gerçek-PG repository testi.
4. **PR-4:** billing + finance + localization birer gerçek-PG testi.
5. **PR-5:** JaCoCo parent pom + CI artefakt + eşik.

Her PR bağımsız yeşil; CI kapısı Faz 1'de kuruldu.

## 5. Kodlamaya Geçince İlk Adım
`wms-test-support` iskeleti (pom + `MigrationChainSupport` + `AbstractPostgresDataJpaTest`) — PR-1. Bunu onaylarsan modülü kurup 5 kopyayı tek kaynağa indirerek başlarım.
