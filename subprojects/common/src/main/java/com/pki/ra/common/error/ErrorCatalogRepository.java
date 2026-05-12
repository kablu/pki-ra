package com.pki.ra.common.error;

import com.pki.ra.common.model.ErrorCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ErrorCatalogRepository extends JpaRepository<ErrorCatalog, Long> {

    @Query("SELECT e FROM ErrorCatalog e WHERE e.isActive = true")
    List<ErrorCatalog> findAllActive();
}
