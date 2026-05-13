package com.assessment.repository;
import com.assessment.entity.AssessmentSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, Long> {
    List<AssessmentSession> findByStudentId(Long studentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssessmentSession> findByStudentIdAndStatus(Long studentId, AssessmentSession.SessionStatus status);
}
