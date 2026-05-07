package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_answers")
public class SessionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AssessmentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_tag", nullable = false)
    private Question.SkillTag skillTag;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(name = "student_answer", length = 500)
    private String studentAnswer;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "scaffolding_triggered")
    private Boolean scaffoldingTriggered = false;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt = LocalDateTime.now();

    public SessionAnswer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AssessmentSession getSession() { return session; }
    public void setSession(AssessmentSession session) { this.session = session; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Question.SkillTag getSkillTag() { return skillTag; }
    public void setSkillTag(Question.SkillTag skillTag) { this.skillTag = skillTag; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    public Boolean getScaffoldingTriggered() { return scaffoldingTriggered; }
    public void setScaffoldingTriggered(Boolean scaffoldingTriggered) { this.scaffoldingTriggered = scaffoldingTriggered; }
    public Integer getTimeTakenSeconds() { return timeTakenSeconds; }
    public void setTimeTakenSeconds(Integer timeTakenSeconds) { this.timeTakenSeconds = timeTakenSeconds; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }
}
