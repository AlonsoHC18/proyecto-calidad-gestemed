package com.calidad.gestemed.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Contract {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String clientName;
    private String clientEmail; // NUEVO: email del cliente

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(length=2000)
    private String terms;

    private Integer alertDays;

    @ManyToMany
    @JoinTable(name="contract_assets",
            joinColumns=@JoinColumn(name="contract_id"),
            inverseJoinColumns=@JoinColumn(name="asset_id"))
    private Set<Asset> assets;

    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    @Transient
    public String getStatus() {
        LocalDate today = LocalDate.now();
        if (endDate == null) return "VIGENTE";
        if (endDate.isBefore(today)) return "VENCIDO";
        int alert = (alertDays == null ? 0 : alertDays);
        LocalDate warnFrom = today.plusDays(alert);
        return (!endDate.isAfter(warnFrom)) ? "POR_VENCER" : "VIGENTE";
    }
}
