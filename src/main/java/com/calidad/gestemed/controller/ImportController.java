package com.calidad.gestemed.controller;

// controller/ImportController.java

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.repo.AssetRepo;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Este controlador maneja la carga de archivos CSV y XLSX para la importación de datos.
// Incluye lógica estándar para detectar formatos, leer el contenido,
// y mapear las filas a objetos de la base de datos, evitando duplicados.
// Es una plantilla común para procesos de importación.

@Controller @RequiredArgsConstructor
@RequestMapping("/import")
public class ImportController {
    private final AssetRepo assetRepo;

    @GetMapping public String form() { return "import/form"; }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            if (file.isEmpty()) {
                model.addAttribute("error", "Selecciona un archivo CSV o XLSX.");
                return "import/form";
            }

            String name = (file.getOriginalFilename() == null ? "" : file.getOriginalFilename()).toLowerCase();

            if (name.endsWith(".csv")) {
                // Leemos todo el contenido para detectar separador y quitar caracteres especiales
                String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                content = content.replace("\uFEFF", ""); // quita BOM si existe

                char sep = detectSeparator(content); // ',' o ';'

                // OpenCSV con separador detectado
                com.opencsv.CSVParser parser = new com.opencsv.CSVParserBuilder()
                        .withSeparator(sep)
                        .build();

                try (com.opencsv.CSVReader r = new com.opencsv.CSVReaderBuilder(new java.io.StringReader(content))
                        .withCSVParser(parser)
                        .build()) {

                    java.util.List<com.calidad.gestemed.domain.Asset> imported = new java.util.ArrayList<>();
                    java.util.List<String> duplicates = new java.util.ArrayList<>();
                    String[] row;
                    boolean header = true;
                    int line = 0;

                    while ((row = r.readNext()) != null) {
                        line++;

                        if (header) { header = false; continue; }          // salta encabezado
                        if (row.length == 0) continue;                      // fila completamente vacía
                        if (row.length == 1 && (row[0] == null || row[0].isBlank())) continue; // línea en blanco

                        if (row.length < 7) {
                            model.addAttribute("error",
                                    "Formato CSV inválido en línea " + line + ": se esperaban 7 columnas y llegaron " + row.length);
                            return "import/form";
                        }

                        // Trim de columnas
                        for (int i = 0; i < row.length; i++) {
                            row[i] = (row[i] == null ? "" : row[i].trim());
                        }

                        com.calidad.gestemed.domain.Asset a = mapRow(
                                row[0], row[1], row[2], row[3], row[4], row[5], row[6]
                        );

                        // Evitar duplicados por assetId
                        if (!assetRepo.existsByAssetId(a.getAssetId())) {
                            imported.add(assetRepo.save(a));
                        } else {
                            duplicates.add(a.getAssetId());
                    }

                    model.addAttribute("count", imported.size());
                    model.addAttribute("duplicates", duplicates);
                    return "import/success";
                }

            } else if (name.endsWith(".xlsx")) {
                // Tu ruta de Excel sigue igual
                java.util.List<com.calidad.gestemed.domain.Asset> imported = new java.util.ArrayList<>();
                java.util.List<String> duplicates = new java.util.ArrayList<>();
                try (org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
                    org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(0);
                    boolean header = true;
                    for (org.apache.poi.ss.usermodel.Row row : s) {
                        if (header) { header = false; continue; }
                        if (row == null) continue;
                        // Protege contra celdas nulas
                        String assetId = getCellString(row.getCell(0));
                        if (assetId.isBlank()) continue;

                        com.calidad.gestemed.domain.Asset a = mapRow(
                                assetId,
                                getCellString(row.getCell(1)),
                                getCellString(row.getCell(2)),
                                getCellString(row.getCell(3)),
                                getCellDate(row.getCell(4)),
                                getCellString(row.getCell(5)),
                                getCellString(row.getCell(6))
                        );
                        if (!assetRepo.existsByAssetId(a.getAssetId())) {
                            imported.add(assetRepo.save(a));
                        } else {
                            duplicates.add(a.getAssetId());
                    }
                }
                model.addAttribute("count", /* imported.size() si lo guardaste */ 0);
                model.addAttribute("duplicates", duplicates);
                return "import/success";
            } else {
                model.addAttribute("error", "Formato no soportado. Sube un .csv o .xlsx");
                return "import/form";
            }

        } catch (Exception e) {
            model.addAttribute("error", "No se pudo importar: " + e.getMessage());
            return "import/form";
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
        // Se queda solo con la primera línea (encabezado)
        String firstLine = content.lines().findFirst().orElse("");
        int commas = firstLine.length() - firstLine.replace(",", "").length();
        int semis  = firstLine.length() - firstLine.replace(";", "").length();
        return (semis > commas) ? ';' : ',';
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        String s;
        CellType type = cell.getCellType();
        switch (type) {
            case STRING:
                s = cell.getStringCellValue();
                break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    s = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    double v = cell.getNumericCellValue();
                    long lv = (long) v;
                    s = (v == lv) ? Long.toString(lv) : Double.toString(v);
                }
                break;
            case BOOLEAN:
                s = Boolean.toString(cell.getBooleanCellValue());
                break;
            case FORMULA:
                s = cell.getCellFormula();
                break;
            default:
                s = "";
        }
        return (s == null) ? "" : s.trim();
    }

    private String getCellDate(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        // Si Excel guardó la fecha como texto:
        return getCellString(cell);
    }
}
