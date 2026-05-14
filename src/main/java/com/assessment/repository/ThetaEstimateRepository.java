package com.assessment.repository;
import com.assessment.entity.ThetaEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ThetaEstimateRepository extends JpaRepository<ThetaEstimate, Long> {
    Optional<ThetaEstimate> findByStudentIdAndTopic(Long studentId, String topic);
    List<ThetaEstimate> findByStudentId(Long studentId);
}
