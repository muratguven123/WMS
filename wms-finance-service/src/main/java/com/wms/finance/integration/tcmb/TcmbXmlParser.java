package com.wms.finance.integration.tcmb;

import com.wms.finance.entity.enums.RateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TCMB today.xml yanıtını parse ederek {@link TcmbParsedRate} listesine dönüştürür.
 *
 * <p>TCMB XML yapısı:
 * <pre>
 * &lt;Tarih_Date Date="04/07/2026"&gt;
 *   &lt;Currency CurrencyCode="USD"&gt;
 *     &lt;ForexBuying&gt;38.5000&lt;/ForexBuying&gt;
 *     &lt;ForexSelling&gt;38.7000&lt;/ForexSelling&gt;
 *     &lt;BanknoteBuying&gt;38.4500&lt;/BanknoteBuying&gt;
 *     &lt;BanknoteSelling&gt;38.7500&lt;/BanknoteSelling&gt;
 *   &lt;/Currency&gt;
 * &lt;/Tarih_Date&gt;
 * </pre>
 *
 * <p>Mapping:
 * <ul>
 *   <li>ForexBuying     → {@link RateType#BUYING}</li>
 *   <li>ForexSelling    → {@link RateType#SELLING}</li>
 *   <li>BanknoteBuying  → {@link RateType#EFFECTIVE_BUYING}</li>
 *   <li>BanknoteSelling → {@link RateType#EFFECTIVE_SELLING}</li>
 * </ul>
 */
@Slf4j
@Component
public class TcmbXmlParser {

    private static final DateTimeFormatter TCMB_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Takip edilecek döviz kodları — UI ve {@link TcmbCurrencyDefaults} ile hizalı. */
    private static final Set<String> TRACKED_CURRENCIES = TcmbCurrencyDefaults.trackedCodes();

    /**
     * TCMB XML metnini parse eder.
     *
     * @param xml TCMB'den alınan ham XML metni
     * @return parse edilen kur kayıtları listesi
     * @throws TcmbParseException XML ayrıştırılamadığında
     */
    public List<TcmbParsedRate> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE saldırılarına karşı güvenli yapılandırma
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            XPath xpath = XPathFactory.newInstance().newXPath();

            // Kur tarihini oku
            String dateStr = (String) xpath.evaluate(
                    "/Tarih_Date/@Date", document, XPathConstants.STRING);
            LocalDate rateDate = LocalDate.parse(dateStr, TCMB_DATE_FORMAT);

            // Tüm Currency node'larını al
            NodeList currencyNodes = (NodeList) xpath.evaluate(
                    "/Tarih_Date/Currency", document, XPathConstants.NODESET);

            List<TcmbParsedRate> rates = new ArrayList<>();

            for (int i = 0; i < currencyNodes.getLength(); i++) {
                Node currencyNode = currencyNodes.item(i);
                String code = currencyNode.getAttributes()
                        .getNamedItem("CurrencyCode")
                        .getNodeValue();

                if (!TRACKED_CURRENCIES.contains(code)) {
                    continue;
                }

                extractRate(currencyNode, xpath, code, rateDate, "ForexBuying",
                        RateType.BUYING, rates);
                extractRate(currencyNode, xpath, code, rateDate, "ForexSelling",
                        RateType.SELLING, rates);
                extractRate(currencyNode, xpath, code, rateDate, "BanknoteBuying",
                        RateType.EFFECTIVE_BUYING, rates);
                extractRate(currencyNode, xpath, code, rateDate, "BanknoteSelling",
                        RateType.EFFECTIVE_SELLING, rates);
            }

            log.info("TCMB XML parse tamamlandı. Tarih: {}, Kur adedi: {}", rateDate, rates.size());
            return rates;

        } catch (Exception ex) {
            throw new TcmbParseException("TCMB XML parse hatası: " + ex.getMessage(), ex);
        }
    }

    private void extractRate(Node currencyNode, XPath xpath,
                             String code, LocalDate rateDate,
                             String elementName, RateType rateType,
                             List<TcmbParsedRate> target) {
        try {
            String valueStr = xpath.evaluate(elementName, currencyNode).trim();
            if (valueStr.isBlank()) {
                log.debug("TCMB: {} için {} alanı boş, atlanıyor.", code, elementName);
                return;
            }
            BigDecimal value = new BigDecimal(valueStr);
            target.add(new TcmbParsedRate(code, rateDate, rateType, value));
        } catch (Exception ex) {
            log.warn("TCMB: {} için {} parse edilemedi: {}", code, elementName, ex.getMessage());
        }
    }
}
