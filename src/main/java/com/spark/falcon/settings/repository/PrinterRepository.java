package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    Optional<Printer> findByIdAndBusinessId(Long id, Long businessId);
    List<Printer> findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscTitleAsc(Long businessId);
}
