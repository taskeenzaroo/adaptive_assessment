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

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType = QuestionType.MCQ;

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

    @Column(name = "correct_value", length = 500)
    private String correctValue;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(length = 200)
    private String subtopic;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_tag", nullable = false)
    private SkillTag skillTag;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(name = "has_image")
    private Boolean hasImage = false;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_description", columnDefinition = "TEXT")
    private String imageDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_format")
    private QuestionFormat questionFormat = QuestionFormat.TEXT_ONLY;

    @Column(name = "times_used")
    private Integer timesUsed = 0;

    @Column(length = 200)
    private String source;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum QuestionType { MCQ, SHORT_ANSWER, SUBJECTIVE }
    public enum SkillTag { concept, calculation, application }
    public enum QuestionFormat { TEXT_ONLY, TEXT_WITH_IMAGE, IMAGE_ONLY }

    public Question() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }
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
    public String getCorrectValue() { return correctValue; }
    public void setCorrectValue(String correctValue) { this.correctValue = correctValue; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getSubtopic() { return subtopic; }
    public void setSubtopic(String subtopic) { this.subtopic = subtopic; }
    public SkillTag getSkillTag() { return skillTag; }
    public void setSkillTag(SkillTag skillTag) { this.skillTag = skillTag; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public Boolean getHasImage() { return hasImage; }
    public void setHasImage(Boolean hasImage) { this.hasImage = hasImage; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String imageDescription) { this.imageDescription = imageDescription; }
    public QuestionFormat getQuestionFormat() { return questionFormat; }
    public void setQuestionFormat(QuestionFormat questionFormat) { this.questionFormat = questionFormat; }
    public Integer getTimesUsed() { return timesUsed; }
    public void setTimesUsed(Integer timesUsed) { this.timesUsed = timesUsed; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
