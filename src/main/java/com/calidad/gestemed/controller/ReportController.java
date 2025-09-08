package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.repo.AssetRepo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.internet.MimeMessage;
import jakarta.validation.constraints.Email;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
@Validated
public class ReportController {

    private final AssetRepo assetRepo;
    private final JavaMailSender mailSender;

    // Map para almacenar envíos programados: email -> configuración
    private final Map<String, ScheduledReport> scheduledReports = new HashMap<>();

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
            Pattern.CASE_INSENSITIVE
    );

    /*** Página de reportes ***/
    @GetMapping
    public String index(Model model) { return "reports/index"; }

    /*** Exportar Excel de activos ***/
    @GetMapping(value="/assets.xlsx", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> assetsExcel() throws Exception {
        byte[] data = generateExcelReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activos.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    /*** Exportar PDF de activos ***/
    @GetMapping(value="/assets.pdf", produces="application/pdf")
    public ResponseEntity<byte[]> assetsPdf() throws Exception {
        byte[] data = generatePdfReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    /*** PDF de resumen ***/
    @GetMapping(value="/summary.pdf", produces="application/pdf")
    public ResponseEntity<byte[]> summaryPdf() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, bos);
        doc.open();
        Font font = new Font(Font.HELVETICA, 12);
        doc.add(new Paragraph("Resumen Operativo", font));
        doc.add(new Paragraph("Fecha: " + LocalDate.now(), font));
        doc.add(new Paragraph("Total activos: " + assetRepo.count(), font));
        doc.close();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resumen.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bos.toByteArray());
    }

    /*** Formulario de envíos automáticos ***/
    @PostMapping("/auto")
    public String scheduleReport(
            @RequestParam @Email String email,
            @RequestParam(required=false) boolean sendExcel,
            @RequestParam(required=false) boolean sendPdf,
            @RequestParam int intervalDays,
            Model model
    ) {
        if (!isValidEmail(email)) {
            model.addAttribute("error", "Correo inválido.");
            return "reports/index";
        }
        if (!sendExcel && !sendPdf) {
            model.addAttribute("error", "Debe seleccionar al menos un formato (Excel o PDF).");
            return "reports/index";
        }
        if (intervalDays != 15 && intervalDays != 30) {
            model.addAttribute("error", "Intervalo inválido.");
            return "reports/index";
        }

        ScheduledReport config = new ScheduledReport(email, sendExcel, sendPdf, intervalDays, LocalDateTime.now());
        scheduledReports.put(email, config);

        // Envío inmediato
        sendReportEmail(config);

        model.addAttribute("success", "Reporte programado correctamente.");
        return "reports/index";
    }

    /*** Job diario que revisa envíos programados ***/
    @Scheduled(cron = "0 0 8 * * *") // todos los días a las 8:00am
    public void sendScheduledReports() {
        LocalDateTime now = LocalDateTime.now();
        for (ScheduledReport config : scheduledReports.values()) {
            if (config.getLastSent().plusDays(config.getIntervalDays()).isBefore(now)) {
                sendReportEmail(config);
                config.setLastSent(now);
            }
        }
    }

    /*** Lógica de envío de correo con adjuntos ***/
    private void sendReportEmail(ScheduledReport config) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(config.getEmail());
            helper.setSubject("Reporte de Activos");
            helper.setText("Adjunto se encuentran los reportes solicitados.");

            // Adjuntar archivos según la configuración
            if (config.isSendExcel()) helper.addAttachment("activos.xlsx", new javax.mail.util.ByteArrayDataSource(generateExcelReport(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            if (config.isSendPdf()) helper.addAttachment("activos.pdf", new javax.mail.util.ByteArrayDataSource(generatePdfReport(), "application/pdf"));

            mailSender.send(message);
            System.out.println("[INFO] Reporte enviado a " + config.getEmail());
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo enviar reporte a " + config.getEmail() + ": " + e.getMessage());
        }
    }

    /*** Generación de Excel ***/
    public byte[] generateExcelReport() throws IOException {
        var assets = assetRepo.findAll();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Activos");
            Row header = sheet.createRow(0);
            String[] columns = {"ID Activo","Modelo","Serial","Fabricante","Fecha Compra","Ubicación","Valor"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);

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

    /*** Generación de PDF ***/
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

    /*** Validación de correo ***/
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_REGEX.matcher(email).find();
    }

    /*** Clase interna para envíos programados ***/
    @RequiredArgsConstructor
    private static class ScheduledReport {
        private final String email;
        private final boolean sendExcel;
        private final boolean sendPdf;
        private final int intervalDays;
        private LocalDateTime lastSent;

        public boolean isSendExcel() { return sendExcel; }
        public boolean isSendPdf() { return sendPdf; }
        public int getIntervalDays() { return intervalDays; }
        public String getEmail() { return email; }
        public LocalDateTime getLastSent() { return lastSent; }
        public void setLastSent(LocalDate
