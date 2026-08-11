package com.tim.language_project.repository;

import com.tim.language_project.entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for API usage and cost records.
 */
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {
}
