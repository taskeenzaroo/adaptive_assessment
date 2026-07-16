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

    @Value("${openai.api.key}")
    private String apiKey;


    @Value("${openai.api.url}")
    private String apiUrl;


    @Value("${openai.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateDiagnosticPlan(
            String questionText,
            String topic,
            String skillTag,
            int difficulty
    ) {
        try {
            String prompt = """
                    Your job is to create ONLY TWO diagnostic stages.
                    
                     Stage 1:
                     Identify the most likely prerequisite skill required to solve the original question.
                    
                     Stage 2:
                     If the student still struggles after Stage 1, identify the deeper foundational skill that should be tested.
                    
                     The two stages should progressively diagnose the student's misunderstanding without becoming repetitive.
                    
                     Return ONLY valid JSON.
                    
                     {
                       "steps": [
                         {
                           "stepNumber": 1,
                           "focus": "...",
                           "prerequisite": "...",
                           "diagnosticPurpose": "...",
                           "failureMeaning": "..."
                         },
                         {
                           "stepNumber": 2,
                           "focus": "...",
                           "prerequisite": "...",
                           "diagnosticPurpose": "...",
                           "failureMeaning": "..."
                         }
                       ]
                     }

                    Original Question:
                    %s

                    Topic:
                    %s

                    Skill Tag:
                    %s

                    Difficulty:
                    %d
                    """.formatted(questionText, topic, skillTag, difficulty);

            String content = callOpenAI(prompt);
            return cleanJson(content);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Diagnostic plan generation failed: " + e.getMessage());
            return fallbackDiagnosticPlan();
        }
    }

    public Map<String, Object> generateDiagnosticQuestion(
            String questionText,
            String topic,
            String skillTag,
            int difficulty,
            String diagnosticPlanJson,
            int diagnosticStep,
            String previousDiagnosticQuestion
    ) {
        try {
            String prompt = """
                            You are an expert mathematics teacher and diagnostic assessment specialist for children.
                    
                                            A student answered the original mathematics question incorrectly.
                    
                                            Your task is to generate ONE diagnostic multiple-choice question that identifies the student's learning gap.
                    
                                            The diagnostic question should help determine WHY the student answered incorrectly, not simply whether they can answer another similar question.
                    
                                            =========================
                                            IMPORTANT OBJECTIVE
                                            =========================
                    
                                            The question MUST assess ONLY ONE prerequisite mathematical skill.
                    
                                            The question should require fewer reasoning steps than the original question while remaining mathematically meaningful.
                    
                                            The question should look like one from a real Grade 5–8 mathematics workbook.
                    
                                            =========================
                                            STRICT RULES
                                            =========================
                    
                                            1. Generate exactly ONE multiple-choice question.
                                            2. Test ONLY the current diagnostic step.
                                            3. Keep the same topic as the original question.
                                            4. Make the question easier than the original.
                                            5. Reduce cognitive load by testing one prerequisite skill.
                                            6. Never repeat the original question.
                                            7. Never reuse the same numbers from the original question.
                                            8. Never repeat any previous scaffold question.
                                            9. The student must solve a mathematical problem.
                                            10. Exactly one option must be correct.
                                            11. The remaining three options must represent realistic student misconceptions.
                                            12. Before returning the JSON, silently solve the problem yourself and verify the correct answer.
                                            13. Return ONLY valid JSON.
                                            14. Do NOT include markdown.
                                            15. Do NOT explain anything outside JSON.
                    
                                            =========================
                                            DO NOT GENERATE
                                            =========================
                    
                                            Do NOT ask:
                    
                                            - What is a numerator?
                                            - What is a denominator?
                                            - Define equivalent fractions.
                                            - Explain mixed fractions.
                                            - Describe the concept.
                                            - Identify the definition.
                                            - Fill in theoretical statements.
                    
                                            Never generate vocabulary or definition questions.
                    
                                            =========================
                                            GOOD QUESTION TYPES
                                            =========================
                    
                                            Fractions
                                            - Add two fractions with the same denominator.
                                            - Convert an improper fraction into a mixed fraction.
                                            - Compare two fractions.
                                            - Identify an equivalent fraction.
                                            - Order fractions.
                                            - Simplify a fraction.
                                       
                    
                                            The student should always CALCULATE, CONVERT, COMPARE, SIMPLIFY, ESTIMATE, or APPLY a mathematical operation.
                    
                                            =========================
                                            DIAGNOSTIC STEP GUIDELINES
                                            =========================
                    
                                            If the diagnostic step tests CONCEPT:
                                            Generate a very simple mathematical question that checks conceptual understanding through solving, not definitions.
                    
                                            Example:
                                            Which fraction is greater?
                    
                                            If the diagnostic step tests PROCEDURE:
                                            Ask for the correct mathematical step.
                    
                                            If the diagnostic step tests CALCULATION:
                                            Generate a smaller numerical calculation.
                    
                                            If the diagnostic step tests APPLICATION:
                                            Generate a simpler real-world or word problem based on the same concept.
                    
                                            =========================
                                            RETURN EXACTLY THIS JSON
                                            =========================
                    
                                            {
                                              "questionText": "",
                                              "optionA": "",
                                              "optionB": "",
                                              "optionC": "",
                                              "optionD": "",
                                              "correctAnswer": "A",
                                              "correctValue": "",
                                              "explanation": "",
                                              "diagnosedMisconception": "",
                                              "diagnosticFocus": "",
                                              "suspectedWeakness": "",
                                              "confidence": "HIGH",
                                              "diagnosticStep": 1,
                                              "prerequisiteTested": "",
                                              "failureMeaning": ""
                                            }
                    
                                            =========================
                                            ORIGINAL QUESTION
                                            =========================
                    
                                            %s
                    
                                            Student's Incorrect Answer:
                    
                                            %s
                    
                                            Topic:
                    
                                            %s
                    
                                            Skill Tag:
                    
                                            %s
                    
                                            Difficulty:
                    
                                            %d
                    
                                            Diagnostic Plan:
                    
                                            %s
                    
                                            Current Diagnostic Step:
                    
                                            %d
                    
                                            Previous Diagnostic Question:
                    
                                            %s
                """.formatted(
                    questionText,
                    topic,
                    skillTag,
                    difficulty,
                    diagnosticPlanJson,
                    diagnosticStep,
                    previousDiagnosticQuestion
            );

            String content = callOpenAI(prompt);
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

    private String callOpenAI(String prompt) throws Exception {
        System.out.println("API KEY EXISTS: " + (apiKey != null));
        System.out.println("API KEY LENGTH: " + apiKey.length());
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

        System.out.println("ACTUAL OpenAI URL = [" + apiUrl + "]");
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

    private String fallbackDiagnosticPlan() {
        return """
                {
                  "steps": [
                    {
                      "stepNumber": 1,
                      "focus": "concept",
                      "prerequisite": "Understand the basic concept needed for the question",
                      "diagnosticPurpose": "Check whether the student understands the meaning of the concept",
                      "failureMeaning": "The student may have a concept gap"
                    },
                    {
                      "stepNumber": 2,
                      "focus": "procedure",
                      "prerequisite": "Know the correct method or steps",
                      "diagnosticPurpose": "Check whether the student knows the procedure",
                      "failureMeaning": "The student may have procedural confusion"
                    },
                    {
                      "stepNumber": 3,
                      "focus": "calculation",
                      "prerequisite": "Carry out the calculation correctly",
                      "diagnosticPurpose": "Check calculation accuracy",
                      "failureMeaning": "The student may have a calculation gap"
                    },
                    {
                      "stepNumber": 4,
                      "focus": "application",
                      "prerequisite": "Apply the concept to the full problem",
                      "diagnosticPurpose": "Check whether the student can apply the idea",
                      "failureMeaning": "The student may struggle with application"
                    }
                  ]
                }
                """;
    }

    private Map<String, Object> fallbackDiagnosticQuestion(int diagnosticStep) {
        Map<String, Object> fallback = new HashMap<>();

        fallback.put("questionText", "What should we do first when solving a new question?");
        fallback.put("optionA", "Read carefully and understand what is asked");
        fallback.put("optionB", "Guess quickly");
        fallback.put("optionC", "Skip the question");
        fallback.put("optionD", "Choose the longest option");
        fallback.put("correctAnswer", "A");
        fallback.put("correctValue", "Read carefully and understand what is asked");
        fallback.put("explanation", "Understanding the question is the first step to solving it.");
        fallback.put("diagnosedMisconception", "Student may be rushing without understanding the problem.");
        fallback.put("diagnosticFocus", "foundational understanding");
        fallback.put("suspectedWeakness", "problem understanding");
        fallback.put("confidence", "low");
        fallback.put("diagnosticStep", diagnosticStep);
        fallback.put("prerequisiteTested", "Understanding the question");
        fallback.put("failureMeaning", "The student may struggle to understand what the question is asking.");

        return fallback;
    }
}