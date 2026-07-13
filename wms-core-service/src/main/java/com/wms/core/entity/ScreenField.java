package com.wms.core.entity;

import com.wms.core.entity.enums.FieldBehavior;
import com.wms.core.entity.enums.FieldDataType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Bir ekrana ({@link Screen}) ait tek bir form alanını temsil eder.
 *
 * <p>{@code fieldKey} alan kimliğidir — örn: {@code tax_number}, {@code district},
 * {@code zip_code}. Bu değer aynı zamanda çeviri sistemiyle eşleştirilir:
 * {@code field.<fieldKey>.label} anahtarıyla ilgili dil etiketi çekilir.</p>
 *
 * <p>{@code defaultBehavior}: Hiçbir {@link FieldBehaviorRule} eşleşmediğinde
 * uygulanacak varsayılan davranış.</p>
 */
@Entity
@Table(
        name = "screen_fields",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_screen_field_key",
                columnNames = {"screen_id", "field_key"}
        )
)
@SQLDelete(sql = "UPDATE screen_fields SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenField extends BaseEntity {

    /** Bu alanın ait olduğu ekran. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "screen_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_screen_field_screen")
    )
    private Screen screen;

    /**
     * Alan tanımlayıcısı — örn: {@code tax_number}, {@code district}.
     * Frontend ve çeviri sistemi bu key üzerinden alan etiketini çeker.
     */
    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    /**
     * Hiçbir kural eşleşmediğinde uygulanacak varsayılan davranış.
     * Kural motorunun fallback değeridir.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_behavior", nullable = false, length = 20)
    private FieldBehavior defaultBehavior;

    /** Alanın beklediği veri tipi — frontend bileşen seçiminde kullanılır. */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private FieldDataType dataType;

    /** Bu alana ait bağlamsal davranış kuralları. */
    @OneToMany(mappedBy = "screenField", fetch = FetchType.LAZY)
    @Builder.Default
    private List<FieldBehaviorRule> behaviorRules = new ArrayList<>();
}
