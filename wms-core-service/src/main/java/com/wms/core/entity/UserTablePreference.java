package com.wms.core.entity;

import com.wms.core.dto.ui.ColumnPreferenceEntry;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Kullanıcı bazlı tablo kolon tercihi — tek satır JSONB (İş İsteri 16).
 */
@Entity
@Table(
        name = "user_table_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_utp_user_screen",
                columnNames = {"user_id", "screen_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTablePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false, foreignKey = @ForeignKey(name = "fk_utp_screen"))
    private Screen screen;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<ColumnPreferenceEntry> preferences = new ArrayList<>();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
}
