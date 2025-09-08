package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.AssetMovement;
import com.calidad.gestemed.repo.AssetMovementRepo;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.service.AssetService;
import com.calidad.gestemed.service.impl.AzureBlobService;
import com.calidad.gestemed.service.impl.GeoAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetMovementRepo movementRepo;
    private final AssetRepo assetRepo;
    private final AzureBlobService azureBlobService;
    private final GeoAlertService geoAlertService;

    // Lista todos los activos
    @GetMapping
    public String list(Model model){
        model.addAttribute("assets", assetService.list());
        return "assets/list";
    }

    // Formulario para crear un nuevo activo
    @GetMapping("/new")
    public String form(Model model){
        model.addAttribute("asset", new Asset());
        return "assets/new";
    }

    // Crear un activo nuevo con fotos
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

    // Redirige al historial de movimientos de un activo
    @GetMapping("/{id}/movements")
    public String movements(@PathVariable Long id){
        return "redirect:/assets/" + id + "/history";
    }

    // Formulario para mover un activo
    @GetMapping("/{id}/move")
    public String moveForm(@PathVariable Long id, Model model) {
        model.addAttribute("asset", assetRepo.findById(id).orElseThrow());
        return "assets/move";
    }

    // Procesar movimiento de activo
    @PostMapping("/{id}/move")
    public String doMove(@PathVariable Long id,
                         @RequestParam String toLocation,
                         @RequestParam Double toLocationLatitude,
                         @RequestParam Double toLocationLongitude,
                         @RequestParam(required=false) String note,
                         Principal who) {

        Asset a = assetRepo.findById(id).orElseThrow();
        String from = a.getInitialLocation();
        Double fromLatitude = a.getLastLatitude();
        Double fromLongitude = a.getLastLongitude();

        a.setInitialLocation(toLocation);
        a.setLastLatitude(toLocationLatitude);
        a.setLastLongitude(toLocationLongitude);
        assetRepo.save(a);

        movementRepo.save(AssetMovement.builder()
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
                .build());

        // Aquí podrías llamar a geoAlertService para generar alertas si se activa alguna regla
        // geoAlertService.checkGeoAlerts(a);

        return "redirect:/assets/" + id + "/history";
    }

    // Historial de movimientos con filtros opcionales
    @GetMapping("/{id}/history")
    public String history(@PathVariable Long id,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required=false) String location,
                          Model model) {

        LocalDateTime fromDate = (from == null) ? LocalDateTime.of(1, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime toDate = (to == null) ? LocalDateTime.of(9999, 12, 31, 0, 0) : to.plusDays(1).atStartOfDay();
        String pattern = (location == null || location.isBlank()) ? null : "%" + location.trim() + "%";

        Asset asset = assetRepo.findById(id).orElseThrow();
        List<AssetMovement> list = movementRepo.searchNative(id, fromDate, toDate, pattern);

        model.addAttribute("asset", asset);
        model.addAttribute("movs", list);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("location", location);

        return "assets/history";
    }
}
