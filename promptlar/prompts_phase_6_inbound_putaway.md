# Faz 6: Mal Kabul & Akıllı Raf Yerleştirme için LLM Promptları

Aşağıdaki promptlar, Faz 6 kapsamında Satın Alma Siparişlerinin (Purchase Order) karşılanması, mal kabul (Receipt) kalite kontrol süreçleri ve ürün özelliklerine göre en uygun raf konumunu öneren Akıllı Raf Yerleştirme (Directed Putaway) motorunun **Java 21**, **Spring Boot 3.x** ve **PostgreSQL** ile kodlanması için hazırlanmıştır.

Bu modül, yeni kurulacak olan **`wms-inbound-service`** mikroservisi (Port: `8086`, Veritabanı: `wms_inbound_db`) olarak kodlanacaktır.

---

## Prompt 6.1: InboundOrder ve Receipt JPA Entity Modelleri ve DB Şeması

```text
Rolün: Senior Java & Database Designer.
Görev: Satın alma siparişlerini ve mal kabul kayıtlarını yönetmek için gerekli JPA modellerini ve PostgreSQL DDL kodlarını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

JPA Entity Modelleri ve Alanlar:
1. InboundOrder (id UUID PK, orderNumber String (Unique, ERP Sipariş No), companyId UUID, supplierName String, orderDate LocalDateTime, status String (Enum: PENDING, RECEIVING, COMPLETED, CANCELLED), createdAt LocalDateTime)
2. InboundOrderItem (id UUID PK, inboundOrder FK, productCode String, quantity BigDecimal (18, 4), receivedQuantity BigDecimal (18, 4), uom String (Birim), unitVolume BigDecimal, unitWeight BigDecimal)
3. Receipt (id UUID PK, inboundOrder FK, receiptNumber String (Unique, İrsaliye/Kabul No), receivedByUserId UUID, receivedAt LocalDateTime, status String (Enum: QC_PENDING, APPROVED, REJECTED))
4. ReceiptItem (id UUID PK, receipt FK, productCode String, quantity BigDecimal (18, 4), lotNumber String (nullable), serialNumber String (nullable), qcStatus String (Enum: PASSED, FAILED))

Özel Kurallar:
- Decimal Formatı: Tutar ve miktarlar için `BigDecimal` (18, 4) kullan.
- JPA mappings fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

---

## Prompt 6.2: Akıllı Yerleştirme Motoru ve Rota Öneri Servisi (Putaway Strategy)

```text
Rolün: Senior Java Developer & Algorithm Specialist.
Görev: Mal kabulü tamamlanan bir ürün için ürün tipine, soğuk zincir gereksinimine ve en yakın boş kapasiteye göre en uygun depo gözünü (StorageLocation) bulup öneren akıllı yerleştirme motorunu (Putaway Strategy Engine) kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, WebClient (Core servise bağlanmak için).

Aşağıdaki kurallara uygun `PutawayEngineService` sınıfını kodla:

Akış ve Kurallar:
1. Strateji Arayüzü (Strategy Pattern):
   - `Optional<UUID> findPutawayLocation(ReceiptItem item, UUID warehouseLocationId)`
2. Somut Stratejiler (Concrete Strategies):
   - `ZoneMatchStrategy`: Ürünün kategorisine (Örn: Soğuk Gıda ise COLD_ZONE, kimyasal ise HAZMAT_ZONE) uygun alanlardaki (Zone) boş lokasyonları bulur.
   - `CapacityMatchStrategy`: Bulunan lokasyonlar arasından, ürünün hacim (`unitVolume * quantity`) ve ağırlık (`unitWeight * quantity`) yükünü kaldırabilecek, en yüksek boş kapasiteye sahip olan lokasyonları filtreler.
3. Servis Entegrasyonu:
   - `wms-core-service` mikroservisinin `/api/locations/search` endpoint'ini Spring WebClient ile çağırarak o depodaki aktif ve boş konumları sorgula.
   - En uygun lokasyonu (örneğin doluluk oranı en düşük veya koridor sırasına göre en yakın olanı) belirleyip kullanıcı el terminaline/arayüze öneri olarak dön.

Bana bu Strategy Pattern yapısını, WebClient entegrasyonunu ve test senaryolarını içeren Java kodlarını üret.
```

---

## Prompt 6.3: Mal Kabul ve Kalite Kontrol (QC) Onay API'leri

```text
Rolün: Senior Web API Developer.
Görev: Depo görevlilerinin mal kabul (Receipt) adımlarını yürütebileceği, kalite kontrol sonuçlarını sisteme kaydedebileceği ve kabulü onaylanan stokları envantere göndermek üzere ERP'ye bildiren API'leri kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

REST API Tasarımı:
1. Mal Kabul Başlat API (POST /api/inbound/receipts):
   - `inboundOrderId` ve irsaliye detaylarıyla yeni bir `Receipt` kaydı oluştur.
2. Kalite Kontrol Giriş API (POST /api/inbound/receipts/{receiptId}/qc):
   - Mal kabul edilen kalemlerin QC durumlarını (`PASSED`, `FAILED`) ve varsa seri/lot numaralarını kaydet.
3. Mal Kabul Kapat & Onayla API (POST /api/inbound/receipts/{receiptId}/approve):
   - QC durumu `PASSED` olan tüm kalemleri onaylanmış kabul et.
   - Durumunu `APPROVED` olarak güncelle.
   - Onaylanan stok bilgilerini (Outbox Message kullanarak) `wms-integration-service` üzerinden ERP sistemine bildiren işlemsel kaydı at (Transactional Outbox).

Bana REST Controller sınıfını, QC doğrulama servis metotlarını ve örnek entegrasyon kuyruğu tetikleyicisini temiz Java kodu olarak üret.
```
