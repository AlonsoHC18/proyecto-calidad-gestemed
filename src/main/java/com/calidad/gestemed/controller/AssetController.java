package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.AssetMovement;
import com.calidad.gestemed.domain.Notification;
import com.calidad.gestemed.domain.User; // Asumiendo que tienes entidad User con email
import com.calidad.gestemed.repo.AssetMovementRepo;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.repo.NotificationRepo;
import com.calidad.gestemed.repo.UserRepo;
import com.calidad.gestemed.service.AssetService;
import com.calidad.gestemed.service.impl.AzureBlobService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
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
    private final NotificationRepo notificationRepo;
    private final UserRepo userRepo;              // Para obtener correo del administrador
    private final JavaMailSender mailSender;      // Para enviar email

    // GET /assets
    @GetMapping
    public String list(Model model){
        model.addAttribute("assets", assetService.list());
        return "assets/list";
    }

    // GET /assets/new
    @GetMapping("/new")
    public String form(Model model){
        model.addAttribute("asset", new Asset());
        return "assets/new";
    }

    // POST /assets
    @PostMapping
    public String create(Asset asset, @RequestParam("photos") List<MultipartFile> photos, Principal who){
        String photosUrls = photos.stream()
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    try {
                        return azureBlobService.uploadFile(f);
                    } catch (Exception e) {
                        System.out.println("[WARN] No se pudo subir la foto: " + e.getMessage());
                        return null;
                    }
                }).filter(url -> url != null)
                .collect(Collectors.joining("|"));

        asset.setPhotoPaths(photosUrls);
        assetService.create(asset, (who!=null?who.getName():"admin"));
        return "redirect:/assets?created";
    }

    @GetMapping("/{id}/move")
    public String moveForm(@PathVariable Long id, Model model) {
        model.addAttribute("asset", assetRepo.findById(id).orElseThrow());
        return "assets/move";
    }

    // POST /assets/{id}/move — incluyendo alerta por geocerca
    @PostMapping("/{id}/move")
    public String doMove(@PathVariable Long id,
                         @RequestParam String toLocation,
                         @RequestParam Double toLocationLatitude,
                         @RequestParam Double toLocationLongitude,
                         @RequestParam(required=false) String note,
                         Principal who) {

        Asset a = assetRepo.findById(id).orElseThrow();
        String from = a.getInitialLocation();
        Double fromLat = a.getLastLatitude();
        Double fromLon = a.getLastLongitude();

        a.setInitialLocation(toLocation);
        a.setLastLatitude(toLocationLatitude);
        a.setLastLongitude(toLocationLongitude);
        assetRepo.save(a);

        movementRepo.save(AssetMovement.builder()
                .asset(a)
                .fromLocation(from)
                .toLocation(toLocation)
                .fromLocationLatitude(fromLat)
                .fromLocationLongitude(fromLon)
                .toLocationLatitude(toLocationLatitude)
                .toLocationLongitude(toLocationLongitude)
                .reason(note)
                .performedBy(who!=null?who.getName():"system")
                .movedAt(LocalDateTime.now())
                .build());

        // --- F-10: Verificar geocerca ---
        if(!isInsideCostaRica(toLocationLatitude, toLocationLongitude)) {
            // Crear notificación en el panel
            notificationRepo.save(Notification.builder()
                    .message("Alerta: Activo " + a.getId() + " salió de la zona segura y se encuentra en " + toLocation)
                    .createdAt(LocalDateTime.now())
                    .build());

            // Enviar correo al administrador
            userRepo.findAll().stream()
                    .filter(User::isAdmin)
                    .filter(u -> u.getEmail()!=null && !u.getEmail().isBlank())
                    .forEach(u -> {
                        try {
                            SimpleMailMessage msg = new SimpleMailMessage();
                            msg.setTo(u.getEmail());
                            msg.setSubject("Alerta: Activo fuera de geocerca");
                            msg.setText("Tu activo " + a.getId() + " salió de la zona segura y se encuentra en " + toLocation);
                            mailSender.send(msg);
                        } catch(Exception e){
                            System.out.println("[WARN] No se pudo enviar correo: "+e.getMessage());
                        }
                    });
        }

        return "redirect:/assets/" + id + "/history";
    }

    // Método auxiliar para geocerca Costa Rica
    private boolean isInsideCostaRica(double lat, double lon) {
        return lat >= 8.0 && lat <= 11.3 && lon >= -85.9 && lon <= -82.5;
    }

}
