package com.wms.core.repository;

import com.wms.core.entity.TableColumnDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableColumnDefRepository extends JpaRepository<TableColumnDef, Long> {

    @Query("""
            SELECT DISTINCT tcd FROM TableColumnDef tcd
            JOIN FETCH tcd.screen
            WHERE tcd.screen.code = :screenCode
            ORDER BY tcd.defaultSequence ASC
            """)
    List<TableColumnDef> findByScreenCodeOrderByDefaultSequence(@Param("screenCode") String screenCode);

    List<TableColumnDef> findByScreenIdOrderByDefaultSequenceAsc(Long screenId);

    Optional<TableColumnDef> findByScreenIdAndColumnKey(Long screenId, String columnKey);

    boolean existsByScreenIdAndColumnKey(Long screenId, String columnKey);
}
