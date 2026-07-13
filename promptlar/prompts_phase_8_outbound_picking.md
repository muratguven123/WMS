# Faz 8: Sipariş Hazırlama, Toplama & Paketleme için LLM Promptları

Aşağıdaki promptlar, Faz 8 kapsamında Satış Siparişlerinin (Outbound Order) karşılanması, depo içinde en az yürümeyi sağlayan Rota Optimizasyonlu Toplama Listesi (Picking Rote) oluşturulması ve Paketleme (Packing/Validation) işlemlerinin **Java 21**, **Spring Boot 3.x** ve **PostgreSQL** ile kodlanması için hazırlanmıştır.

Bu modül, yeni kurulacak olan **`wms-outbound-service`** mikroservisi (Port: `8088`, Veritabanı: `wms_outbound_db`) olarak kodlanacaktır.

---

## Prompt 8.1: OutboundOrder ve PickingList JPA Modelleri (JPA & DB Schema)

```text
Rolün: Senior Java & Database Designer.
Görev: Müşteri siparişlerini, toplama listelerini ve toplama satırlarını yönetmek için gerekli JPA modellerini ve PostgreSQL DDL kodlarını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

JPA Entity Modelleri ve Alanlar:
1. OutboundOrder (id UUID PK, orderNumber String (Unique, Müşteri/Sipariş No), customerId UUID, companyId UUID, orderDate LocalDateTime, status String (Enum: PENDING, ALLOCATED, PICKING, PACKED, SHIPPED), shippingAddressId UUID, createdAt LocalDateTime)
2. OutboundOrderItem (id UUID PK, outboundOrder FK, productCode String, quantity BigDecimal (18, 4), allocatedQuantity BigDecimal (18, 4), pickedQuantity BigDecimal (18, 4))
3. PickingList (id UUID PK, warehouseLocationId UUID, createdByUserId UUID, status String (Enum: PENDING, IN_PROGRESS, COMPLETED), createdAt LocalDateTime)
4. PickingItem (id UUID PK, pickingList FK, outboundOrderItem FK, sourceLocationId UUID (Raftaki adres), addressCode String (örn: A-12-03-01), quantityToPick BigDecimal (18, 4), pickedQuantity BigDecimal (18, 4), status String (Enum: PENDING, PICKED, SHORTAGE))

Özel Kurallar:
- Decimal Formatı: Tutar ve miktarlar için `BigDecimal` (18, 4) kullan.
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

---

## Prompt 8.2: Rota Optimizasyonlu Toplama Listesi Oluşturucu (Picking Router)

```text
Rolün: Senior Java Developer & Route Optimization Specialist.
Görev: Rezervasyonu tamamlanmış sipariş kalemleri için, depo görevlisinin en az yürümesini sağlayacak şekilde (S-Shape toplama rotası mantığıyla) sıralı bir toplama listesi (PickingList) oluşturan algoritmayı kodlamak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, WebClient.

Aşağıdaki kurallara uygun `PickingRoutingService` sınıfını kodla:

Akış ve Kurallar:
1. Toplama Listesi Yaratma:
   `PickingList createPickingList(List<UUID> outboundOrderIds, UUID warehouseLocationId)`
2. Stok Tahsis İstediği (Allocation Check):
   - `wms-inventory-service` servisinin `/api/inventory/allocate` endpoint'ini çağırarak sipariş edilen ürünlerin hangi raf konumlarından (`sourceLocationId` ve `addressCode`) kaç adet ayrılacağını (FIFO/FEFO'ya göre) belirle.
3. Rota Optimizasyon Sıralaması (S-Shape Routing):
   - Alınan toplama konumlarını, depo içindeki yılan kıvrımı (S-Shape) rotasına uyması için şu sıralama hiyerarşisine göre sırala:
     - 1. `aisle` (Koridor alfabetik sırası: A, B, C...)
     - 2. Çift numaralı koridorlar için `bay` (bölüm) sırası küçükten büyüğe, tek numaralı koridorlar için `bay` sırası büyükten küçüğe (Böylece görevli koridorun birinden girip diğerinden geri çıkar, gereksiz dönüş yapmaz).
     - 3. `shelf` (kat) sırası aşağıdan yukarıya.
4. Bu şekilde sıralanmış konumları `PickingItem` satırları olarak `PickingList` içine kaydet.

Bana bu rota optimizasyonu sıralamasını ve toplama listesi oluşturma metodunu temiz Java kodu olarak üret.
```

---

## Prompt 8.3: Paketleme (Packing Verification) ve Barkod Kontrol API'leri

```text
Rolün: Senior Web API Developer.
Görev: Toplama arabasıyla paketleme masasına gelen ürünlerin barkodlarının okutularak doğrulanmasını (Packing Verification) ve koli/SSCC etiketlerinin üretilmesini sağlayan API'leri yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

REST API Tasarımı:
1. Paketleme Masası Giriş API (POST /api/packing/verify):
   - İstek parametreleri: `pickingListId`, `productBarcode`, `scannedQuantity`.
   - Toplanan ürün barkodu okutulduğunda, `PickingItem` listesindeki miktar ile karşılaştır.
   - Doğrulanan miktarı `pickedQuantity` olarak güncelle. Fazla veya eksik okutma durumlarında uyarı fırlat.
2. Koli Kapat & Etiket Üret API (POST /api/packing/close-box):
   - Paketlemesi biten koli için yeni bir SSCC (Serial Shipping Container Code - 18 haneli standart barkod) numarası üret.
   - Koli içeriğini ve paketlenen sipariş durumunu `PACKED` olarak güncelle.
   - Onaylanan siparişi sevkiyata göndermek üzere transactional outbox tablosuna yaz.

Bana REST Controller sınıfını, paket doğrulama servis metotlarını ve SSCC etiket üretim kodlarını temiz Java kodu olarak üret.
```
