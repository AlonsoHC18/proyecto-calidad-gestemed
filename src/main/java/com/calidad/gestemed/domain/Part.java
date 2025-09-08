package com.calidad.gestemed.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Part {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String sku;
    private Integer minStock; // umbral
    private Integer stock;

    // modelos aplicables (MVP: texto)
    @Column(length=1000)
    private String applicableModels;

    // correo de notificación
    private String notificationEmail;
}
