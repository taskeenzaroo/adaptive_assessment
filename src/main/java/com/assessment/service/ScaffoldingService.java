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

    private String apiUrl = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generateDiagnosticQuestion(
            String questionText,
            String topic,
            String skillTag,
            int difficulty,
            String studentWrongAnswer,
            String correctAnswer,
            int diagnosticStep,
            String previousDiagnosticQuestion,
            String previousStudentAnswer
    ) {
        try {
            String prompt = """
                    You are an expert adaptive math tutor for children.

                    A student answered a math question incorrectly.

                    Your job is NOT to follow fixed steps like concept/procedure/calculation/application.

                    Your job is to analyze the student's wrong answer and generate ONE targeted scaffolded MCQ
                    that directly tests the most likely misconception.

                    MAIN GOAL:
                    Identify the exact mathematical gap and ask a smaller, easier question that targets that gap.

                    Use these inputs:
                    - Original question
                    - Student wrong answer
                    - Correct answer
                    - Topic
                    - Skill tag
                    - Previous scaffold question, if any
                    - Previous student answer, if any

                    If this is diagnosticStep 1:
                    Target the most likely misconception from the original wrong answer.

                    If this is diagnosticStep 2:
                    Use the previous scaffold question and previous student answer to target the remaining weakness.
                    Do not repeat the first scaffold.

                    VERY IMPORTANT:
                    Generate an actual math problem.
                    Do NOT generate theory-only or definition-only questions.

                    The student should solve, compare, convert, calculate, identify a value,
                    or choose the correct next step.

                    DO NOT ask questions like:
                    - What is a numerator?
                    - What is a denominator?
                    - What is an improper fraction?
                    - What does mixed fraction mean?

                    GOOD question types:
                    - Convert 7/4 into a mixed fraction.
                    - Which fraction is greater than 1?
                    - Which fraction is equivalent to 1/2?
                    - What is the LCM of 4 and 6?
                    - Add 2/5 + 1/5.
                    - What should be the next step to solve this fraction problem?

                    RULES:
                    1. Generate only ONE scaffolded MCQ.
                    2. Make it easier than the original question.
                    3. Stay connected to the original question topic.
                    4. Do NOT repeat the original question.
                    5. Do NOT reuse the exact same numbers from the original question.
                    6. Do NOT repeat any previous scaffold question.
                    7. Options must be short math values, fractions, numbers, expressions, or short steps.
                    8. Exactly ONE option must be correct.
                    9. The other three options must be plausible but clearly incorrect.
                    10. correctAnswer must match the correct option letter.
                    11. correctValue must match the correct option text exactly.
                    12. Before returning, silently solve the question and verify the answer.
                    13. Return ONLY valid JSON.
                    14. Do not include markdown.
                    15. Do not explain outside JSON.

                    Return exactly this JSON:

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
                      "confidence": "medium",
                      "diagnosticStep": 1,
                      "prerequisiteTested": "...",
                      "failureMeaning": "..."
                    }

                    Original Question:
                    %s

                    Student Wrong Answer:
                    %s

                    Correct Answer:
                    %s

                    Topic:
                    %s

                    Skill Tag:
                    %s

                    Difficulty:
                    %d

                    Diagnostic Step:
                    %d

                    Previous Scaffold Question:
                    %s

                    Previous Student Answer:
                    %s
                    """.formatted(
                    questionText,
                    studentWrongAnswer,
                    correctAnswer,
                    topic,
                    skillTag,
                    difficulty,
                    diagnosticStep,
                    previousDiagnosticQuestion == null ? "None" : previousDiagnosticQuestion,
                    previousStudentAnswer == null ? "None" : previousStudentAnswer
            );

            String content = callGroq(prompt);
            content = cleanJson(content);

            JsonNode json = objectMapper.readTree(content);

            Map<String, Object> result = new HashMap<>();

            result.put("questionText", json.path("questionText").asText());
            result.put("optionA", json.path("optionA").asText());
            result.put("optionB", json.path("optionB").asText());
            result.put("optionC", json.path("optionC").asText());
            result.put("optionD", json.path("optionD").asText());
            result.put("correctAnswer", json.path("correctAnswer").asText());
            result.put("correctValue", json.path("correctValue").asText());
            result.put("explanation", json.path("explanation").asText());
            result.put("diagnosedMisconception", json.path("diagnosedMisconception").asText());
            result.put("diagnosticFocus", json.path("diagnosticFocus").asText());
            result.put("suspectedWeakness", json.path("suspectedWeakness").asText());
            result.put("confidence", json.path("confidence").asText());
            result.put("diagnosticStep", json.path("diagnosticStep").asInt(diagnosticStep));
            result.put("prerequisiteTested", json.path("prerequisiteTested").asText());
            result.put("failureMeaning", json.path("failureMeaning").asText());

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Diagnostic question failed: " + e.getMessage());
            return fallbackDiagnosticQuestion(diagnosticStep);
        }
    }

    private String callGroq(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("temperature", 0.2);
        body.put("max_tokens", 700);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                request,
                String.class
        );

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

    private Map<String, Object> fallbackDiagnosticQuestion(int diagnosticStep) {
        Map<String, Object> fallback = new HashMap<>();

        fallback.put("questionText", "What should we do first when solving this type of question?");
        fallback.put("optionA", "Find what the question is asking");
        fallback.put("optionB", "Guess the answer");
        fallback.put("optionC", "Choose the biggest number");
        fallback.put("optionD", "Skip the calculation");
        fallback.put("correctAnswer", "A");
        fallback.put("correctValue", "Find what the question is asking");
        fallback.put("explanation", "Before solving, the student must understand what the question wants.");
        fallback.put("diagnosedMisconception", "The student may be rushing without identifying the task.");
        fallback.put("diagnosticFocus", "targeted understanding");
        fallback.put("suspectedWeakness", "problem interpretation");
        fallback.put("confidence", "low");
        fallback.put("diagnosticStep", diagnosticStep);
        fallback.put("prerequisiteTested", "Understanding the question requirement");
        fallback.put("failureMeaning", "The student may not be identifying what the problem is asking.");

        return fallback;
    }
}