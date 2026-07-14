package com.wms.localization.service.notification;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.dto.notification.RenderedTemplateDto;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.NotificationChannel;
import com.wms.localization.entity.NotificationTemplate;
import com.wms.localization.entity.NotificationTemplateContent;
import com.wms.localization.event.MissingTemplateEvent;
import com.wms.localization.exception.notification.NotificationTemplateNotFoundException;
import com.wms.localization.exception.notification.PlaceholderResolutionException;
import com.wms.localization.exception.notification.TemplateContentNotFoundException;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.NotificationTemplateContentRepository;
import com.wms.localization.repository.NotificationTemplateRepository;
import com.wms.localization.service.FormatConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateRenderServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationTemplateContentRepository contentRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private FormatConfigService formatConfigService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TemplatePlaceholderResolver placeholderResolver;
    private NotificationTemplateRenderService renderService;

    private NotificationTemplate template;
    private ActiveFormatResponse formatTr;

    @BeforeEach
    void setUp() {
        placeholderResolver = new TemplatePlaceholderResolver();
        renderService = new NotificationTemplateRenderService(
                templateRepository,
                contentRepository,
                languageRepository,
                formatConfigService,
                placeholderResolver,
                eventPublisher
        );
        ReflectionTestUtils.setField(renderService, "defaultStrategy", UnresolvedPlaceholderStrategy.BLANK);

        template = NotificationTemplate.builder()
                .id(1L)
                .templateCode("TEST_TEMPLATE")
                .channel(NotificationChannel.EMAIL)
                .active(true)
                .build();

        formatTr = ActiveFormatResponse.of("dd.MM.yyyy", "HH:mm", ",", ".");
    }

    @Test
    void render_success() {
        NotificationTemplateContent content = NotificationTemplateContent.builder()
                .template(template)
                .languageCode("tr")
                .subject("Merhaba {{name}}")
                .body("Sayı: {{amount}}, Tarih: {{date}}")
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("TEST_TEMPLATE"))
                .thenReturn(Optional.of(template));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr"))
                .thenReturn(Optional.of(content));
        when(formatConfigService.resolveActiveFormat(any())).thenReturn(formatTr);

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Murat");
        variables.put("amount", new BigDecimal("12345.67"));
        variables.put("date", LocalDateTime.of(2026, 7, 13, 14, 30));

        RenderedTemplateDto result = renderService.render("TEST_TEMPLATE", "tr", variables);

        assertThat(result.subject()).isEqualTo("Merhaba Murat");
        assertThat(result.body()).isEqualTo("Sayı: 12.345,67, Tarih: 13.07.2026 14:30");
        assertThat(result.languageCode()).isEqualTo("tr");
        assertThat(result.fallbackApplied()).isFalse();
        assertThat(result.unresolvedPlaceholders()).isEmpty();
    }

    @Test
    void render_fallbackToDefaultLanguage() {
        NotificationTemplateContent defaultContent = NotificationTemplateContent.builder()
                .template(template)
                .languageCode("tr")
                .subject("Hello {{name}} (TR)")
                .body("Body (TR)")
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("TEST_TEMPLATE"))
                .thenReturn(Optional.of(template));
        // Requested 'en' is empty
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "en"))
                .thenReturn(Optional.empty());
        // Default language is 'tr'
        Language defaultLang = Language.builder().code("tr").isDefault(true).isActive(true).build();
        when(languageRepository.findFirstByIsDefaultTrueAndIsActiveTrue())
                .thenReturn(Optional.of(defaultLang));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr"))
                .thenReturn(Optional.of(defaultContent));
        when(formatConfigService.resolveActiveFormat(any())).thenReturn(formatTr);

        Map<String, Object> variables = Map.of("name", "John");
        RenderedTemplateDto result = renderService.render("TEST_TEMPLATE", "en", variables);

        assertThat(result.subject()).isEqualTo("Hello John (TR)");
        assertThat(result.body()).isEqualTo("Body (TR)");
        assertThat(result.languageCode()).isEqualTo("tr");
        assertThat(result.fallbackApplied()).isTrue();

        // Check if MissingTemplateEvent was published
        ArgumentCaptor<MissingTemplateEvent> captor = ArgumentCaptor.forClass(MissingTemplateEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        MissingTemplateEvent event = captor.getValue();
        assertThat(event.getLocale()).isEqualTo("en");
        assertThat(event.getTemplateCode()).isEqualTo("TEST_TEMPLATE");
        assertThat(event.getChannel()).isEqualTo("EMAIL");
    }

    @Test
    void render_templateNotFound() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> renderService.render("UNKNOWN", "tr", Map.of()))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void render_contentNotFoundBothRequestedAndDefault() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("TEST_TEMPLATE"))
                .thenReturn(Optional.of(template));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "en"))
                .thenReturn(Optional.empty());

        Language defaultLang = Language.builder().code("tr").isDefault(true).isActive(true).build();
        when(languageRepository.findFirstByIsDefaultTrueAndIsActiveTrue())
                .thenReturn(Optional.of(defaultLang));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> renderService.render("TEST_TEMPLATE", "en", Map.of()))
                .isInstanceOf(TemplateContentNotFoundException.class);
    }

    @Test
    void render_strategyBlank() {
        NotificationTemplateContent content = NotificationTemplateContent.builder()
                .template(template)
                .languageCode("tr")
                .subject("Hi {{name}}")
                .body("Values: {{val1}} and {{val2}}")
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("TEST_TEMPLATE"))
                .thenReturn(Optional.of(template));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr"))
                .thenReturn(Optional.of(content));
        when(formatConfigService.resolveActiveFormat(any())).thenReturn(formatTr);

        // strategy override to BLANK
        RenderedTemplateDto result = renderService.render(
                "TEST_TEMPLATE", "tr", Map.of("name", "Alice"), UnresolvedPlaceholderStrategy.BLANK);

        assertThat(result.subject()).isEqualTo("Hi Alice");
        assertThat(result.body()).isEqualTo("Values:  and ");
        assertThat(result.unresolvedPlaceholders()).containsExactlyInAnyOrder("val1", "val2");
    }

    @Test
    void render_strategyKeep() {
        NotificationTemplateContent content = NotificationTemplateContent.builder()
                .template(template)
                .languageCode("tr")
                .subject("Hi {{name}}")
                .body("Values: {{val1}} and {{val2}}")
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("TEST_TEMPLATE"))
                .thenReturn(Optional.of(template));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr"))
                .thenReturn(Optional.of(content));
        when(formatConfigService.resolveActiveFormat(any())).thenReturn(formatTr);

        // strategy override to KEEP
        RenderedTemplateDto result = renderService.render(
                "TEST_TEMPLATE", "tr", Map.of("name", "Alice"), UnresolvedPlaceholderStrategy.KEEP);

        assertThat(result.subject()).isEqualTo("Hi Alice");
        assertThat(result.body()).isEqualTo("Values: {{val1}} and {{val2}}");
        assertThat(result.unresolvedPlaceholders()).containsExactlyInAnyOrder("val1", "val2");
    }

    @Test
    void render_strategyFail() {
        NotificationTemplateContent content = NotificationTemplateContent.builder()
                .template(template)
                .languageCode("tr")
                .subject("Hi {{name}}")
                .body("Values: {{val1}} and {{val2}}")
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("TEST_TEMPLATE"))
                .thenReturn(Optional.of(template));
        when(contentRepository.findByTemplate_IdAndLanguageCode(1L, "tr"))
                .thenReturn(Optional.of(content));
        when(formatConfigService.resolveActiveFormat(any())).thenReturn(formatTr);

        assertThatThrownBy(() -> renderService.render(
                "TEST_TEMPLATE", "tr", Map.of("name", "Alice"), UnresolvedPlaceholderStrategy.FAIL))
                .isInstanceOf(PlaceholderResolutionException.class);
    }
}
