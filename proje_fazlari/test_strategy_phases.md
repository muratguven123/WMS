# WMS Test Stratejisi — Fazlı Plan

> Tarih: 2026-07-09 · Durum: Faz 1 uygulanıyor
> Kapsam: 9 Java servisi (Maven monorepo, Java 21, Spring Boot 3.2.5, Kafka, PostgreSQL) + wms-ui (React/Vite)

## 1. Mevcut Durum Analizi

| Alan | Durum |
|---|---|
| Unit test | ~97 dosya / ~586 main sınıf; ağırlıklı Mockito + MockMvc, kalite makul |
| Entegrasyon testi | Yalnızca core (1) ve localization (3 dosya); diğer servislerde yok |
| DB testleri | Outbound/inventory `H2 MODE=PostgreSQL` kullanıyor — gerçek PG davranışını (dblink, şema, PG'ye özgü SQL) test edemez |
| Kafka testleri | Yok; test profilinde Kafka mock'lanıyor veya kapatılıyor |
| Kontrat testleri | Yok (wms-common-events paylaşımlı ama şema doğrulaması yok) |
| UI testleri | Yok (package.json'da test script'i dahi tanımsız) |
| CI | Yok; ayrıca **git reposu da yok** |
| Coverage | Yok (JaCoCo tanımsız) |

### Kritik bulgular (Faz 1'de düzeltildi)
1. **`*IT` testleri hiç koşmuyordu** — failsafe plugin tanımlı değildi; surefire `*IT` desenini toplamaz. Localization'daki migration IT'leri fiilen sadece IDE'den çalıştırılabiliyordu. → Parent pom'a failsafe eklendi.
2. **Testcontainers yalnızca core ve localization'da tanımlıydı** — billing, finance, integration, outbound, inventory pom'larında yoktu. → Beş servise `org.testcontainers:junit-jupiter` + `postgresql` (test scope) eklendi.
3. **`ops` profili** (inbound, inventory, outbound, notification) varsayılan build dışında — CI `-P ops` ile koşmalı.
4. Migration'lar **dblink ile servisler arası bağımlı**: finance→core, billing→core+finance, outbound→core+finance+localization, integration→core, inventory→core. Smoke testler bu zinciri aynı sırayla kurar.

## 2. Fazlar

### Faz 0 — Önkoşul: Git + GitHub (0.5 gün)
- Monorepo kökünde `git init`, `.gitignore` (target/, node_modules/, dist/), GitHub'a push.
- `main` branch koruması: PR zorunlu, CI yeşil olmadan merge yok.
- **Kabul kriteri:** Repo GitHub'da, ilk pipeline tetiklenmiş.

### Faz 1 — CI + Migration Smoke Testleri (bu oturumda kuruldu)
- `.github/workflows/ci.yml`: backend job (`mvn -B -ntp -P ops verify`, Testcontainers için Docker runner'da hazır) + frontend job (`npm ci && lint && build`).
- Parent pom'a maven-failsafe eklendi → `*IT` sınıfları `mvn verify`'da koşar; `mvn test` Docker'sız kalır.
- 5 servise (billing, finance, integration, outbound, inventory) `<Servis>MigrationSmokeIT`: gerçek PostgreSQL 16 (Testcontainers) üzerinde dblink zinciri dahil tüm Flyway migration'larını uygular, `flyway_schema_history` temizliğini ve çekirdek tabloyu doğrular.
- **Kabul kriteri:** Lokalde `mvn -P ops verify` yeşil (Docker açıkken); GitHub'da her push'ta pipeline koşuyor.
- **Not:** İlk koşuda inbound/notification derleme sorunu çıkarırsa geçici olarak `-pl`/profil daraltın, sorunları ayrı düzeltin.

### Faz 2 — Gerçek DB Entegrasyon Testleri + Coverage (1-2 hafta, kademeli)
- H2'den çıkış: repository ve `@SpringBootTest` testlerinde `@ServiceConnection` + PostgreSQLContainer; H2 config'leri kaldırılana kadar yeni test H2 ile yazılmaz.
- Ortak test altyapısı `wms-test-support` modülüne taşınır (container singleton'ları, migration chain helper — şu an her serviste kopya).
- JaCoCo parent pom'a eklenir; başlangıç eşiği ölçülen mevcut değer, hedef kademeli %60 satır.
- Öncelik sırası: integration-service (en riskli/en az testli) → billing → inventory.
- **Kabul kriteri:** Her serviste en az bir gerçek-PG repository testi; coverage raporu CI artefaktı.

### Faz 3 — Kafka Event Testleri (1 hafta)
- Testcontainers Kafka (veya hız için Redpanda) ile producer→broker→consumer round-trip; Awaitility ile asenkron doğrulama, `sleep` yasak.
- Kapsam: kritik event'ler (stok hareketi, sipariş durum geçişleri, billing tetikleyicileri). Producer: doğru topic + serileştirme; consumer: idempotency + hata yolu (poison message).
- **Kabul kriteri:** Kritik her event akışı için en az bir round-trip IT.

### Faz 4 — Kontrat Testleri (3-5 gün)
- `wms-common-events` şemaları sabitlenir: her event sınıfı için serileştirilmiş JSON snapshot testi (geriye dönük uyumsuz değişiklik build'i kırar).
- REST için: springdoc OpenAPI çıktısı CI'da üretilip commit'lenen şema ile diff'lenir (breaking change tespiti). İleride gerekirse Pact.
- **Kabul kriteri:** Event/endpoint şemasında uyumsuz değişiklik CI'da kırmızı.

### Faz 5 — UI Testleri (1 hafta, kademeli)
- vitest + React Testing Library: kritik bileşenler ve form validasyonları; `npm test` script'i CI frontend job'ına eklenir.
- Playwright smoke: login → temel navigasyon → bir CRUD akışı (mock API veya compose'daki gerçek backend'e karşı).
- **Kabul kriteri:** CI frontend job'ı lint+build+test koşuyor; en az 1 Playwright smoke senaryosu.

### Faz 6 — Ayrı E2E Reposu (1-2 hafta)
- Bağımsız `wms-e2e-tests` reposu: kökteki `docker-compose.yml` temel alınarak tüm stack (PG, Kafka, Redis, servisler, UI) ayağa kalkar.
- RestAssured ile iş akışı senaryoları (endpoint değil süreç bazlı): inbound→putaway→stok, outbound→pick→pack→ship→billing.
- Etiketler: `@smoke` (5-10 dk, her deploy sonrası) / `@regression` (nightly). Aynı suite parametreyle staging'e karşı da koşabilir.
- Servis pipeline'ları imaj publish sonrası e2e reposunu tetikler (repository dispatch).
- **Kabul kriteri:** Nightly regression koşusu; smoke suite deploy kapısı.

## 3. İlkeler
- **Piramit korunur:** unit (servis içi) > entegrasyon (servis sınırı) > e2e (sistem). Kontrat doğrulaması e2e'ye yıkılmaz.
- **Koşmayan test yoktur:** her test türü CI'da bir aşamaya bağlanmadan "var" sayılmaz.
- **Test verisi izole:** her test kendi verisini kurar; paylaşımlı seed'e bağımlılık yasak.
- **H2 yasağı:** yeni DB testi yalnızca Testcontainers PostgreSQL ile yazılır.

## 4. Efor Özeti

| Faz | Süre | Bağımlılık |
|---|---|---|
| 0 — Git/GitHub | 0.5 gün | — |
| 1 — CI + smoke IT | 1 gün (kuruldu) | Faz 0 (pipeline'ın koşması için) |
| 2 — DB entegrasyon + coverage | 1-2 hafta | Faz 1 |
| 3 — Kafka | 1 hafta | Faz 1 |
| 4 — Kontrat | 3-5 gün | Faz 3 |
| 5 — UI | 1 hafta | Faz 1 |
| 6 — E2E repo | 1-2 hafta | Faz 2-4 |
