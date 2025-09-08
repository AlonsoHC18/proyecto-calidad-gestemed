package com.calidad.gestemed.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime importedAt;

    private String filename;

    private String uploadedBy;

    private Integer totalRows;

    private Integer insertedCount;

    private Integer duplicateCount;

    private Integer errorCount;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String detailsJson; // JSON con lista de filas y estado (INSERTED, DUPLICATE, ERROR)
}
