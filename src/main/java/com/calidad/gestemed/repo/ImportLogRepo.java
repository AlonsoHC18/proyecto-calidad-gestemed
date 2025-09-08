package com.calidad.gestemed.repo;

import com.calidad.gestemed.domain.ImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportLogRepo extends JpaRepository<ImportLog, Long> {
}
