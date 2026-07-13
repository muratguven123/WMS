# WMS Kafka Entegrasyonu ve Kurulum Promptu

Aşağıdaki prompt, projenize **Kafka (KRaft)** altyapısını Docker üzerinde kurmak, Spring Boot parent ve microservice bağımlılıklarını eklemek ve Faz 6 kapsamında mal kabul onaylandığında envanter servisine gönderilmek üzere event yayınlama (Publish) mekanizmasını kodlatmak için hazırlanmıştır.

---

## 🚀 Kafka Entegrasyon Promptu

```text
Rolün: Senior Java Architect & Event-Driven Systems Expert.
Proje Bağlamı: Java 21, Spring Boot 3.x, JPA ve PostgreSQL kullanan WMS projemize asenkron ve gevşek bağlı (loose coupling) haberleşmeyi sağlamak amacıyla Kafka entegrasyonu yapıyoruz.

Görevimiz: 
1. Docker üzerinde Zookeeper gerektirmeyen modern KRaft moduyla Kafka sunucusunu yapılandırmak.
2. Spring Boot projemizin Maven bağımlılıklarına Spring Kafka eklemek.
3. 'wms-inbound-service' mikroservisinde Producer (Yayınlayıcı) ayarlarını ve JSON serileştirme yapılandırmasını kurmak.
4. Mal kabul (Receipt) onaylandığında, envanter servisinin dinleyebileceği bir 'ReceiptApprovedEvent' nesnesini Kafka'ya gönderecek yapıyı yazmak.

İsterler ve Teknik Detaylar:

AŞAMA 1: DOCKER COMPOSE GÜNCELLEMESİ
Mevcut docker-compose.yml dosyamıza eklenecek olan Kafka (KRaft modu, bitnami/kafka:latest veya confluentinc/cp-kafka:latest sürümü) yapılandırmasını üret.
- Host Portu: 9092 dış bağlantı için, 9093 dahili docker ağı için ayarlanmalı.
- Ekstra veri kaybı yaşanmaması için Kafka datalarını docker volumelerine bağla.

AŞAMA 2: MAVEN & SPRING BOOT YAPILANDIRMASI
1. Kök dizindeki parent pom.xml veya doğrudan 'wms-inbound-service' pom.xml dosyasına eklenecek 'spring-kafka' bağımlılığını göster.
2. 'wms-inbound-service' application.yml dosyasına eklenecek Kafka Producer ayarlarını yaz:
   - bootstrap-servers: localhost:9092
   - key-serializer: org.apache.kafka.common.serialization.StringSerializer
   - value-serializer: org.springframework.kafka.support.serializer.JsonSerializer (JSON formatında gönderebilmek için)
   - acks: all (Güvenli gönderim için)

AŞAMA 3: EVENT DTO VE PRODUCER KODLARI ('wms-inbound-service')
1. 'ReceiptApprovedEvent' record sınıfını oluştur:
   - fields: receiptId (UUID), inboundOrderId (UUID), companyId (UUID), warehouseLocationId (UUID), approvedAt (Instant), items (List of ReceiptItemEventDto).
   - ReceiptItemEventDto: productCode (String), quantity (BigDecimal), lotNumber (String), serialNumber (String).
2. 'KafkaProducerConfig' adında bir Spring @Configuration sınıfı yaz. Bu sınıf default KafkaTemplate bean'ini yapılandırmalıdır.
3. 'ReceiptEventPublisher' adında bir servis sınıfı yaz:
   - `void publishReceiptApproved(ReceiptApprovedEvent event)` metoduna sahip olsun.
   - Event'i "wms.inbound.receipts" topic'ine KafkaTemplate vasıtasıyla asenkron olarak göndersin. Gönderim sonrası başarı veya hata durumunu loglasın (ListenableFuture/CompletableFuture callback'leri kullanarak).
4. Bu event tetikleyicisini, Faz 6'da yazdığımız 'Receipt' onaylama servis metodunun (`approveReceipt`) sonuna entegre et (Transaction tamamlandıktan sonra çalışması için `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` kullanılmasını tavsiye ederim).

Bana bu 3 aşamanın tüm kodlarını, yapılandırmalarını ve kurulum yönergelerini temiz, üretime hazır Java kodları olarak üret.
```
