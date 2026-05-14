package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_bank")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", length = 500)
    private String optionA;

    @Column(name = "option_b", length = 500)
    private String optionB;

    @Column(name = "option_c", length = 500)
    private String optionC;

    @Column(name = "option_d", length = 500)
    private String optionD;

    @Column(name = "correct_answer", length = 10)
    private String correctAnswer;

    @Column(nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_tag", nullable = false)
    private SkillTag skillTag;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(name = "times_used")
    private Integer timesUsed = 0;

    public enum SkillTag { concept, calculation, application }
    public Question() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }
    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }
    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }
    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public SkillTag getSkillTag() { return skillTag; }
    public void setSkillTag(SkillTag skillTag) { this.skillTag = skillTag; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public Integer getTimesUsed() { return timesUsed; }
    public void setTimesUsed(Integer timesUsed) { this.timesUsed = timesUsed; }
}
