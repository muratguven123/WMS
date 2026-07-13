package com.wms.localization.service.translation;

/**
 * Pluggable makine çevirisi sağlayıcısı (MyMemory, LibreTranslate, DeepL, …).
 */
public interface TranslationProvider {

    String name();

    /** Başarısız veya kaynakla aynıysa null döner. */
    String translate(String text, String sourceLang, String targetLang);
}
