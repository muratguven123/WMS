package com.wms.inbound.service;

import com.wms.inbound.dto.PutawayRecommendationResponse;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import com.wms.inbound.exception.BusinessException;
import com.wms.inbound.repository.ReceiptRepository;
import com.wms.inbound.security.TenantContext;
import com.wms.inbound.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PutawayService} parametre doğrulama ve tenant izolasyonu testleri. Saf Mockito — Docker gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class PutawayServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long WAREHOUSE_ID = 10L;

    @Mock private ReceiptRepository receiptRepository;
    @Mock private PutawayEngineService putawayEngineService;
    @InjectMocks private PutawayService putawayService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setContext(new TenantContext(1L, COMPANY_ID, WAREHOUSE_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static Receipt receipt(Long warehouseLocationId, ReceiptItem... items) {
        InboundOrder order = InboundOrder.builder()
                .companyId(COMPANY_ID)
                .warehouseLocationId(warehouseLocationId)
                .build();
        Receipt receipt = Receipt.builder().inboundOrder(order).items(List.of(items)).build();
        for (ReceiptItem item : items) {
            item.setReceipt(receipt);
        }
        return receipt;
    }

    private static ReceiptItem item(String productCode, ReceiptItemQcStatus qcStatus) {
        return ReceiptItem.builder()
                .productCode(productCode)
                .quantity(BigDecimal.ONE)
                .qcStatus(qcStatus)
                .build();
    }

    @Test
    @DisplayName("receiptId null ise repository'ye gitmeden NullPointerException atar")
    void nullReceiptId_throws() {
        assertThatThrownBy(() -> putawayService.recommendForItem(null, "SKU-1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> putawayService.recommendForReceipt(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(receiptRepository);
    }

    @Test
    @DisplayName("productCode boşluklardan oluşuyorsa 400 döner")
    void blankProductCode_returnsBadRequest() {
        assertThatThrownBy(() -> putawayService.recommendForItem(1L, "   "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus().value()).isEqualTo(400));

        verifyNoInteractions(receiptRepository);
    }

    @Test
    @DisplayName("Başka depoya ait receipt için 403 döner ve öneri hesaplanmaz")
    void receiptFromAnotherWarehouse_isForbidden() {
        when(receiptRepository.findWithDetailsById(1L))
                .thenReturn(Optional.of(receipt(99L, item("SKU-1", ReceiptItemQcStatus.PASSED))));

        assertThatThrownBy(() -> putawayService.recommendForItem(1L, "SKU-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus().value()).isEqualTo(403));

        verifyNoInteractions(putawayEngineService);
    }

    @Test
    @DisplayName("QC'den geçmemiş kalem için öneri dönmez")
    void itemNotPassedQc_returnsEmpty() {
        when(receiptRepository.findWithDetailsById(1L))
                .thenReturn(Optional.of(receipt(WAREHOUSE_ID, item("SKU-1", ReceiptItemQcStatus.FAILED))));

        assertThat(putawayService.recommendForItem(1L, "SKU-1")).isEmpty();
        verifyNoInteractions(putawayEngineService);
    }

    @Test
    @DisplayName("QC'den geçmiş kalem için motordan öneri alınır")
    void passedItem_returnsRecommendation() {
        when(receiptRepository.findWithDetailsById(1L))
                .thenReturn(Optional.of(receipt(WAREHOUSE_ID, item("SKU-1", ReceiptItemQcStatus.PASSED))));
        when(putawayEngineService.findRecommendedLocation(any(), eq(WAREHOUSE_ID)))
                .thenReturn(Optional.empty());

        Optional<PutawayRecommendationResponse> result = putawayService.recommendForItem(1L, "SKU-1");

        assertThat(result).isPresent();
        assertThat(result.get().productCode()).isEqualTo("SKU-1");
    }
}
