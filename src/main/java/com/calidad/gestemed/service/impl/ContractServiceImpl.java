package com.calidad.gestemed.service.impl;

import com.calidad.gestemed.domain.Contract;
import com.calidad.gestemed.domain.ContractStatus;
import com.calidad.gestemed.domain.Notification;
import com.calidad.gestemed.repo.ContractRepo;
import com.calidad.gestemed.repo.NotificationRepo;
import com.calidad.gestemed.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepo repo;
    private final NotificationRepo notificationRepo;
    private final MailSender mailSender;

    @Override
    public Contract save(Contract c) {
        c.setStatus(ContractStatus.VIGENTE);
        return repo.save(c);
    }

    @Override
    public List<Contract> list() {
        return repo.findAll();
    }

    @Scheduled(cron = "0 0 8 * * *") // cada mañana 08:00
    @Override
    public void checkAndNotifyExpiring() {
        LocalDate today = LocalDate.now();
        repo.findAll().forEach(c -> {
            LocalDate end = c.getEndDate();
            int alertDays = (c.getAlertDays() == null ? 30 : c.getAlertDays());
            if (end == null) return;

            String msg = null;
            if (end.isBefore(today)) {
                c.setStatus(ContractStatus.VENCIDO);
                msg = "Contrato " + c.getCode() + " vencido el " + end;
            } else if (!end.isBefore(today) && !end.isAfter(today.plusDays(alertDays))) {
                c.setStatus(ContractStatus.POR_VENCER);
                msg = "Contrato " + c.getCode() + " por vencer el " + end + " (≤ " + alertDays + " días)";
            } else {
                c.setStatus(ContractStatus.VIGENTE);
            }

            repo.save(c);

            if (msg != null) sendNotification(msg, c.getClientEmail());
        });
    }

    private void sendNotification(String msg, String clientEmail) {
        // Guardar en panel de notificaciones
        notificationRepo.save(Notification.builder()
                .message(msg)
                .createdAt(java.time.LocalDateTime.now())
                .build());

        // Enviar correo al cliente
        if (clientEmail != null && !clientEmail.isEmpty()) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setTo(clientEmail);
                m.setSubject("Alerta de contrato");
                m.setText(msg);
                mailSender.send(m);
            } catch (Exception ignored) {}
        }
    }
}
