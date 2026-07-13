# Faz 5: Depo Fiziksel Yerleşim & Adres Yönetimi için LLM Promptları

Aşağıdaki promptlar, Faz 5 kapsamında depo içi alanların (Zone), raf adresleme hiyerarşisinin (Koridor, Raf, Kat, Göz) ve hacimsel/ağırlık bazlı kapasite kontrol mekanizmalarının **Java 21**, **Spring Boot 3.x** ve **PostgreSQL** ile kodlanması için hazırlanmıştır.

Bu modül, çekirdek yapıyı barındıran **`wms-core-service`** mikroservisi içerisine eklenecektir.

---

## Prompt 5.1: Zone ve StorageLocation JPA Entity Modelleri ve DB Şeması

```text
Rolün: Senior Java & Database Designer.
Görev: Depo fiziksel yerleşimini (Alanlar, Koridorlar, Raflar, Gözler) ve kapasite sınırlarını tutacak JPA modellerini ve PostgreSQL DDL kodlarını oluşturmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL, Lombok.

Aşağıdaki kurallara ve ilişkilere uygun olarak JPA modellerini ve SQL/Liquibase/Flyway şemalarını oluştur:

JPA Entity Modelleri ve Alanlar:
1. Zone (id UUID PK, locationId UUID (Faz 1'deki Location tablosuna referans), code String (Unique, örn: COLD_ZONE, QUARANTINE), name String, description String, isActive boolean)
2. StorageLocation (id UUID PK, zone FK, aisle String (Koridor, örn: A, B), bay String (Bölüm, örn: 01, 02), shelf String (Kat/Raf, örn: 03), bin String (Göz, örn: 01), addressCode String (Unique, örn: A-01-03-01), maxVolume BigDecimal (m3 cinsinden, precision: 10, 4), maxWeight BigDecimal (kg cinsinden, precision: 10, 4), currentVolume BigDecimal (precision: 10, 4), currentWeight BigDecimal (precision: 10, 4), status String (Enum: ACTIVE, BLOCKED, FULL), isActive boolean, updatedAt LocalDateTime)

Özel Kurallar:
- Benzersiz Adres: Bir depo içinde aynı koridor, bölüm, kat ve göz kombinasyonundan sadece bir adet bulunabilir. Bu doğrulamayı veritabanı seviyesinde `UniqueConstraint` ile sağla.
- Adres Kodunun Otomatik Üretilmesi (Address Code Builder): `@PrePersist` ve `@PreUpdate` listener'ları kullanarak, adres kaydedilmeden veya güncellenmeden önce `addressCode` alanını otomatik olarak şu formatta birleştirip doldur: `[aisle]-[bay]-[shelf]-[bin]` (Örn: `A-01-03-01`).
- Lombok Annotations: @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor annotations kullan.

Bana temiz, üretime hazır Java JPA Entity kodlarını, Repository sınıflarını ve SQL DDL kodlarını üret.
```

---

## Prompt 5.2: Adres Kapasite Kontrol ve Rezervasyon Servisi (Capacity Validator)

```text
Rolün: Senior Backend & Algorithms Developer.
Görev: Bir ürün depo gözüne yerleştirilmek istendiğinde, o gözün hacimsel ve ağırlık kapasitesinin uygun olup olmadığını doğrulayan ve stok giriş-çıkışlarında kapasite güncelleyen doğrulama servisini yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

Aşağıdaki kurallara uygun `LocationCapacityService` sınıfını kodla:

Metot ve Kurallar:
1. Kapasite Uygunluk Metodu:
   `boolean hasAvailableCapacity(UUID storageLocationId, BigDecimal itemVolume, BigDecimal itemWeight)`
   - Belirtilen lokasyonun mevcut boş hacmini (`maxVolume - currentVolume`) ve boş ağırlık kapasitesini (`maxWeight - currentWeight`) hesapla.
   - Gelen ürünün hacmi ve ağırlığı bu boş kapasiteden küçük veya eşitse `true`, değilse `false` dön.
2. Kapasite Güncelleme Metodu (Stok Girişi/Çıkışı):
   `void updateLocationLoad(UUID storageLocationId, BigDecimal volumeDelta, BigDecimal weightDelta, boolean isAddition)`
   - `isAddition = true` ise, lokasyonun `currentVolume` ve `currentWeight` değerlerini gelen delta değerleri kadar artır. Eğer yeni yük `max` limitleri aşarsa `LocationCapacityExceededException` fırlat.
   - `isAddition = false` ise (stok çıkışı), lokasyonun yükünü delta kadar azalt (Değerlerin sıfırın altına düşmesini engelle).
   - Eğer `currentVolume` ve `currentWeight` değerleri maksimum limitlere ulaştıysa lokasyon durumunu otomatik olarak `FULL` yap, yük azaldığında tekrar `ACTIVE` durumuna çek.
3. Bu metotları transaksiyonel güvenlikle (`@Transactional`) kodla ve doğruluğunu test eden JUnit 5 test sınıfını yaz.

Bana bu kapasite kontrol servisini ve JUnit 5 test sınıfını temiz Java kodu olarak üret.
```

---

## Prompt 5.3: Depo Gözü Yönetimi ve Engelleme (Blocking) REST API'leri

```text
Rolün: Senior Web API Developer.
Görev: Sistem yöneticilerinin ve depo şeflerinin belirli depo gözlerini (StorageLocation) manuel olarak kullanıma kapatmasını (BLOCKED) veya açmasını sağlayan ve lokasyonları filtreleyen API'leri yazmak.

Teknoloji Stack'i: Java 21, Spring Boot 3.x, Spring Data JPA.

REST API Tasarımı:
1. Lokasyon Durumu Güncelleme API (POST /api/locations/{locationId}/status):
   - İstek parametresi: `status` (ACTIVE, BLOCKED).
   - Belirtilen depo gözünün durumunu güncelle. Eğer lokasyon `BLOCKED` yapılırsa, o göze yeni stok girişi yapılamaz.
2. Filtreleme ve Arama API (GET /api/locations/search):
   - `zoneId`, `status`, `aisle` (koridor) ve `isActive` durumuna göre filtrelenebilir, sayfalama destekli (`Pageable`) lokasyon listesini dön.
3. Kapasite Durum Raporu API (GET /api/locations/utilization):
   - Doluluk oranı (Utilization Rate: `(currentVolume / maxVolume) * 100`) %80'in üzerinde olan kritik lokasyonları listeleyen endpoint'i kodla.

Bana bu REST Controller endpoint'lerini, JPA Repository sorgularını ve servis metotlarını temiz Java kodu olarak üret.
```
