package com.tim.language_project.repository;

import com.tim.language_project.entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * API 用量與費用紀錄的資料存取。
 */
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {
}
