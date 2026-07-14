package com.wms.localization.service.notification;

import com.wms.localization.dto.ImportResultDto;
import com.wms.localization.dto.notification.*;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.NotificationChannel;
import com.wms.localization.entity.NotificationTemplate;
import com.wms.localization.entity.NotificationTemplateContent;
import com.wms.localization.exception.notification.NotificationTemplateConflictException;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.MissingTemplateLogRepository;
import com.wms.localization.repository.NotificationTemplateContentRepository;
import com.wms.localization.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateAdminServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationTemplateContentRepository contentRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private MissingTemplateLogRepository missingTemplateLogRepository;

    @Mock
    private NotificationTemplateRenderService renderService;

    @InjectMocks
    private NotificationTemplateAdminService adminService;

    private NotificationTemplate template;
    private Language langTr;

    @BeforeEach
    void setUp() {
        template = NotificationTemplate.builder()
                .id(1L)
                .templateCode("RECEIPT_APPROVED_MAIL")
                .channel(NotificationChannel.EMAIL)
                .description("Mal kabul onay maili")
                .active(true)
                .build();

        langTr = Language.builder().code("tr").name("Türkçe").isActive(true).build();
    }

    @Test
    void listTemplates_returnsList() {
        when(templateRepository.findAllByOrderByTemplateCodeAsc()).thenReturn(List.of(template));
        List<NotificationTemplateDto> result = adminService.listTemplates();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTemplateCode()).isEqualTo("RECEIPT_APPROVED_MAIL");
    }

    @Test
    void getTemplateDetail_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(contentRepository.findAllByTemplate_IdOrderByLanguageCodeAsc(1L)).thenReturn(List.of(
                NotificationTemplateContent.builder()
                        .id(10L)
                        .languageCode("tr")
                        .subject("Konu")
                        .body("Gövde")
                        .build()
        ));

        NotificationTemplateDetailDto detail = adminService.getTemplateDetail(1L);
        assertThat(detail.getTemplateCode()).isEqualTo("RECEIPT_APPROVED_MAIL");
        assertThat(detail.getContents()).hasSize(1);
        assertThat(detail.getContents().get(0).getLanguageCode()).isEqualTo("tr");
    }

    @Test
    void createTemplate_throwsConflict_whenCodeExists() {
        when(templateRepository.existsByTemplateCode("RECEIPT_APPROVED_MAIL")).thenReturn(true);
        CreateTemplateRequest request = CreateTemplateRequest.builder()
                .templateCode("RECEIPT_APPROVED_MAIL")
                .channel(NotificationChannel.EMAIL)
                .build();

        assertThatThrownBy(() -> adminService.createTemplate(request))
                .isInstanceOf(NotificationTemplateConflictException.class);
    }

    @Test
    void createTemplate_success() {
        when(templateRepository.existsByTemplateCode("NEW_TEMPLATE")).thenReturn(false);
        CreateTemplateRequest request = CreateTemplateRequest.builder()
                .templateCode("NEW_TEMPLATE")
                .channel(NotificationChannel.SMS)
                .description("Açıklama")
                .active(true)
                .build();

        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(i -> {
            NotificationTemplate t = i.getArgument(0);
            t.setId(2L);
            return t;
        });

        NotificationTemplateDto result = adminService.createTemplate(request);
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getTemplateCode()).isEqualTo("NEW_TEMPLATE");
        assertThat(result.getChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void upsertContent_throwsException_whenEmailHasNoSubject() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(languageRepository.findByCode("tr")).thenReturn(Optional.of(langTr));

        UpsertTemplateContentRequest request = UpsertTemplateContentRequest.builder()
                .subject("") // empty subject for EMAIL
                .body("Gövde")
                .build();

        assertThatThrownBy(() -> adminService.upsertContent(1L, "tr", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("konu (subject) zorunludur");
    }

    @Test
    void upsertContent_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(languageRepository.findByCode("tr")).thenReturn(Optional.of(langTr));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr")).thenReturn(Optional.empty());

        UpsertTemplateContentRequest request = UpsertTemplateContentRequest.builder()
                .subject("Onaylandı")
                .body("Kabul edildi.")
                .build();

        when(contentRepository.save(any(NotificationTemplateContent.class))).thenAnswer(i -> i.getArgument(0));

        NotificationTemplateContentDto result = adminService.upsertContent(1L, "tr", request);
        assertThat(result.getSubject()).isEqualTo("Onaylandı");
        assertThat(result.getBody()).isEqualTo("Kabul edildi.");

        verify(missingTemplateLogRepository).resolveByLocaleAndTemplateCode(eq("tr"), eq("RECEIPT_APPROVED_MAIL"), any());
    }

    @Test
    void exportAsCsv_success() {
        when(languageRepository.existsByCodeAndActive("tr")).thenReturn(true);
        when(templateRepository.findAllByOrderByTemplateCodeAsc()).thenReturn(List.of(template));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr")).thenReturn(Optional.of(
                NotificationTemplateContent.builder()
                        .subject("Konu")
                        .body("Gövde")
                        .build()
        ));

        byte[] csvBytes = adminService.exportAsCsv("tr");
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv)
                .contains("Template Code,Channel,Subject,Body,Description,Active")
                .contains("RECEIPT_APPROVED_MAIL,EMAIL,Konu,Gövde,Mal kabul onay maili,TRUE");
    }

    @Test
    void importTemplates_csv_success() throws IOException {
        String csvContent = "Template Code,Channel,Subject,Body,Description,Active\n"
                + "SHIPMENT_DISPATCHED_MAIL,EMAIL,Yola çıktı,Gövde,Sevkiyat maili,TRUE\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "templates.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(languageRepository.findByCode("tr")).thenReturn(Optional.of(langTr));
        when(templateRepository.findByTemplateCode("SHIPMENT_DISPATCHED_MAIL")).thenReturn(Optional.empty());
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(i -> {
            NotificationTemplate t = i.getArgument(0);
            t.setId(10L);
            return t;
        });
        when(contentRepository.findByTemplate_IdAndLanguageCode(10L, "tr")).thenReturn(Optional.empty());

        ImportResultDto result = adminService.importTemplates("tr", file);

        assertThat(result.getInsertedCount()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
        verify(contentRepository).save(any(NotificationTemplateContent.class));
    }
}
