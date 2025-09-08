package com.calidad.gestemed.service.impl;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.Notification;
import com.calidad.gestemed.domain.User;
import com.calidad.gestemed.repo.NotificationRepo;
import com.calidad.gestemed.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoAlertService {

    private final NotificationRepo notificationRepo;
    private final UserRepo userRepo;
    private final JavaMailSender mailSender;

    // Costa Rica
    private static final double MIN_LAT = 8.0;
    private static final double MAX_LAT = 11.25;
    private static final double MIN_LON = -86.0;
    private static final double MAX_LON = -82.0;

    public void checkGeoFence(Asset asset) {

        Double lat = asset.getLastLatitude();
        Double lon = asset.getLastLongitude();

        if(lat == null || lon == null) return; // si no hay GPS, no hacemos nada

        boolean outside = lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON;

        if(outside){
            String msg = "El activo " + asset.getAssetId() +
                         " ha salido de Costa Rica. Ubicación: ("+lat+", "+lon+")";

            // Guardar en base
            Notification n = Notification.builder()
                    .message(msg)
                    .readFlag(false)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            notificationRepo.save(n);

            // Enviar correo a todos los usuarios con rol admin
            List<User> admins = userRepo.findAll().stream()
                    .filter(u -> u.getRole()!=null && u.getRole().getRoleName().equalsIgnoreCase("ADMIN"))
                    .toList();

            for(User u: admins){
                try {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setTo(u.getEmail());
                    mail.setSubject("Alerta Geocerca: activo fuera de Costa Rica");
                    mail.setText(msg);
                    mailSender.send(mail);
                } catch (Exception e){
                    System.out.println("[WARN] No se pudo enviar correo a " + u.getEmail());
                }
            }
        }
    }
}
