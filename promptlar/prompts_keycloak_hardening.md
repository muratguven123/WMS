# WMS Keycloak Güvenlik Sıkılaştırma (Security Hardening) Promptu

Aşağıdaki prompt, Faz 9 tamamlandıktan sonra tüm mikroservislerinizin önüne **Keycloak (OAuth2 / JWT)** güvenliğini eklemek ve servislerdeki API uçlarını (endpoints) yetkilendirilmiş rollerle koruma altına almak için hazırlanmıştır.

---

## 🔐 Keycloak Güvenlik Sıkılaştırma Promptu (Faz 9 Sonrası İçin)

```text
Rolün: Senior Security Architect & Lead Spring Security Developer.
Proje Bağlamı: Java 21, Spring Boot 3.x, JPA ve PostgreSQL kullanan WMS projemizin tüm iş mantığı (Faz 1 - Faz 9) tamamlandı. Şu ana kadar API isteklerinde kullanıcı yetkilerini el ile custom header'lardan alıp doğruluyorduk. 

Görevimiz: Projedeki tüm mikroservislerin güvenlik altyapısını kurumsal seviyeye çıkarmak amacıyla Keycloak (OAuth2 Resource Server & JWT) entegrasyonunu gerçekleştirmek.

Gereksinimler ve Teknik Detaylar:

AŞAMA 1: DOCKER COMPOSE GÜNCELLEMESİ (KEYCLOAK)
Mevcut docker-compose.yml dosyamıza eklenecek olan Keycloak yapılandırmasını üret.
- Keycloak Sürümü: En güncel kararlı sürüm (Örn: keycloak:24.x veya üzeri).
- Port: `8080` (Dışarıdan yönetim paneline erişim için).
- Veritabanı: Yerel PostgreSQL sunucumuzda oluşturacağımız `keycloak_db` veritabanını kullanacak şekilde DB bağlantı çevre değişkenlerini yapılandır.
- Varsayılan Yönetici Bilgileri: admin / admin (Çalışma ortamı için).

AŞAMA 2: SPRING SECURITY VE MAVEN BAĞIMLILIKLARI
1. Parent pom.xml veya tüm mikroservis pom.xml dosyalarına eklenecek olan OAuth2 Resource Server bağımlılığını göster:
   - `spring-boot-starter-oauth2-resource-server`
2. Mikroservislerin application.yml dosyalarına eklenecek olan Keycloak Issuer ve JWK Set URI yapılandırmasını yaz:
   - `spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/wms-realm`
   - `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/realms/wms-realm/protocol/openid-connect/certs`

AŞAMA 3: GÜVENLİK FİLTRESİ REFACTORİNG (TENANT CONTEXT ENTEGRASYONU)
Faz 1'de yazdığımız custom 'TenantContextFilter' yapısını şu şekilde güncelle:
1. Gelen JWT Token'ı Doğrulama: Gelen istekteki Authorization header'ından ("Bearer <token>") JWT token'ı oku ve Spring Security OAuth2 mekanizmasıyla doğrula.
2. Rol ve Kullanıcı Çözümleme:
   - Keycloak JWT'sinin içinden kullanıcının benzersiz ID'sini (`sub` claim) ve rollerini (`realm_access.roles` veya custom client roles) al.
   - Bu `sub` değerini (Keycloak User ID'si) `User` tablomuzdaki `keycloakUserId` alanı ile eşleştirerek yerel kullanıcıyı bul.
   - Bulunan kullanıcının aktif firma/lokasyon yetkilerini `UserAccess` tablosundan sorgula (X-Active-Company-ID ve X-Active-Location-ID header'ları ile doğrula).
3. Context'e Kayıt: Doğrulanan bilgileri `TenantContextHolder` (ThreadLocal) içerisine set et.

AŞAMA 4: API ENDPOINT'LERİNİN KORUNMASI (METHOD SECURITY)
1. Spring Security `@EnableMethodSecurity` yapılandırmasını kur.
2. Yazdığımız tüm mikroservis Controller sınıflarında (Mal Kabul, Envanter, Sipariş Toplama vb.) API uçlarına Keycloak rolleriyle koruma ekle:
   - Örn: `@PreAuthorize("hasRole('ROLE_WAREHOUSE_MANAGER')")` veya `@PreAuthorize("hasAnyRole('ROLE_PICKER', 'ROLE_MANAGER')")`.
3. Spring Security'nin Keycloak JWT içindeki rolleri otomatik tanıması için custom bir `JwtAuthenticationConverter` yaz.

Bana bu 4 aşamanın tüm kodlarını, konfigurasyonlarını ve Keycloak yönetim panelinde (Realm, Client, Roles) yapılması gereken ayarların rehberini içeren temiz Java kodlarını üret.
```
