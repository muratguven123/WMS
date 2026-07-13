# Faz 9: Sevkiyat & Çıkış İşlemleri için LLM Promptları

Aşağıdaki promptlar, Faz 9 kapsamında koli/palet sevkiyat gruplamaları (Shipment), kargo/taşıyıcı entegrasyonu (Labeling/Tracking), araç yükleme doğrulama API'leri ve ERP sevkiyat tamamlama bildirimlerinin **Java 21**, **Spring Boot 3.x** ve **PostgreSQL** ile kodlanması için hazırlanmıştır.

Bu modül, **`wms-outbound-service`** mikroservisi içerisine veya entegrasyon API'leri ile **`wms-integration-service`** üzerine eklenecektir.

---

## Prompt 9.1: Shipment ve ShipmentItem JPA Modelleri (JPA & DB Schema)

```text
Rolün: Senior Java & Database Designer.
Görev: Sevkiyat yükleme, kargo araç çıkışlarını ve palet/koli eşleşmelerini yönetmek için gerekli JPA modellerini ve PostgreSQL DDL kodlarını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

JPA Entity Modelleri ve Alanlar:
1. Shipment (id UUID PK, shipmentNumber String (Unique, Sevkiyat/Çıkış No), companyId UUID, carrierCode String (örn: DHL, YURTICI), trackingNumber String (Kargo Takip No), status String (Enum: PENDING, LOADED, DISPATCHED), totalBoxes int, totalWeight BigDecimal (Precision: 10, 4), dispatchedAt LocalDateTime)
2. ShipmentItem (id UUID PK, shipment FK, outboundOrderId UUID, boxSsccNumber String (Koli SSCC Barkodu), status String (Enum: STAGED, LOADED))

Özel Kurallar:
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

---

## Prompt 9.2: Taşıyıcı (Kargo) Entegrasyon Servisi (Carrier Integration)

```text
Rolün: Senior Java Integration Specialist.
Görev: Sevkiyat hazırlandığında, harici kargo/taşıyıcı API'lerini (DHL, Yurtiçi vb.) tetikleyerek otomatik kargo takip numarası (Tracking Number) ve kargo barkod etiketi (PDF) alan entegrasyon servisini kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring WebClient (HTTP Web API).

Aşağıdaki entegrasyon kurallarına göre `CarrierIntegrationService` sınıfını kodla:

Akış ve Kurallar:
1. Entegrasyon Metodu:
   `CarrierResponseDto requestShippingLabel(Shipment shipment, ShippingAddressDto address)`
2. Dinamik Entegrasyon Fabrikası (Adapter/Strategy):
   - `carrierCode` parametresine göre (Örn: DHL, YURTICI) ilgili kargo entegrasyon adaptörünü (Spring WebClient çağrısı yapacak şekilde) çağır.
   - DHL için mock REST API çağrısı, Yurtiçi Kargo için SOAP/XML mock çağrısı kurgula.
3. Çıktı:
   - Başarılı istek sonrası alınan takip numarasını (`trackingNumber`) ve dönen Base64 formatındaki etiket PDF verisini içeren `CarrierResponseDto` nesnesini geri dön.

Bana bu kargo entegrasyon servis mimarisini ve örnek mock API çağrılarını temiz Java kodu olarak üret.
```

---

## Prompt 9.3: Araç Yükleme Doğrulama ve ERP Çıkış API'leri (Dispatch & Load Verification)

```text
Rolün: Senior Web API Developer.
Görev: Kolilerin araca yüklenirken SSCC barkodlarının okutulup doğrulanmasını (Loading Verification) sağlayan ve aracı sevk ettikten (Dispatch) sonra ERP'yi güncelleyen API'leri kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

REST API Tasarımı:
1. Araç Yükleme Doğrulama API (POST /api/shipping/verify-load):
   - İstek parametreleri: `shipmentId`, `boxSsccNumber`.
   - Okutulan SSCC barkodunun o sevkiyata ait bir koli olup olmadığını kontrol et. 
   - Varsa, `ShipmentItem` durumunu `LOADED` olarak güncelle. Sevkiyattaki tüm koliler yüklenene kadar araç çıkışına izin verme.
2. Sevkiyat Sevk Et (Dispatch) API (POST /api/shipping/{shipmentId}/dispatch):
   - Sevkiyatın durumunu `DISPATCHED`, `dispatchedAt` zamanını günün tarihi olarak güncelle.
   - Bu işlemle birlikte, stokların depodan fiili çıkışını tamamlamak üzere `wms-inventory-service` ve `wms-integration-service` (Outbox) aracılığıyla ERP sistemine stok düşüm (Inventory Issue) bildirimini tetikle.

Bana REST Controller sınıfını, yükleme doğrulama servis metotlarını ve ERP çıkış outbox tetikleyicisini temiz Java kodu olarak üret.
```
