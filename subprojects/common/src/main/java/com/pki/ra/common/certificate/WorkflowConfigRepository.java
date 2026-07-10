package com.pki.ra.common.certificate;

import com.pki.ra.common.model.WorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, Long> {

    Optional<WorkflowConfig> findByConfigKeyAndIsActiveTrue(String configKey);

    List<WorkflowConfig> findAllByIsActiveTrue();
}
