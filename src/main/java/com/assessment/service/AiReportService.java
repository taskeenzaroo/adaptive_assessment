package com.assessment.service;

import com.assessment.entity.*;
import com.assessment.repository.ScaffoldingLogRepository;
import com.assessment.repository.SessionAnswerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiReportService {

    private final SessionAnswerRepository answerRepository;
    private final ScaffoldingLogRepository scaffoldingLogRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    public AiReportService(SessionAnswerRepository answerRepository,
                           ScaffoldingLogRepository scaffoldingLogRepository) {
        this.answerRepository = answerRepository;
        this.scaffoldingLogRepository = scaffoldingLogRepository;
    }

    public Map<String, Object> generateAiReport(AssessmentSession session,
                                                Map<String, Object> numericReport) {
        System.out.println("AI REPORT SERVICE CALLED");
        System.out.println("Groq key present = " + (apiKey != null && !apiKey.isBlank()));
        System.out.println("Groq URL = " + apiUrl);

        if (apiKey == null || apiKey.isBlank()) {
            return fallbackReport();
        }

        List<SessionAnswer> answers =
                answerRepository.findBySessionId(session.getId());

        List<ScaffoldingLog> scaffoldLogs =
                scaffoldingLogRepository.findBySessionId(session.getId());

        String prompt = buildPrompt(session, answers, scaffoldLogs, numericReport);

        try {
            String aiContent = callGroq(prompt);
            aiContent = cleanJson(aiContent);

            return objectMapper.readValue(
                    aiContent,
                    new TypeReference<Map<String, Object>>() {}
            );

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("AI report generation failed: " + e.getMessage());
            return fallbackReport();
        }
    }

    private String buildPrompt(AssessmentSession session,
                               List<SessionAnswer> answers,
                               List<ScaffoldingLog> scaffoldLogs,
                               Map<String, Object> numericReport) {

        StringBuilder answerData = new StringBuilder();

        for (SessionAnswer a : answers) {
            answerData.append("""
                    
                    Question: %s
                    Topic: %s
                    Skill: %s
                    Difficulty: %s
                    Student Answer: %s
                    Correct Answer: %s
                    Was Correct: %s
                    Time Taken: %s seconds
                    """.formatted(
                    a.getQuestion() != null ? a.getQuestion().getQuestionText() : "Unknown",
                    a.getTopic(),
                    a.getSkillTag(),
                    a.getDifficulty(),
                    a.getStudentAnswer(),
                    a.getQuestion() != null ? a.getQuestion().getCorrectAnswer() : "Unknown",
                    a.getIsCorrect(),
                    a.getTimeTakenSeconds()
            ));
        }

        StringBuilder scaffoldData = new StringBuilder();

        for (ScaffoldingLog log : scaffoldLogs) {
            scaffoldData.append("""
                    
                    Original Question: %s
                    Scaffold/Diagnostic Data: %s
                    Student Scaffold Answer: %s
                    Scaffold Correct: %s
                    Diagnostic Step: %s
                    """.formatted(
                    log.getOriginalQuestion() != null
                            ? log.getOriginalQuestion().getQuestionText()
                            : "Unknown",
                    log.getGeneratedQuestion(),
                    log.getStudentAnswer(),
                    log.getIsCorrect(),
                    log.getAttemptNumber()
            ));
        }

        String numericJson;
        try {
            numericJson = objectMapper.writeValueAsString(numericReport);
        } catch (Exception e) {
            numericJson = "{}";
        }

        return """
                You are an expert educational assessment analyst for children.

                Analyze the student's assessment using ONLY the given evidence:
                - answer data
                - scaffold/diagnostic data
                - numeric performance report

                Do NOT give generic feedback.
                Do NOT invent misconceptions.
                Only mention a weakness if the answer/scaffold evidence supports it.
                If the student answered correctly without scaffolding, mention independent understanding.
                If the student needed scaffolding, explain what kind of support was needed.
                Keep the language teacher-friendly and parent-friendly.

                Return ONLY valid JSON.
                Do not include markdown.
                Do not write anything outside JSON.

                Student Name:
                %s

                Session ID:
                %s

                Assessment Status:
                %s

                Numeric Report:
                %s

                Answer Data:
                %s

                Scaffold / Diagnostic Data:
                %s

                Return exactly this JSON structure:

                {
                  "aiSummary": "",
                  "studentLevel": "",
                  "confidence": "",
                  "thinkingPattern": "",
                  "strengths": [],
                  "weaknesses": [],
                  "diagnosedMisconceptions": [],
                  "scaffoldingAnalysis": "",
                  "teacherNote": "",
                  "parentNote": "",
                  "recommendations": [],
                  "nextSteps": []
                }
                """.formatted(
                session.getStudent().getName(),
                session.getId(),
                session.getStatus().name(),
                numericJson,
                answerData,
                scaffoldData
        );
    }

    private String callGroq(String prompt) throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You generate educational assessment reports. Return only valid JSON.");

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(systemMessage, userMessage));
        body.put("temperature", 0.25);
        body.put("max_tokens", 1200);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API failed: " + response.getBody());
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        return root.path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
    }

    private String cleanJson(String content) {
        return content
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private Map<String, Object> fallbackReport() {
        Map<String, Object> fallback = new LinkedHashMap<>();

        fallback.put("aiSummary", "AI report could not be generated at this time.");
        fallback.put("studentLevel", "UNKNOWN");
        fallback.put("confidence", "LOW");
        fallback.put("thinkingPattern", "");
        fallback.put("strengths", List.of());
        fallback.put("weaknesses", List.of());
        fallback.put("diagnosedMisconceptions", List.of());
        fallback.put("scaffoldingAnalysis", "");
        fallback.put("teacherNote", "Please review the student's answers manually.");
        fallback.put("parentNote", "");
        fallback.put("recommendations", List.of());
        fallback.put("nextSteps", List.of());

        return fallback;
    }
}