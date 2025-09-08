package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.ImportLog;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.repo.ImportLogRepo;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/import")
public class ImportController {

    private final AssetRepo assetRepo;
    private final ImportLogRepo importLogRepo;

    @GetMapping
    public String form() {
        return "import/form";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            if (file.isEmpty()) {
                model.addAttribute("error", "Selecciona un archivo CSV o XLSX.");
                return "import/form";
            }

            String name = (file.getOriginalFilename() == null ? "" : file.getOriginalFilename()).toLowerCase();

            int insertedCount = 0, duplicateCount = 0, errorCount = 0;
            List<String> details = new ArrayList<>();
            List<Asset> imported = new ArrayList<>();

            if (name.endsWith(".csv")) {
                String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "");
                char sep = detectSeparator(content);

                try (CSVReader r = new CSVReader(new java.io.StringReader(content))) {
                    String[] row;
                    boolean header = true;
                    int line = 0;

                    while ((row = r.readNext()) != null) {
                        line++;
                        if (header) { header = false; continue; }
                        if (row.length == 0 || (row.length == 1 && (row[0] == null || row[0].isBlank()))) continue;
                        if (row.length < 7) {
                            model.addAttribute("error", "Formato CSV inválido en línea " + line);
                            return "import/form";
                        }

                        for (int i = 0; i < row.length; i++) row[i] = (row[i] == null ? "" : row[i].trim());

                        Asset a = mapRow(row[0], row[1], row[2], row[3], row[4], row[5], row[6]);
                        try {
                            if (!assetRepo.existsByAssetId(a.getAssetId())) {
                                imported.add(assetRepo.save(a));
                                insertedCount++;
                                details.add("LINE " + line + ": INSERTED");
                            } else {
                                duplicateCount++;
                                details.add("LINE " + line + ": DUPLICATE");
                            }
                        } catch (Exception e) {
                            errorCount++;
                            details.add("LINE " + line + ": ERROR - " + e.getMessage());
                        }
                    }
                }

            } else if (name.endsWith(".xlsx")) {
                Workbook wb = WorkbookFactory.create(file.getInputStream());
                Sheet s = wb.getSheetAt(0);
                boolean header = true;
                for (Row row : s) {
                    if (header) { header = false; continue; }
                    if (row == null) continue;
                    String assetId = getCellString(row.getCell(0));
                    if (assetId.isBlank()) continue;

                    Asset a = mapRow(
                            assetId,
                            getCellString(row.getCell(1)),
                            getCellString(row.getCell(2)),
                            getCellString(row.getCell(3)),
                            getCellDate(row.getCell(4)),
                            getCellString(row.getCell(5)),
                            getCellString(row.getCell(6))
                    );

                    try {
                        if (!assetRepo.existsByAssetId(a.getAssetId())) {
                            imported.add(assetRepo.save(a));
                            insertedCount++;
                            details.add("ROW " + row.getRowNum() + ": INSERTED");
                        } else {
                            duplicateCount++;
                            details.add("ROW " + row.getRowNum() + ": DUPLICATE");
                        }
                    } catch (Exception e) {
                        errorCount++;
                        details.add("ROW " + row.getRowNum() + ": ERROR - " + e.getMessage());
                    }
                }
                wb.close();

            } else {
                model.addAttribute("error", "Formato no soportado. Sube un .csv o .xlsx");
                return "import/form";
            }

            ImportLog log = importLogRepo.save(
                    ImportLog.builder()
                            .importedAt(LocalDateTime.now())
                            .filename(file.getOriginalFilename())
                            .totalRows(insertedCount + duplicateCount + errorCount)
                            .insertedCount(insertedCount)
                            .duplicateCount(duplicateCount)
                            .errorCount(errorCount)
                            .detailsJson(String.join("\n", details))
                            .build()
            );

            model.addAttribute("count", insertedCount);
            model.addAttribute("details", details);
            model.addAttribute("fileName", file.getOriginalFilename());

            return "import/success";

        } catch (Exception e) {
            model.addAttribute("error", "No se pudo importar: " + e.getMessage());
            return "import/form";
        }
    }

    // ===================== REPORTES =========================

    @PostMapping("/report/current")
    public void currentImportReport(@RequestParam("format") String format,
                                    @RequestParam("fileName") String filename,
                                    HttpServletResponse response) throws Exception {

        Optional<ImportLog> logOpt = importLogRepo.findTopByFilenameOrderByImportedAtDesc(filename);
        if (logOpt.isEmpty()) throw new RuntimeException("No se encontró registro de importación");

        ImportLog log = logOpt.get();
        generateReport(log, format, response, "import_report_" + filename);
    }

    @PostMapping("/report/all")
    public void allImportsReport(@RequestParam("format") String format,
                                 HttpServletResponse response) throws Exception {

        List<ImportLog> logs = importLogRepo.findAll();
        generateReport(logs, format, response, "all_imports_report");
    }

    // ===================== MÉTODOS AUXILIARES =========================

    private void generateReport(ImportLog log, String format, HttpServletResponse response) throws Exception {
        if ("excel".equalsIgnoreCase(format)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + log.getFilename() + ".xlsx");

            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Import Report");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Fila/Linea");
                header.createCell(1).setCellValue("Estado");

                String[] lines = log.getDetailsJson().split("\n");
                for (int i = 0; i < lines.length; i++) {
                    Row row = sheet.createRow(i + 1);
                    row.createCell(0).setCellValue(i + 1);
                    row.createCell(1).setCellValue(lines[i]);
                }

                wb.write(response.getOutputStream());
            }
        } else if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=" + log.getFilename() + ".csv");
            try (PrintWriter writer = response.getWriter()) {
                writer.println("Fila/Linea,Estado");
                String[] lines = log.getDetailsJson().split("\n");
                for (int i = 0; i < lines.length; i++) {
                    writer.println((i + 1) + "," + lines[i]);
                }
            }
        } else {
            throw new RuntimeException("Formato no soportado: " + format);
        }
    }

    private void generateReport(List<ImportLog> logs, String format, HttpServletResponse response, String filename) throws Exception {
        if ("excel".equalsIgnoreCase(format)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".xlsx");

            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("All Imports");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Fecha");
                header.createCell(1).setCellValue("Archivo");
                header.createCell(2).setCellValue("Total Filas");
                header.createCell(3).setCellValue("Insertadas");
                header.createCell(4).setCellValue("Duplicadas");
                header.createCell(5).setCellValue("Errores");

                int rowIndex = 1;
                for (ImportLog log : logs) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(log.getImportedAt().toString());
                    row.createCell(1).setCellValue(log.getFilename());
                    row.createCell(2).setCellValue(log.getTotalRows());
                    row.createCell(3).setCellValue(log.getInsertedCount());
                    row.createCell(4).setCellValue(log.getDuplicateCount());
                    row.createCell(5).setCellValue(log.getErrorCount());
                }
                wb.write(response.getOutputStream());
            }
        } else if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".csv");
            try (PrintWriter writer = response.getWriter()) {
                writer.println("Fecha,Archivo,Total Filas,Insertadas,Duplicadas,Errores");
                for (ImportLog log : logs) {
                    writer.println(String.format("%s,%s,%d,%d,%d,%d",
                            log.getImportedAt(), log.getFilename(), log.getTotalRows(),
                            log.getInsertedCount(), log.getDuplicateCount(), log.getErrorCount()));
                }
            }
        } else {
            throw new RuntimeException("Formato no soportado: " + format);
        }
    }

    private Asset mapRow(String assetId, String model, String serial, String maker,
                         String purchase, String location, String value) {
        return Asset.builder()
                .assetId(assetId).model(model).serialNumber(serial).manufacturer(maker)
                .purchaseDate(LocalDate.parse(purchase)).initialLocation(location)
                .value(new BigDecimal(value)).build();
    }

    private char detectSeparator(String content) {
        String firstLine = content.lines().findFirst().orElse("");
        int commas = firstLine.length() - firstLine.replace(",", "").length();
        int semis = firstLine.length() - firstLine.replace(";", "").length();
        return (semis > commas) ? ';' : ',';
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        String s;
        switch (cell.getCellType()) {
            case STRING: s = cell.getStringCellValue(); break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) s = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                else {
                    double v = cell.getNumericCellValue();
                    long lv = (long) v;
                    s = (v == lv) ? Long.toString(lv) : Double.toString(v);
                }
                break;
            case BOOLEAN: s = Boolean.toString(cell.getBooleanCellValue()); break;
            case FORMULA: s = cell.getCellFormula(); break;
            default: s = "";
        }
        return s.trim();
    }

    private String getCellDate(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return getCellString(cell);
    }
}
