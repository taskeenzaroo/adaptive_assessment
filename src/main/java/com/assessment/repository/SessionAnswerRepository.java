package com.assessment.repository;
import com.assessment.entity.Question;
import com.assessment.entity.SessionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, Long> {
    List<SessionAnswer> findBySessionId(Long sessionId);

    @Query("SELECT sa.question.id FROM SessionAnswer sa WHERE sa.session.id = :sessionId")
    List<Long> findQuestionIdsBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(sa) FROM SessionAnswer sa WHERE sa.session.id = :sessionId AND sa.topic = :topic AND sa.skillTag = :skillTag AND sa.isCorrect = true")
    Long countCorrectBySessionTopicSkill(@Param("sessionId") Long sessionId, @Param("topic") String topic, @Param("skillTag") Question.SkillTag skillTag);

    @Query("SELECT COUNT(sa) FROM SessionAnswer sa WHERE sa.session.id = :sessionId AND sa.topic = :topic AND sa.skillTag = :skillTag")
    Long countTotalBySessionTopicSkill(@Param("sessionId") Long sessionId, @Param("topic") String topic, @Param("skillTag") Question.SkillTag skillTag);
}
