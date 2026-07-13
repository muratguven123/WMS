package com.wms.integration.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutboxRetryPolicy} — exponential backoff ve max-attempts kuralları.
 *
 * <p>Backoff assert'leri deterministiktir: {@code nextAttemptAt} çağrısı
 * {@code before}/{@code after} zaman damgaları arasında yapılır ve sonucun
 * {@code [before + n dk, after + n dk]} penceresinde olması beklenir —
 * böylece tam dakika değeri (1/2/4) milisaniye toleransıyla kanıtlanır.</p>
 */
@DisplayName("OutboxRetryPolicy — exponential backoff & max attempts")
class OutboxRetryPolicyTest {

    private OutboxRetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new OutboxRetryPolicy();
        setField(retryPolicy, "maxAttempts", 3);
        setField(retryPolicy, "maxBackoffMinutes", 60);
    }

    // =====================================================================
    // Senaryo 1 — Üstel bekleme süresi: 2^retryCount dakika
    // =====================================================================

    @ParameterizedTest(name = "retryCount={0} → +{1} dakika")
    @CsvSource({
            "0, 1",   // ilk hata  → 2^0 = 1 dk
            "1, 2",   //            2^1 = 2 dk
            "2, 4"    //            2^2 = 4 dk
    })
    @DisplayName("nextAttemptAt = now + 2^retryCount dakika")
    void nextAttemptAt_exactExponentialBackoff(int retryCount, long expectedMinutes) {
        OffsetDateTime before = OffsetDateTime.now();
        OffsetDateTime next = retryPolicy.nextAttemptAt(retryCount);
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(next)
                .isAfterOrEqualTo(before.plusMinutes(expectedMinutes))
                .isBeforeOrEqualTo(after.plusMinutes(expectedMinutes));
    }

    @Test
    @DisplayName("Backoff üst sınırı: 2^10 = 1024 dk yerine maxBackoffMinutes (60) uygulanır")
    void nextAttemptAt_cappedAtMaxBackoffMinutes() {
        OffsetDateTime before = OffsetDateTime.now();
        OffsetDateTime next = retryPolicy.nextAttemptAt(10);
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(next)
                .isAfterOrEqualTo(before.plusMinutes(60))
                .isBeforeOrEqualTo(after.plusMinutes(60));
    }

    // =====================================================================
    // Senaryo 2 — Max attempts aşımı
    // =====================================================================

    @Test
    @DisplayName("maxAttempts=3: isExhausted(3+)=true, daha küçük değerlerde false")
    void isExhausted_boundaryChecks() {
        assertThat(retryPolicy.isExhausted(0)).isFalse();
        assertThat(retryPolicy.isExhausted(1)).isFalse();
        assertThat(retryPolicy.isExhausted(2)).isFalse();
        assertThat(retryPolicy.isExhausted(3)).isTrue();   // sınır: tam max değerinde tükenir
        assertThat(retryPolicy.isExhausted(4)).isTrue();
    }

    // =====================================================================
    // Yardımcı — @Value alanlarını testte doldurur
    // =====================================================================

    private static void setField(Object target, String fieldName, int value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
