package com.wms.integration.adapter.impl;

import com.jcraft.jsch.*;
import com.wms.integration.adapter.ErpAdapter;
import com.wms.integration.adapter.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Logo ERP adaptörü — SFTP dosya bazlı entegrasyon.
 *
 * <p>Logo Tiger/GO ürünleri çoğunlukla CSV/XML dosya aktarımı ile çalışır.
 * Bu adaptör verileri belirli formatlara dönüştürüp JSch kütüphanesi ile
 * SFTP sunucusuna yükler; Logo tarafındaki Import Job dosyayı işler.
 *
 * <p><b>Mock notu:</b> SFTP bağlantı parametreleri application.yml'den
 * enjekte edilir. Test ortamında {@code erp.logo.sftp.mock=true} ile
 * gerçek bağlantı kurulmaksızın akış simüle edilebilir.
 */
@Slf4j
@Component("logoAdapter")
public class LogoAdapter implements ErpAdapter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${erp.logo.sftp.host:sftp.logo-mock-host}")
    private String sftpHost;

    @Value("${erp.logo.sftp.port:22}")
    private int sftpPort;

    @Value("${erp.logo.sftp.user:wms}")
    private String sftpUser;

    @Value("${erp.logo.sftp.password:}")
    private String sftpPassword;

    @Value("${erp.logo.sftp.remote-dir:/import}")
    private String remoteDir;

    /** true ise SFTP'ye gerçek bağlantı kurulmaz, akış simüle edilir. */
    @Value("${erp.logo.sftp.mock:true}")
    private boolean mockMode;

    // -----------------------------------------------------------------------
    // ErpAdapter implementation
    // -----------------------------------------------------------------------

    @Override
    public ErpResponse sendMaterialCard(MaterialDto material) {
        String fileName = "MATERIAL_" + material.getSku() + "_"
                + LocalDate.now().format(DATE_FMT) + ".csv";
        String csv = buildMaterialCsv(material);

        log.info("[Logo] sendMaterialCard -> sku={}, file={}", material.getSku(), fileName);
        return uploadViaSftp(fileName, csv.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ErpResponse sendInventoryMovement(MovementDto movement) {
        String fileName = "MOVEMENT_" + movement.getMovementId() + "_"
                + LocalDate.now().format(DATE_FMT) + ".xml";
        String xml = buildMovementXml(movement);

        log.info("[Logo] sendInventoryMovement -> movementId={}, file={}", movement.getMovementId(), fileName);
        return uploadViaSftp(fileName, xml.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ErpResponse sendInvoice(InvoiceDto invoice) {
        String fileName = "INVOICE_" + invoice.getInvoiceNumber() + "_"
                + LocalDate.now().format(DATE_FMT) + ".xml";
        String xml = buildInvoiceXml(invoice);

        log.info("[Logo] sendInvoice -> invoiceNumber={}, file={}", invoice.getInvoiceNumber(), fileName);
        return uploadViaSftp(fileName, xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Logo ERP'de genellikle kur çekme için ayrı bir SFTP dosyası değil,
     * REST veya DB bağlantısı kullanılır. Bu örnek sabit veri döner;
     * gerçek projede Logo REST API entegrasyonuyla değiştirilmeli.
     */
    @Override
    public List<ExchangeRateDto> fetchExchangeRates() {
        log.info("[Logo] fetchExchangeRates -> mock data returned");
        return List.of(
                ExchangeRateDto.builder()
                        .fromCurrency("USD").toCurrency("TRY")
                        .rate(new BigDecimal("32.50")).rateDate(LocalDate.now())
                        .build(),
                ExchangeRateDto.builder()
                        .fromCurrency("EUR").toCurrency("TRY")
                        .rate(new BigDecimal("35.10")).rateDate(LocalDate.now())
                        .build()
        );
    }

    // -----------------------------------------------------------------------
    // SFTP transfer
    // -----------------------------------------------------------------------

    /**
     * Verilen byte dizisini SFTP üzerinden uzak dizine yükler.
     *
     * @param fileName uzak dosya adı
     * @param data     yüklenecek içerik
     * @return adaptör yanıtı
     */
    private ErpResponse uploadViaSftp(String fileName, byte[] data) {
        String remoteRef = remoteDir + "/" + fileName;

        if (mockMode) {
            log.info("[Logo][MOCK] SFTP upload simulated -> {}", remoteRef);
            return ErpResponse.success("MOCK-" + 1L,
                    "Logo SFTP upload simulated (mock mode): " + remoteRef);
        }

        JSch jsch = new JSch();
        com.jcraft.jsch.Session session = null;
        ChannelSftp channel = null;

        try {
            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            session.setPassword(sftpPassword);

            // Strict host key checking'i kapatıyoruz (prod'da known_hosts kullan)
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10_000);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(5_000);

            try (InputStream is = new ByteArrayInputStream(data)) {
                channel.put(is, remoteDir + "/" + fileName, ChannelSftp.OVERWRITE);
            }

            log.info("[Logo] SFTP upload success -> {}", remoteRef);
            return ErpResponse.success(remoteRef, "Logo SFTP upload success");

        } catch (JSchException | SftpException | java.io.IOException ex) {
            log.error("[Logo] SFTP upload failed -> file={}", fileName, ex);
            return ErpResponse.failure("LOGO_SFTP_ERR",
                    "SFTP upload failed for " + fileName + ": " + ex.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    // -----------------------------------------------------------------------
    // Private — format builders
    // -----------------------------------------------------------------------

    private String buildMaterialCsv(MaterialDto dto) {
        // Header + 1 satır (Logo import formatı — genişletilmeli)
        return "SKU;Name;Unit;Weight;Barcode\n"
                + dto.getSku() + ";"
                + dto.getName() + ";"
                + dto.getUnit() + ";"
                + dto.getUnitWeight() + ";"
                + (dto.getBarcode() != null ? dto.getBarcode() : "") + "\n";
    }

    private String buildMovementXml(MovementDto dto) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <InventoryMovement>
                    <MovementId>%d</MovementId>
                    <MovementType>%s</MovementType>
                    <SKU>%s</SKU>
                    <Quantity>%s</Quantity>
                    <Unit>%s</Unit>
                    <MovementDate>%s</MovementDate>
                    <ReferenceDocument>%s</ReferenceDocument>
                </InventoryMovement>
                """.formatted(
                dto.getMovementId(),
                dto.getMovementType(),
                dto.getSku(),
                dto.getQuantity(),
                dto.getUnit(),
                dto.getMovementDate(),
                dto.getReferenceDocumentNo() != null ? dto.getReferenceDocumentNo() : ""
        );
    }

    private String buildInvoiceXml(InvoiceDto dto) {
        StringBuilder lines = new StringBuilder();
        if (dto.getLines() != null) {
            for (InvoiceLineDto line : dto.getLines()) {
                lines.append("""
                            <Line>
                                <LineNo>%d</LineNo>
                                <SKU>%s</SKU>
                                <Description>%s</Description>
                                <Quantity>%s</Quantity>
                                <Unit>%s</Unit>
                                <UnitPrice>%s</UnitPrice>
                                <LineTotalForeign>%s</LineTotalForeign>
                                <LineTotalLocal>%s</LineTotalLocal>
                            </Line>
                        """.formatted(
                        line.getLineNumber(), line.getSku(), line.getDescription(),
                        line.getQuantity(), line.getUnit(),
                        line.getUnitPriceForeign(), line.getLineTotalForeign(),
                        line.getLineTotalLocal()
                ));
            }
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice>
                    <InvoiceId>%d</InvoiceId>
                    <InvoiceNumber>%s</InvoiceNumber>
                    <InvoiceDate>%s</InvoiceDate>
                    <PartnerCode>%s</PartnerCode>
                    <Currency>%s</Currency>
                    <TotalForeign>%s</TotalForeign>
                    <TotalLocal>%s</TotalLocal>
                    <ExchangeRate>%s</ExchangeRate>
                    <Lines>
                %s
                    </Lines>
                </Invoice>
                """.formatted(
                dto.getInvoiceId(),
                dto.getInvoiceNumber(),
                dto.getInvoiceDate(),
                dto.getPartnerCode(),
                dto.getCurrencyCode(),
                dto.getTotalAmountForeign(),
                dto.getTotalAmountLocal(),
                dto.getLockedExchangeRate(),
                lines
        );
    }
}
