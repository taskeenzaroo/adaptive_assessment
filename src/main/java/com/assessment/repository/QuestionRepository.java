package com.assessment.repository;

import com.assessment.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("""
    SELECT q FROM Question q
    WHERE q.topic = :topic
    AND q.skillTag = :skillTag
    AND q.difficulty = :difficulty
    AND q.id NOT IN :usedIds
    ORDER BY q.timesUsed ASC
    """)
    List<Question> findAvailableQuestions(
            @Param("topic") String topic,
            @Param("skillTag") Question.SkillTag skillTag,
            @Param("difficulty") Integer difficulty,
            @Param("usedIds") List<Long> usedIds
    );

    @Query("""
    SELECT q FROM Question q
    WHERE q.topic = :topic
    AND q.skillTag = :skillTag
    AND q.difficulty = :difficulty
    ORDER BY q.timesUsed ASC
    """)
    List<Question> findByTopicSkillDifficulty(
            @Param("topic") String topic,
            @Param("skillTag") Question.SkillTag skillTag,
            @Param("difficulty") Integer difficulty
    );

    List<Question> findByTopic(String topic);
}