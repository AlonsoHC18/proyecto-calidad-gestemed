package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.AssetMovement;
import com.calidad.gestemed.domain.MovementType;
import com.calidad.gestemed.repo.AssetMovementRepo;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.service.AssetService;
import com.calidad.gestemed.service.impl.AzureBlobService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.io.IOException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetMovementRepo movementRepo;
    private final AssetRepo assetRepo;
    private final AzureBlobService azureBlobService;

    @GetMapping
    public String list(Model model){
        model.addAttribute("assets", assetService.list());
        return "assets/list";
    }

    @GetMapping("/new")
    public String form(Model model){
        model.addAttribute("asset", new Asset());
        return "assets/new";
    }

    @PostMapping
    public String create(Asset asset, @RequestParam("photos") List<MultipartFile> photos, Authentication auth){
        String photosUrls = photos.stream()
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    try {
                        return azureBlobService.uploadFile(f);
                    } catch (IOException e) {
                        System.out.println("[WARN] No se pudo subir la foto: " + e.getMessage());
                        return null;
                    }
                })
                .filter(url -> url != null)
                .collect(Collectors.joining("|"));

        asset.setPhotoPaths(photosUrls);
        assetService.create(asset, (auth != null ? auth.getName() : "admin"));
        return "redirect:/assets?created";
    }

    @GetMapping("/{id}/movements")
    public String movements(@PathVariable Long id){
        return "redirect:/assets/" + id + "/history";
    }

    @GetMapping("/{id}/move")
    public String moveForm(@PathVariable Long id, Model model) {
        model.addAttribute("asset", assetRepo.findById(id).orElseThrow());
        return "assets/move";
    }

    @PostMapping("/{id}/move")
    public String doMove(@PathVariable Long id,
                         @RequestParam String toLocation,
                         @RequestParam Double toLocationLatitude,
                         @RequestParam Double toLocationLongitude,
                         @RequestParam(required=false) String note,
                         @RequestParam(required=false) MovementType movementType,
                         @RequestParam(required=false) String signatureDataUrl,
                         Principal who) {

        Asset a = assetRepo.findById(id).orElseThrow();
        String from = a.getInitialLocation();
        Double fromLatitude = a.getLastLatitude();
        Double fromLongitude = a.getLastLongitude();

        a.setInitialLocation(toLocation);
        a.setLastLatitude(toLocationLatitude);
        a.setLastLongitude(toLocationLongitude);

        assetRepo.save(a);

        AssetMovement.AssetMovementBuilder builder = AssetMovement.builder()
                .asset(a)
                .fromLocation(from)
                .toLocation(toLocation)
                .fromLocationLatitude(fromLatitude)
                .fromLocationLongitude(fromLongitude)
                .toLocationLatitude(toLocationLatitude)
                .toLocationLongitude(toLocationLongitude)
                .reason(note)
                .performedBy(who != null ? who.getName() : "system")
                .movedAt(LocalDateTime.now())
                .movementType(movementType);

        // Guardamos la firma si existe
        if (signatureDataUrl != null && signatureDataUrl.startsWith("data:image")) {
            try {
                String b64 = signatureDataUrl.substring(signatureDataUrl.indexOf(",") + 1);
                byte[] png = java.util.Base64.getDecoder().decode(b64);
                MultipartFile signatureFile = new ByteArrayMultipartFile(png, "signature.png");
                String signatureUrl = azureBlobService.uploadFile(signatureFile);
                builder.signaturePath(signatureUrl);
            } catch (IOException e) {
                System.out.println("[WARN] No se pudo subir la firma: " + e.getMessage());
            }
        }

        movementRepo.save(builder.build());
        return "redirect:/assets/" + id + "/history";
    }

    @GetMapping("/{id}/history")
    public String history(@PathVariable Long id,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required=false) String location,
                          Model model) {

        LocalDateTime fromDate = (from == null) ? LocalDateTime.of(1, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime toDate = (to == null) ? LocalDateTime.of(9999, 12, 31, 0, 0) : to.plusDays(1).atStartOfDay();
        String pattern = (location == null || location.isBlank()) ? null : "%" + location.trim() + "%";

        var asset = assetRepo.findById(id).orElseThrow();
        var list  = movementRepo.searchNative(id, fromDate, toDate, pattern);

        model.addAttribute("asset", asset);
        model.addAttribute("movs", list);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("location", location);

        return "assets/history";
    }

    // Clase auxiliar para convertir Base64 en MultipartFile
    static class ByteArrayMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final byte[] bytes;
        private final String filename;
        public ByteArrayMultipartFile(byte[] bytes, String filename) { this.bytes = bytes; this.filename = filename; }
        @Override public String getName() { return filename; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return "image/png"; }
        @Override public boolean isEmpty() { return bytes == null || bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) { fos.write(bytes); }
        }
    }
}
