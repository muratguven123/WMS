package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistemde tanımlı bir formu/ekranı temsil eder.
 *
 * <p>{@code code} değeri frontend ile kontrattır — örn: {@code MAT_CARD_FORM},
 * {@code REC_CONTROL_FORM}. Bu değer üzerinden form şeması sorgulanır.</p>
 *
 * <p>Soft-delete desteklenir: {@code is_active = false} yapılan ekranlar
 * {@link SQLRestriction} filtresiyle sorgulardan otomatik dışlanır.</p>
 */
@Entity
@Table(
        name = "screens",
        uniqueConstraints = @UniqueConstraint(name = "uk_screen_code", columnNames = "code")
)
@SQLDelete(sql = "UPDATE screens SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen extends BaseEntity {

    /**
     * Frontend ile kontrakt — form tanımlayıcısı.
     * Örn: {@code MAT_CARD_FORM}, {@code REC_CONTROL_FORM}
     */
    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    /** İnsan tarafından okunabilir ekran adı. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Bu ekrana ait form alanları.
     * Cascade ile yönetilmez; alanlar bağımsız yaşam döngüsüne sahiptir.
     */
    @OneToMany(mappedBy = "screen", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ScreenField> fields = new ArrayList<>();
}
