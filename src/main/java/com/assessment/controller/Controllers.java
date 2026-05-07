package com.assessment.controller;

import com.assessment.entity.Question;
import com.assessment.entity.User;
import com.assessment.repository.QuestionRepository;
import com.assessment.repository.UserRepository;
import com.assessment.service.AssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

// ── Assessment Controller ──────────────────────────────────────────────────

@RestController
@RequestMapping("/api/assessment")
class AssessmentController {

    private final AssessmentService assessmentService;
    private final UserRepository userRepository;

    AssessmentController(AssessmentService assessmentService, UserRepository userRepository) {
        this.assessmentService = assessmentService;
        this.userRepository = userRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@AuthenticationPrincipal UserDetails userDetails,
                                   @RequestBody Map<String, Object> body) {
        Long studentId = getId(userDetails);
        List<String> topics = (List<String>) body.get("topics");
        if (topics == null || topics.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "Topics required"));
        return ResponseEntity.ok(assessmentService.startAssessment(studentId, topics));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
        Long sessionId  = Long.valueOf(body.get("sessionId").toString());
        Long questionId = Long.valueOf(body.get("questionId").toString());
        String answer   = (String) body.get("answer");
        Integer timeTaken = body.get("timeTaken") != null
            ? Integer.valueOf(body.get("timeTaken").toString()) : null;
        return ResponseEntity.ok(assessmentService.submitAnswer(sessionId, questionId, answer, timeTaken));
    }

    @GetMapping("/report/latest")
    public ResponseEntity<?> latestReport(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(assessmentService.getLatestReport(getId(userDetails)));
    }

    @GetMapping("/report/{sessionId}")
    public ResponseEntity<?> reportBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(assessmentService.getReportBySession(sessionId));
    }

    private Long getId(UserDetails u) {
        return userRepository.findByEmail(u.getUsername())
            .map(User::getId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
}

// ── Student Controller ─────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/students")
class StudentController {

    private final AssessmentService assessmentService;
    private final UserRepository userRepository;

    StudentController(AssessmentService assessmentService, UserRepository userRepository) {
        this.assessmentService = assessmentService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> myProfile(@AuthenticationPrincipal UserDetails userDetails) {
        Long id = userRepository.findByEmail(userDetails.getUsername())
            .map(User::getId).orElseThrow();
        return ResponseEntity.ok(assessmentService.getStudentProfile(id));
    }

    @GetMapping("/me/report")
    public ResponseEntity<?> myReport(@AuthenticationPrincipal UserDetails userDetails) {
        Long id = userRepository.findByEmail(userDetails.getUsername())
            .map(User::getId).orElseThrow();
        return ResponseEntity.ok(assessmentService.getLatestReport(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(assessmentService.getStudentProfile(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllStudents() {
        List<User> students = userRepository.findAll().stream()
            .filter(u -> u.getRole() == User.Role.STUDENT).toList();
        return ResponseEntity.ok(students.stream()
            .map(s -> assessmentService.getStudentProfile(s.getId())).toList());
    }
}

// ── Question Controller ────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/questions")
class QuestionController {

    private final QuestionRepository questionRepository;

    QuestionController(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Question>> getAll() {
        return ResponseEntity.ok(questionRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Question> getById(@PathVariable Long id) {
        return questionRepository.findById(id)
            .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Question> create(@RequestBody Question q) {
        q.setId(null);
        q.setTimesUsed(0);
        q.setIsActive(true);
        return ResponseEntity.ok(questionRepository.save(q));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Question> update(@PathVariable Long id, @RequestBody Question updated) {
        return questionRepository.findById(id).map(q -> {
            q.setQuestionText(updated.getQuestionText());
            q.setQuestionType(updated.getQuestionType());
            q.setOptionA(updated.getOptionA());
            q.setOptionB(updated.getOptionB());
            q.setOptionC(updated.getOptionC());
            q.setOptionD(updated.getOptionD());
            q.setCorrectAnswer(updated.getCorrectAnswer());
            q.setCorrectValue(updated.getCorrectValue());
            q.setTopic(updated.getTopic());
            q.setSubtopic(updated.getSubtopic());
            q.setSkillTag(updated.getSkillTag());
            q.setDifficulty(updated.getDifficulty());
            q.setIsActive(updated.getIsActive());
            return ResponseEntity.ok(questionRepository.save(q));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return questionRepository.findById(id).map(q -> {
            q.setIsActive(false);
            questionRepository.save(q);
            return ResponseEntity.ok(Map.of("message", "Deactivated"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/topic/{topic}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Question>> byTopic(@PathVariable String topic) {
        return ResponseEntity.ok(questionRepository.findByTopicAndIsActiveTrue(topic));
    }
}
