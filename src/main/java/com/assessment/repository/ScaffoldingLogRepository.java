package com.assessment.repository;
import com.assessment.entity.ScaffoldingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScaffoldingLogRepository extends JpaRepository<ScaffoldingLog, Long> {
    List<ScaffoldingLog> findBySessionId(Long sessionId);
}
