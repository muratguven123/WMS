# Faz 7: Stok, Envanter & Depo İçi Hareketler için LLM Promptları

Aşağıdaki promptlar, Faz 7 kapsamında anlık stok (on-hand) takibi, FIFO/FEFO kurallarına göre stok çıkış sıralaması, depo içi raf transferleri ve periyodik sayım (Cycle Counting) işlemlerinin **Java 21**, **Spring Boot 3.x** ve **PostgreSQL** ile kodlanması için hazırlanmıştır.

Bu modül, yeni kurulacak olan **`wms-inventory-service`** mikroservisi (Port: `5432`, Veritabanı: `wms_inventory_db`) olarak kodlanacaktır.

---

## Prompt 7.1: Inventory ve InventoryTransaction JPA Modelleri (JPA & DB Schema)

```text
Rolün: Senior Java & Database Designer.
Görev: Depo stoklarını ve stok hareket geçmişini (envanter kartı ve hareketleri) yönetmek için gerekli JPA modellerini ve PostgreSQL DDL kodlarını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ERD ilişkilerine uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

JPA Entity Modelleri ve Alanlar:
1. Inventory (id UUID PK, productCode String, storageLocationId UUID (Faz 5'teki StorageLocation tablosuna referans), quantity BigDecimal (18, 4), lotNumber String (nullable), serialNumber String (nullable), status String (Enum: AVAILABLE, BLOCKED, ALLOCATED), expiryDate LocalDate (nullable), companyId UUID, warehouseLocationId UUID, updatedAt LocalDateTime)
2. InventoryTransaction (id UUID PK, transactionType String (Enum: PUTAWAY, INTERNAL_MOVE, PICKING, ADJUSTMENT), sourceLocationId UUID (nullable), targetLocationId UUID (nullable), productCode String, quantity BigDecimal (18, 4), lotNumber String (nullable), serialNumber String (nullable), transactionDate LocalDateTime, performedByUserId UUID)

Özel Kurallar:
- Benzersiz Stok Kısıtı: Bir depo gözünde (storageLocationId) aynı lot ve seri numarasına sahip aynı üründen sadece tek bir stok kaydı olabilir. Bu durumu `UniqueConstraint` ile yönet (Update/Upsert mantığı için).
- JPA mapping annotasyonlarını fetch=FetchType.LAZY ve JoinColumn kullanarak oluştur.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

---

## Prompt 7.2: FIFO / FEFO Rotasyon Kuralları ve Stok Tahsis Servisi (Allocation Engine)

```text
Rolün: Senior Java Developer & Algorithm Specialist.
Görev: Sipariş sevkiyatı yapılacağı zaman, stokların FIFO (İlk Giren İlk Çıkar) veya FEFO (Son Kullanma Tarihi En Yakın Olan İlk Çıkar) kurallarına göre hangi raflardan toplanması gerektiğini belirleyen stok tahsis motorunu (Allocation Engine) yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Aşağıdaki kurallara uygun `InventoryAllocationService` sınıfını kodla:

Akış ve Kurallar:
1. Tahsis Metodu:
   `List<AllocatedStockDto> allocateStock(String productCode, BigDecimal requiredQuantity, String allocationStrategy, UUID warehouseLocationId)`
   (allocationStrategy: FIFO, FEFO).
2. FIFO Stratejisi:
   - Veritabanındaki `Inventory` tablosundan, durumu `AVAILABLE` olan aktif stokları en eski `updatedAt` (giriş tarihi) tarihine göre sıralayarak sorgula.
3. FEFO Stratejisi:
   - Son kullanma tarihi olan ürünler için, durumu `AVAILABLE` olan aktif stokları en yakın `expiryDate` tarihine göre sıralayarak sorgula. Expiry date null olan stokları en sona koy.
4. Rezervasyon (Allocation) Mantığı:
   - İhtiyaç duyulan miktar karşılanana kadar stok satırlarını gez.
   - Seçilen stok miktarlarının durumunu `AVAILABLE`'dan `ALLOCATED` durumuna çek (Böylece başka bir toplamada mükerrer rezerve edilmezler).
   - Ayrılan her stok satırı için lokasyon ID'si, lot numarası ve rezerve edilen miktarı içeren `AllocatedStockDto` listesi dön.

Bana bu tahsis motorunu (`InventoryAllocationService`) ve test senaryolarını içeren Java kodlarını üret.
```

---

## Prompt 7.3: Depo İçi Transfer ve Sayım (Cycle Counting) API'leri

```text
Rolün: Senior Web API Developer.
Görev: Operatörlerin depo içinde bir ürünü başka bir rafa taşımasını (Internal Movement) sağlayan ve periyodik sayım (Cycle Counting) farklarına göre stok güncelleyen API'leri yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

REST API Tasarımı:
1. Depo İçi Transfer API (POST /api/inventory/move):
   - İstek parametreleri: `sourceLocationId`, `targetLocationId`, `productCode`, `quantity`, `lotNumber`.
   - Kaynak konumdaki stoğu azalt, hedef konumdaki stoğu artır (Hedef lokasyonun kapasite kontrolü için Faz 5'teki `LocationCapacityService` ile entegre çalış).
   - İşlemi `InventoryTransaction` tablosuna `INTERNAL_MOVE` olarak kaydet.
2. Sayım Başlat ve Kapat API (POST /api/inventory/count/adjust):
   - Sayım anındaki sistem stoğu ile fiili sayılan stok arasındaki farkı (`varianceQuantity`) hesapla.
   - Eğer fark varsa, stok miktarını güncelle ve `ADJUSTMENT` tipinde bir stok hareketi kaydı oluştur.

Bana REST Controller sınıfını, transfer ve sayım servis metotlarını temiz Java kodu olarak üret.
```
