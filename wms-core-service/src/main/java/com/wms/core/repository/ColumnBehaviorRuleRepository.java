package com.wms.core.repository;

import com.wms.core.entity.ColumnBehaviorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColumnBehaviorRuleRepository extends JpaRepository<ColumnBehaviorRule, Long> {

    @Query("""
            SELECT cbr FROM ColumnBehaviorRule cbr
            JOIN FETCH cbr.tableColumnDef tcd
            JOIN FETCH tcd.screen s
            WHERE s.code = :screenCode
            """)
    List<ColumnBehaviorRule> findAllByScreenCode(@Param("screenCode") String screenCode);

    List<ColumnBehaviorRule> findByTableColumnDefId(Long tableColumnDefId);
}
