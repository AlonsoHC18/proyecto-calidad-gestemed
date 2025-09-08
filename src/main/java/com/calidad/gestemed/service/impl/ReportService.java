package com.calidad.gestemed.service.impl;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.repo.AssetRepo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AssetRepo assetRepo;
    private final JavaMailSender mailSender;

    // Lista de envíos programados: email -> configuración
    private final Map<String, ScheduledReport> scheduledReports = new HashMap<>();

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
            Pattern.CASE_INSENSITIVE
    );

    /** Programar envío automático */
    public String scheduleEmailReport(String email, boolean sendExcel, boolean sendPdf, int intervalDays) {
        if (!isValidEmail(email)) {
            return "Correo inválido";
        }

        ScheduledReport config = new ScheduledReport(email, sendExcel, sendPdf, intervalDays, LocalDateTime.now());
        scheduledReports.put(email, config);

        // Enviar inmediatamente
        sendReportEmail(config);

        return "Reporte programado correctamente";
    }

    /** Validación simple de correo */
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_REGEX.matcher(email).find();
    }

    /** Enviar reporte por correo */
    private void sendReportEmail(ScheduledReport config) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(config.getEmail());
            helper.setSubject("Reporte de Activos");
            helper.setText("Adjunto se encuentran los reportes solicitados.");

            // Adjuntar archivos según la configuración
            if (config.isSendExcel()) {
                helper.addAttachment("activos.xlsx", new javax.mail.util.ByteArrayDataSource(generateExcelReport(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            }
            if (config.isSendPdf()) {
                helper.addAttachment("activos.pdf", new javax.mail.util.ByteArrayDataSource(generatePdfReport(), "application/pdf"));
            }

            mailSender.send(message);
            System.out.println("[INFO] Reporte enviado a " + config.getEmail());

        } catch (Exception e) {
            System.out.println("[WARN] No se pudo enviar reporte a " + config.getEmail() + ": " + e.getMessage());
        }
    }

    /** Método scheduled que se ejecuta cada día a las 8:00 am */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendScheduledReports() {
        LocalDateTime now = LocalDateTime.now();
        for (ScheduledReport config : scheduledReports.values()) {
            if (config.getLastSent().plusDays(config.getIntervalDays()).isBefore(now)) {
                sendReportEmail(config);
                config.setLastSent(now);
            }
        }
    }

    /** Generación de Excel */
    public byte[] generateExcelReport() throws IOException {
        var assets = assetRepo.findAll();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Activos");
            Row header = sheet.createRow(0);
            String[] columns = {"ID Activo","Modelo","Serial","Fabricante","Fecha Compra","Ubicación","Valor"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }

            int rowNum = 1;
            for (var a : assets) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(a.getAssetId());
                row.createCell(1).setCellValue(a.getModel() != null ? a.getModel() : "");
                row.createCell(2).setCellValue(a.getSerialNumber() != null ? a.getSerialNumber() : "");
                row.createCell(3).setCellValue(a.getManufacturer() != null ? a.getManufacturer() : "");
                row.createCell(4).setCellValue(a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : "");
                row.createCell(5).setCellValue(a.getInitialLocation() != null ? a.getInitialLocation() : "");
                row.createCell(6).setCellValue(a.getValue() != null ? a.getValue().doubleValue() : 0);
            }

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    /** Generación de PDF */
    public byte[] generatePdfReport() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            Document doc = new Document();
            PdfWriter.getInstance(doc, bos);
            doc.open();
            doc.add(new Paragraph("Inventario de Activos", new Font(Font.HELVETICA, 14, Font.BOLD)));
            PdfPTable table = new PdfPTable(7);
            table.addCell("ID Activo");
            table.addCell("Modelo");
            table.addCell("Serial");
            table.addCell("Fabricante");
            table.addCell("Fecha Compra");
            table.addCell("Ubicación");
            table.addCell("Valor");

            for (Asset a : assetRepo.findAll()) {
                table.addCell(String.valueOf(a.getAssetId()));
                table.addCell(a.getModel() != null ? a.getModel() : "");
                table.addCell(a.getSerialNumber() != null ? a.getSerialNumber() : "");
                table.addCell(a.getManufacturer() != null ? a.getManufacturer() : "");
                table.addCell(a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : "");
                table.addCell(a.getInitialLocation() != null ? a.getInitialLocation() : "");
                table.addCell(a.getValue() != null ? a.getValue().toString() : "");
            }

            doc.add(table);
            doc.close();
        } catch (DocumentException e) {
            throw new IOException("Error generando PDF", e);
        }
        return bos.toByteArray();
    }

    /** Clase interna para guardar configuración de envíos */
    @RequiredArgsConstructor
    private static class ScheduledReport {
        private final String email;
        private final boolean sendExcel;
        private final boolean sendPdf;
        private final int intervalDays;
        private LocalDateTime lastSent;

        public boolean isSendExcel() { return sendExcel; }
        public boolean isSendPdf() { return sendPdf; }
        public LocalDateTime getLastSent() { return lastSent; }
        public void setLastSent(LocalDateTime t) { lastSent = t; }
        public String getEmail() { return email; }
        public int getIntervalDays() { return intervalDays; }
    }
}
