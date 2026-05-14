package com.assessment.repository;
import com.assessment.entity.Question;
import com.assessment.entity.SkillMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SkillMasteryRepository extends JpaRepository<SkillMastery, Long> {
    Optional<SkillMastery> findByStudentIdAndTopicAndSkillTag(Long studentId, String topic, Question.SkillTag skillTag);
    List<SkillMastery> findByStudentId(Long studentId);
    List<SkillMastery> findByStudentIdAndTopic(Long studentId, String topic);
}
