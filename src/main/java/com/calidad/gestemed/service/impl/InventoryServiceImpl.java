package com.calidad.gestemed.service.impl;

import com.calidad.gestemed.domain.Notification;
import com.calidad.gestemed.domain.Part;
import com.calidad.gestemed.domain.PartMovement;
import com.calidad.gestemed.repo.NotificationRepo;
import com.calidad.gestemed.repo.PartMovementRepo;
import com.calidad.gestemed.repo.PartRepo;
import com.calidad.gestemed.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final PartRepo partRepo;
    private final PartMovementRepo movementRepo;
    private final NotificationRepo notificationRepo;
    private final JavaMailSender mailSender; // para enviar correos

    // Ajusta el stock de una pieza
    @Override
    public void adjustStock(Long partId, int delta, String note) {
        Part p = partRepo.findById(partId).orElseThrow();
        p.setStock((p.getStock() == null ? 0 : p.getStock()) + delta);
        partRepo.save(p);

        movementRepo.save(PartMovement.builder()
                .part(p)
                .delta(delta)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // Revisar inventario bajo y notificar (ejecuta todos los días a las 08:05 am)
    @Scheduled(cron = "0 5 8 * * *")
    @Override
    public void checkLowStockAndNotify() {
        partRepo.findAll().forEach(p -> {
            if (p.getMinStock() != null && p.getStock() != null && p.getStock() <= p.getMinStock()) {
                // Guardar notificación en panel
                notificationRepo.save(Notification.builder()
                        .message("Stock bajo en refacción: " + p.getName() +
                                " (actual=" + p.getStock() + ", min=" + p.getMinStock() + ")")
                        .createdAt(LocalDateTime.now())
                        .build());

                // Enviar correo si el email está definido
                if (p.getNotificationEmail() != null && !p.getNotificationEmail().isBlank()) {
                    try {
                        SimpleMailMessage msg = new SimpleMailMessage();
                        msg.setTo(p.getNotificationEmail());
                        msg.setSubject("Alerta: Stock bajo en refacción");
                        msg.setText("La refacción \"" + p.getName() +
                                "\" tiene un stock bajo.\nActual: " + p.getStock() +
                                "\nMínimo requerido: " + p.getMinStock());
                        mailSender.send(msg);
                    } catch (Exception e) {
                        System.out.println("[ALERT] No se pudo enviar correo a " + p.getNotificationEmail() +
                                ": " + e.getMessage());
                    }
                }
            }
        });
    }
}
