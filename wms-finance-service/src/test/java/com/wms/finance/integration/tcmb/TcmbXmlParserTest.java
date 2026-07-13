package com.wms.finance.integration.tcmb;

import com.wms.finance.entity.enums.RateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TcmbXmlParser")
class TcmbXmlParserTest {

    private final TcmbXmlParser parser = new TcmbXmlParser();

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Tarih_Date Date="03/07/2026">
              <Currency CurrencyCode="USD">
                <ForexBuying>38.5000</ForexBuying>
                <ForexSelling>38.7000</ForexSelling>
                <BanknoteBuying>38.4500</BanknoteBuying>
                <BanknoteSelling>38.7500</BanknoteSelling>
              </Currency>
              <Currency CurrencyCode="EUR">
                <ForexBuying>44.1000</ForexBuying>
                <ForexSelling>44.3000</ForexSelling>
                <BanknoteBuying>44.0500</BanknoteBuying>
                <BanknoteSelling>44.3500</BanknoteSelling>
              </Currency>
            </Tarih_Date>
            """;

    @Test
    @DisplayName("USD ve EUR için dört kur tipini parse eder")
    void parse_extractsAllRateTypes() {
        List<TcmbParsedRate> rates = parser.parse(SAMPLE_XML);

        assertThat(rates).hasSize(8);
        assertThat(rates).allMatch(r -> r.rateDate().equals(LocalDate.of(2026, 7, 3)));

        assertThat(rates).anyMatch(r ->
                r.currencyCode().equals("USD")
                        && r.rateType() == RateType.SELLING
                        && r.rate().compareTo(new BigDecimal("38.7000")) == 0);

        assertThat(rates).anyMatch(r ->
                r.currencyCode().equals("EUR")
                        && r.rateType() == RateType.EFFECTIVE_BUYING
                        && r.rate().compareTo(new BigDecimal("44.0500")) == 0);
    }

    @Test
    @DisplayName("Takip edilmeyen para birimlerini atlar")
    void parse_skipsUntrackedCurrencies() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Tarih_Date Date="03/07/2026">
                  <Currency CurrencyCode="PLN">
                    <ForexBuying>9.5000</ForexBuying>
                    <ForexSelling>9.7000</ForexSelling>
                  </Currency>
                </Tarih_Date>
                """;

        assertThat(parser.parse(xml)).isEmpty();
    }
}
