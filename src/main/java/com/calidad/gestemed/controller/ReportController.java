package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.repo.AssetRepo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final AssetRepo assetRepo;

    // Página de reportes
    @GetMapping
    public String reportPage() {
        return "reports/reports"; // Vista con botones de Excel, PDF y envío automático
    }

    // Exportar Excel
    @GetMapping("/assets.xlsx")
    public void exportExcel(HttpServletResponse response) throws IOException {
        List<Asset> assets = assetRepo.findAll();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Activos");

        Row header = sheet.createRow(0);
        String[] columns = {"ID Activo", "Modelo", "Serial", "Fabricante", "Fecha Compra", "Ubicación", "Valor"};
        for(int i=0; i<columns.length; i++){
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
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

    // Endpoint para enviar reportes por correo (programación futura)
    @PostMapping("/send")
    public String sendReports(@RequestParam String email,
                              @RequestParam(required=false) boolean sendExcel,
                              @RequestParam(required=false) boolean sendPdf,
                              Model model) {
        // Aquí se implementará la lógica de envío por correo
        // Se puede reutilizar GeoAlertService o AlertService con JavaMailSender
        model.addAttribute("msg", "Reporte enviado a " + email);
        return "reports/reports";
    }
}
