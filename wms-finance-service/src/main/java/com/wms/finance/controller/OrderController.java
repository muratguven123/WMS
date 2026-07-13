package com.wms.finance.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.finance.dto.OrderRequestDto;
import com.wms.finance.dto.OrderResponseDto;
import com.wms.finance.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody OrderRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(request));
    }
}
