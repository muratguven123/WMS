package com.wms.inbound.dto;

import java.util.List;

public record CustomPageResponse<T>(
    List<T> content,
    int number,
    int size,
    long totalElements,
    int totalPages,
    boolean last,
    boolean first
) {
}
