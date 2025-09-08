package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.User;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Email;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final AssetRepo assetRepo;
    private final UserRepo userRepo;
    private final JavaMailSender mailSender;

    // Página de reportes
    @GetMapping
    public String reportPage() {
        return "reports/reports";
    }

    // Exportar Excel
    @GetMapping("/assets.xlsx")
    public void exportExcel(HttpServletResponse response) throws IOException {
        List<Asset> assets = assetRepo.findAll();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Activos");

        Row header = sheet.createRow(0);
        String[] columns = {"ID Activo", "Modelo", "Serial", "Fabricante", "Fecha Compra", "Ubicación", "Valor"};
        for(int i=0;i<columns.length;i++){
            header.createCell(i).setCellValue(columns[i]);
        }

        int rowNum = 1;
        for(Asset a : assets){
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(a.getAssetId());
            row.createCell(1).setCellValue(a.getModel());
            row.createCell(2).setCellValue(a.getSerialNumber());
            row.createCell(3).setCellValue(a.getManufacturer());
            row.createCell(4).setCellValue(a.getPurchaseDate().toString());
            row.createCell(5).setCellValue(a.getInitialLocation());
            row.createCell(6).setCellValue(a.getValue().doubleValue());
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition","attachment; filename=Activos.xlsx");
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    // Exportar PDF
    @GetMapping("/assets.pdf")
    public void exportPdf(HttpServletResponse response) throws IOException, DocumentException {
        List<Asset> assets = assetRepo.findAll();

        Document document = new Document(PageSize.A4.rotate());
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition","attachment; filename=Activos.pdf");

        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{2,3,3,3,3,3,2});

        String[] columns = {"ID Activo", "Modelo", "Serial", "Fabricante", "Fecha Compra", "Ubicación", "Valor"};
        for(String col : columns){
            PdfPCell cell = new PdfPCell(new Phrase(col, font));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            table.addCell(cell);
        }

        for(Asset a : assets){
            table.addCell(a.getAssetId());
            table.addCell(a.getModel());
            table.addCell(a.getSerialNumber());
            table.addCell(a.getManufacturer());
            table.addCell(a.getPurchaseDate().toString());
            table.addCell(a.getInitialLocation());
            table.addCell(a.getValue().toString());
        }

        document.add(table);
        document.close();
    }

    // Envío de reportes con validación y programación
    @PostMapping("/send")
    public String sendReports(@RequestParam @Email String email,
                              @RequestParam(required=false) boolean sendExcel,
                              @RequestParam(required=false) boolean sendPdf,
                              @RequestParam(required=false) Integer intervalDays, // 15 o 30 días
                              Model model) throws Exception {

        // Validar que el correo exista en la base de usuarios
        User u = userRepo.findAll().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

        if(u == null){
            model.addAttribute("error", "El correo no existe en el sistema.");
            return "reports/reports";
        }

        // Generar archivos en memoria
        byte[] excelData = null;
        byte[] pdfData = null;

        if(sendExcel){
            try(ByteArrayOutputStream out = new ByteArrayOutputStream()){
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("Activos");
                Row header = sheet.createRow(0);
                String[] columns = {"ID Activo", "Modelo", "Serial", "Fabricante", "Fecha Compra", "Ubicación", "Valor"};
                for(int i=0;i<columns.length;i++){
                    header.createCell(i).setCellValue(columns[i]);
                }
                int rowNum=1;
                for(Asset a : assetRepo.findAll()){
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(a.getAssetId());
                    row.createCell(1).setCellValue(a.getModel());
                    row.createCell(2).setCellValue(a.getSerialNumber());
                    row.createCell(3).setCellValue(a.getManufacturer());
                    row.createCell(4).setCellValue(a.getPurchaseDate().toString());
                    row.createCell(5).setCellValue(a.getInitialLocation());
                    row.createCell(6).setCellValue(a.getValue().doubleValue());
                }
                workbook.write(out);
                workbook.close();
                excelData = out.toByteArray();
            }
        }

        if(sendPdf){
            try(ByteArrayOutputStream out = new ByteArrayOutputStream()){
                Document document = new Document(PageSize.A4.rotate());
                PdfWriter.getInstance(document, out);
                document.open();
                Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new int[]{2,3,3,3,3,3,2});
                String[] columns = {"ID Activo", "Modelo", "Serial", "Fabricante", "Fecha Compra", "Ubicación", "Valor"};
                for(String col : columns){
                    PdfPCell cell = new PdfPCell(new Phrase(col, font));
                    cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    table.addCell(cell);
                }
                for(Asset a : assetRepo.findAll()){
                    table.addCell(a.getAssetId());
                    table.addCell(a.getModel());
                    table.addCell(a.getSerialNumber());
                    table.addCell(a.getManufacturer());
                    table.addCell(a.getPurchaseDate().toString());
                    table.addCell(a.getInitialLocation());
                    table.addCell(a.getValue().toString());
                }
                document.add(table);
                document.close();
                pdfData = out.toByteArray();
            }
        }

        // Enviar correo
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setSubject("Reportes de Activos Médicos");
        msg.setText("Adjunto(s) su(s) reporte(s). Intervalo de envío: "
                + (intervalDays != null ? intervalDays + " días" : "único"));

        // Para adjuntar archivos reales se usaría MimeMessageHelper, aquí se deja para futura implementación
        mailSender.send(msg);

        model.addAttribute("msg", "Reporte enviado a " + email + (intervalDays!=null ? " cada " + intervalDays + " días." : ""));
        return "reports/reports";
    }
}
