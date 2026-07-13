package com.wms.core.dto.geo;

import java.util.List;

/**
 * CSV eyalet import sonucu (İş İsteri 18 — Teknik İş Kuralı 5).
 *
 * <p>Mükerrer satırlar atlanır ve satır numarasıyla rapor edilir;
 * geçerli satırlar tek transaction'da kaydedilir.</p>
 */
public record StateImportResultDto(
        int imported,
        int skipped,
        List<String> errors
) {}
