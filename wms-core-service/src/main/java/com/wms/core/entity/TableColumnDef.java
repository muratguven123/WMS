package com.wms.core.entity;

import com.wms.core.entity.enums.ColumnDataType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Liste ekranı kolon tanımı (İş İsteri 16).
 */
@Entity
@Table(
        name = "table_column_defs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tcd_screen_column",
                columnNames = {"screen_id", "column_key"})
)
@SQLDelete(sql = "UPDATE table_column_defs SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableColumnDef extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tcd_screen"))
    private Screen screen;

    @Column(name = "column_key", nullable = false, length = 100)
    private String columnKey;

    @Column(name = "label_key", nullable = false, length = 255)
    private String labelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    @Builder.Default
    private ColumnDataType dataType = ColumnDataType.STRING;

    @Column(name = "default_visible", nullable = false)
    @Builder.Default
    private boolean defaultVisible = true;

    @Column(name = "default_sequence", nullable = false)
    @Builder.Default
    private int defaultSequence = 0;

    @Column(name = "locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Column(name = "render_hint", length = 50)
    private String renderHint;
}
