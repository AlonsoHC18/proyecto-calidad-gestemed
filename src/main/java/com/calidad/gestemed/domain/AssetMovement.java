// domain/AssetMovement.java

package com.calidad.gestemed.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="asset_movements",
        indexes = {
                @Index(name="idx_mov_asset", columnList="asset_id"),
                @Index(name="idx_mov_date", columnList="movedAt")
        })
public class AssetMovement {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Asset asset;

    private LocalDateTime movedAt;

    @Column(length=200)
    private String fromLocation;

    @Column(length=200)
    private String toLocation;

    @Column(name = "from_location_latitude")
    private Double fromLocationLatitude;

    @Column(name = "from_location_longitude")
    private Double fromLocationLongitude;

    @Column(name = "to_location_latitude")
    private Double toLocationLatitude;

    @Column(name = "to_location_longitude")
    private Double toLocationLongitude;

    @Column(length=100)
    private String reason;

    @Column(length=100)
    private String performedBy;

    // ----------- NUEVOS CAMPOS PARA F-05 -----------
    @Enumerated(EnumType.STRING)
    private MovementType movementType;  // ENTREGA o DEVOLUCION

    @Column(length=4000)
    private String signaturePath;       // URL de la firma subida a Azure Blob
}
