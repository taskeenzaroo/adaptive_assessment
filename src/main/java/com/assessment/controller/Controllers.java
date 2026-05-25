package com.assessment.controller;

import com.assessment.entity.User;
import com.assessment.repository.UserRepository;
import com.assessment.service.AssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assessment")
class AssessmentController {

    private final AssessmentService assessmentService;
    private final UserRepository userRepository;

    AssessmentController(AssessmentService assessmentService,
                         UserRepository userRepository) {
        this.assessmentService = assessmentService;
        this.userRepository = userRepository;
    }

    // START
    @PostMapping("/start")
    public ResponseEntity<?> start(@AuthenticationPrincipal UserDetails userDetails,
                                   @RequestBody Map<String, Object> body) {

        Long studentId = getId(userDetails);
        List<String> topics = (List<String>) body.get("topics");

        if (topics == null || topics.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Topics required"));
        }

        return ResponseEntity.ok(
                assessmentService.startAssessment(studentId, topics)
        );
    }

    // ACTIVE SESSION CHECK
    @GetMapping("/active")
    public ResponseEntity<?> active(@AuthenticationPrincipal UserDetails userDetails) {

        Long studentId = getId(userDetails);

        return assessmentService.getActiveSession(studentId)
                .map(s -> ResponseEntity.ok(Map.of(
                        "active", true,
                        "session", s
                )))
                .orElse(ResponseEntity.ok(Map.of(
                        "active", false
                )));
    }

    // SUBMIT
    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {

        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        Long questionId = Long.valueOf(body.get("questionId").toString());
        String answer = (String) body.get("correct_answer");

        Integer timeTaken = body.get("timeTaken") != null
                ? Integer.valueOf(body.get("timeTaken").toString())
                : null;

        return ResponseEntity.ok(
                assessmentService.submitAnswer(sessionId, questionId, answer, timeTaken)
        );
    }

    @PostMapping("/abandon/{sessionId}")
    public ResponseEntity<?> abandonAssessment(@PathVariable Long sessionId) {
        return ResponseEntity.ok(assessmentService.abandonAssessment(sessionId));
    }

    // REPORTS
    @GetMapping("/report/latest")
    public ResponseEntity<?> latest(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                assessmentService.getLatestReport(getId(userDetails))
        );
    }

    @GetMapping("/report/{sessionId}")
    public ResponseEntity<?> bySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(
                assessmentService.getReportBySession(sessionId)
        );
    }

    @GetMapping("/reports")
    public ResponseEntity<?> allReports(@AuthenticationPrincipal UserDetails userDetails) {

        Long studentId = getId(userDetails);

        return ResponseEntity.ok(
                assessmentService.getAllReports(studentId)
        );
    }

    // HELPER
    private Long getId(UserDetails u) {
        return userRepository.findByEmail(u.getUsername())
                .map(User::getId)
                .orElseThrow();
    }
}