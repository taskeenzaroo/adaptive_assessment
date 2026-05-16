package com.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ScaffoldingService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generateScaffold(
            String questionText,
            String topic,
            String skillTag,
            int difficulty,
            int scaffoldAttempt,
            String previousScaffold
    ) {

        try {
            String prompt = buildPrompt(
                    questionText,
                    topic,
                    skillTag,
                    difficulty,
                    scaffoldAttempt,
                    previousScaffold
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(message));
            body.put("temperature", 0.3);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            String content = root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            content = cleanJson(content);

            JsonNode scaffoldJson = objectMapper.readTree(content);

            Map<String, Object> result = new HashMap<>();

            result.put("questionText", scaffoldJson.path("questionText").asText());
            result.put("optionA", scaffoldJson.path("optionA").asText());
            result.put("optionB", scaffoldJson.path("optionB").asText());
            result.put("optionC", scaffoldJson.path("optionC").asText());
            result.put("optionD", scaffoldJson.path("optionD").asText());
            result.put("correctAnswer", scaffoldJson.path("correctAnswer").asText());
            result.put("correctValue", scaffoldJson.path("correctValue").asText());
            result.put("explanation", scaffoldJson.path("explanation").asText());
            result.put("diagnosedMisconception", scaffoldJson.path("diagnosedMisconception").asText());
            result.put("diagnosticFocus", scaffoldJson.path("diagnosticFocus").asText());
            result.put("suspectedWeakness", scaffoldJson.path("suspectedWeakness").asText());
            result.put("confidence", scaffoldJson.path("confidence").asText());

            return result;

        } catch (Exception e) {
            System.out.println("Groq scaffolding failed: " + e.getMessage());
            return fallbackScaffold(topic, skillTag, scaffoldAttempt);
        }
    }

    private String buildPrompt(
            String questionText,
            String topic,
            String skillTag,
            int difficulty,
            int scaffoldAttempt,
            String previousScaffold
    ) {

        return """
                You are an AI tutor generating scaffolded diagnostic MCQ questions for children.

                Original question:
                %s

                Topic:
                %s

                Skill tag:
                %s

                Difficulty:
                %d

                Scaffold attempt:
                %d

                Previous scaffold:
                %s

                Your job:
                - Generate ONE scaffolded MCQ.
                - Do NOT just make the question easier.
                - Diagnose WHY the student may be struggling.
                - Focus on misconception, concept gap, calculation gap, visualization issue, or procedural confusion.
                - Use child-friendly wording.
                - Use simple visual or concrete examples when helpful.
                - Keep options short.
                - Return JSON only.
                - Do not include markdown.
                - Do not include explanation outside JSON.

                If scaffoldAttempt is 1:
                - Make a simpler diagnostic question on the same core idea.

                If scaffoldAttempt is 2:
                - Make it much easier.
                - Check the most foundational understanding.

                Return exactly this JSON format:

                {
                  "questionText": "...",
                  "optionA": "...",
                  "optionB": "...",
                  "optionC": "...",
                  "optionD": "...",
                  "correctAnswer": "A",
                  "correctValue": "...",
                  "explanation": "...",
                  "diagnosedMisconception": "...",
                  "diagnosticFocus": "...",
                  "suspectedWeakness": "...",
                  "confidence": "medium"
                }
                """.formatted(
                questionText,
                topic,
                skillTag,
                difficulty,
                scaffoldAttempt,
                previousScaffold == null ? "None" : previousScaffold
        );
    }

    private String cleanJson(String content) {
        return content
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private Map<String, Object> fallbackScaffold(
            String topic,
            String skillTag,
            int scaffoldAttempt
    ) {

        Map<String, Object> fallback = new HashMap<>();

        if (scaffoldAttempt == 1) {
            fallback.put("questionText", "Let's try this in a simpler way. What should we understand first in this topic?");
            fallback.put("optionA", "The meaning of the concept");
            fallback.put("optionB", "Only the final answer");
            fallback.put("optionC", "Guessing the option");
            fallback.put("optionD", "Skipping the steps");
            fallback.put("correctAnswer", "A");
            fallback.put("correctValue", "The meaning of the concept");
            fallback.put("explanation", "Understanding the concept first helps solve the full question.");
            fallback.put("diagnosedMisconception", "Student may not understand the base concept.");
            fallback.put("diagnosticFocus", "Concept understanding");
            fallback.put("suspectedWeakness", "concept gap");
            fallback.put("confidence", "low");
        } else {
            fallback.put("questionText", "Which is the best first step when solving a new question?");
            fallback.put("optionA", "Read carefully and identify what is asked");
            fallback.put("optionB", "Choose any answer quickly");
            fallback.put("optionC", "Ignore the question");
            fallback.put("optionD", "Look only at the options");
            fallback.put("correctAnswer", "A");
            fallback.put("correctValue", "Read carefully and identify what is asked");
            fallback.put("explanation", "Reading carefully helps us understand what to solve.");
            fallback.put("diagnosedMisconception", "Student may be rushing without understanding the question.");
            fallback.put("diagnosticFocus", "Foundational problem understanding");
            fallback.put("suspectedWeakness", "procedural confusion");
            fallback.put("confidence", "low");
        }

        return fallback;
    }


    private boolean isValidScaffold(Map<String, Object> q) {
        return q != null
                && q.containsKey("questionText")
                && q.containsKey("optionA")
                && q.containsKey("optionB")
                && q.containsKey("optionC")
                && q.containsKey("optionD")
                && q.containsKey("correctAnswer");
    }


    private Map<String, Object> getFallback() {
        return Map.of(
                "questionText", "A pizza is cut into 4 equal slices. What does 1 slice show?",
                "optionA", "A whole pizza",
                "optionB", "A part of a whole",
                "optionC", "Four pizzas",
                "optionD", "No pizza",
                "correctAnswer", "B",
                "correctValue", "A part of a whole",
                "explanation", "A fraction shows equal parts of one whole.",
                "diagnosedMisconception", "Student may not understand that fractions represent equal parts of a whole."
        );
    }
}