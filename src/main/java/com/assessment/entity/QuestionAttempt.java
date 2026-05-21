//package com.assessment.entity;
//
//import jakarta.persistence.*;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "question_attempts")
//public class QuestionAttempt {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "session_id", nullable = false)
//    private AssessmentSession session;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "question_id")
//    private Question question;
//
//    private String topic;
//    private String skillTag;
//    private Integer difficulty;
//
//    @Column(columnDefinition = "TEXT")
//    private String questionText;
//
//    @Column(columnDefinition = "TEXT")
//    private String studentAnswer;
//
//    @Column(columnDefinition = "TEXT")
//    private String correctAnswer;
//
//    private Boolean wasCorrect;
//
//    private Boolean scaffoldingUsed = false;
//
//    @Column(columnDefinition = "TEXT")
//    private String scaffoldQuestion;
//
//    @Column(columnDefinition = "TEXT")
//    private String scaffoldStudentAnswer;
//
//    @Column(columnDefinition = "TEXT")
//    private String scaffoldCorrectAnswer;
//
//    private Boolean scaffoldWasCorrect;
//
//    private LocalDateTime answeredAt = LocalDateTime.now();
//
//    // getters and setters
//}