package com.assessment.repository;
import com.assessment.entity.AssessmentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, Long> {
    List<AssessmentSession> findByStudentId(Long studentId);
    Optional<AssessmentSession> findByStudentIdAndStatus(Long studentId, AssessmentSession.SessionStatus status);
}
