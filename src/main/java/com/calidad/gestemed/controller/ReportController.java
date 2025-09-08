package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.repo.AssetRepo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Email;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
@Validated
public class ReportController {

    private final AssetRepo assetRepo;
    private final JavaMailSender mailSender;

    // Map para almacenar envíos programados: email -> configuración
    private final Map<String, ScheduledReport> scheduledReports = new HashMap<>();

    /*** Página de reportes ***/
    @GetMapping
    public String index(Model model) {
        return "reports/index";
    }

    /*** Exportar Excel de activos ***/
    @GetMapping(value="/assets.xlsx", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> assetsExcel() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Activos");
            int r = 0;

            // Header
            Row h = s.createRow(r++);
            int c = 0;
            h.createCell(c++).setCellValue("ID Activo");
            h.createCell(c++).setCellValue("Modelo");
            h.createCell(c++).setCellValue("Serial");
            h.createCell(c++).setCellValue("Fabricante");
            h.createCell(c++).setCellValue("Fecha Compra");
            h.createCell(c++).setCellValue("Ubicación");
            h.createCell(c++).setCellValue("Valor");

            // Datos
            for (Asset a : assetRepo.findAll()) {
                Row row = s.createRow(r++);
                int j = 0;
                row.createCell(j++).setCellValue(nvl(a.getAssetId()));
                row.createCell(j++).setCellValue(nvl(a.getModel()));
                row.createCell(j++).setCellValue(nvl(a.getSerialNumber()));
                row.createCell(j++).setCellValue(nvl(a.getManufacturer()));
                row.createCell(j++).setCellValue(a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : "");
                row.createCell(j++).setCellValue(nvl(a.getInitialLocation()));
                row.createCell(j++).setCellValue(a.getValue() != null ? a.getValue().toString() : "");
            }

            for (int i = 0; i < 7; i++) s.autoSizeColumn(i);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activos.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bos.toByteArray());
        }
    }

    /*** Exportar PDF de activos ***/
    @GetMapping(value="/assets.pdf", produces="application/pdf")
    public ResponseEntity<byte[]> assetsPdf() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, bos);
        doc.open();
        doc.add(new Paragraph("Inventario de Activos"));
        PdfPTable t = new PdfPTable(2);
        t.addCell("ID Activo");
        t.addCell("Modelo");
        for (Asset a : assetRepo.findAll()) {
            t.addCell(nvl(a.getAssetId()));
            t.addCell(nvl(a.getModel()));
        }
        doc.add(t);
        doc.close();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bos.toByteArray());
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

    /*** Lógica de envío de correo ***/
    private void sendReportEmail(ScheduledReport config) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(config.getEmail());
            msg.setSubject("Reporte de activos");
            StringBuilder body = new StringBuilder("Se adjuntan los reportes solicitados.\nFormatos: ");
            if (config.isSendExcel()) body.append("Excel ");
            if (config.isSendPdf()) body.append("PDF");
            msg.setText(body.toString());
            mailSender.send(msg);
            System.out.println("[INFO] Reporte enviado a " + config.getEmail());
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo enviar reporte a " + config.getEmail());
        }
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
        public void setLastSent(LocalDateTime dt) { this.lastSent = dt; }
    }

    /*** Helper ***/
    private String nvl(String s) { return s == null ? "" : s; }
}
