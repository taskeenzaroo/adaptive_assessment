package com.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ScaffoldingService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScaffoldingService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Map<String, Object> generateScaffold(String questionText,
                                                String topic,
                                                String skillTag,
                                                int difficulty,
                                                int scaffoldAttempt,
                                                String previousScaffold) {
        try {
                    String prompt = String.format("""
        You are an expert AI diagnostic math tutor for primary school students.
        
        You are part of an adaptive diagnostic assessment system.
        
        A student answered a math question incorrectly.
        
        Your job is NOT simply to generate an easier question.
        
        Your real goal is to identify WHY the student is struggling.
        
        The scaffolded questions must help diagnose:
        - whether the student lacks conceptual understanding
        - whether the student struggles with calculation
        - whether the student misunderstands representation
        - whether the student cannot visualize the problem
        - whether the student has procedural confusion
        - whether the student understands the relationship between parts and wholes
        - whether the student can apply the concept in context
        
        The system uses scaffolded questions to rule out possible misunderstandings one by one.
        
        IMPORTANT PEDAGOGICAL RULES:
        - Ask follow-up questions strategically.
        - Each scaffold should isolate ONE possible weakness.
        - Do NOT test multiple skills at once.
        - Reduce cognitive load.
        - Keep wording extremely simple and natural.
        - The question should sound like something a good primary teacher would ask.
        - Avoid long storytelling.
        - Avoid unnecessary details.
        - Use visual and concrete examples when helpful.
        - Use child-friendly contexts like pizza, cake, apples, blocks, chocolates, toys, sharing, groups, slices, etc.
        - The goal is diagnosis, not difficulty for its own sake.
        
        VERY IMPORTANT:
        The scaffolded question should help determine WHAT the student understands and WHAT they do not understand.
        
        For example:
        - If the original question is calculation-heavy, the scaffold may test whether the concept itself is understood.
        - If the original question is conceptual, the scaffold may test whether the student can visualize the idea.
        - If the student struggles with improper fractions, the scaffold may test whether they understand grouping into wholes.
        - If the student struggles with mixed fractions, the scaffold may test whether they understand remainder parts.
        
        SCAFFOLD ATTEMPT: %d
        
        Previous Scaffold:
        %s
        
        SCAFFOLDING STRATEGY:
        
        If Scaffold Attempt = 1:
        - Generate a follow-up diagnostic question.
        - Keep the same core concept.
        - Simplify the reasoning slightly.
        - Focus on identifying the likely misunderstanding.
        - Use visual/concrete examples.
        - Keep moderate support.
        - Preserve the same mathematical representation style where possible.
        
        If Scaffold Attempt = 2:
        - Make the question MUCH easier.
        - Focus ONLY on the most foundational missing idea.
        - Use very small numbers.
        - Use a different example from scaffold 1.
        - Do NOT repeat wording or structure.
        - Focus on ruling out misconceptions.
        - Prioritize intuition over calculation.
        - Ask the simplest possible diagnostic question that still reveals understanding.
        
        QUESTION DESIGN RULES:
        - Use short, natural sentences.
        - Keep questions under 25 words if possible.
        - Avoid multi-step wording.
        - Avoid abstract phrasing.
        - Avoid unnecessary text.
        - Questions should feel classroom-natural.
        
        OPTION DESIGN RULES:
        - Exactly 4 MCQ options.
        - One correct answer.
        - Wrong answers must reflect realistic student mistakes.
        - Distractors should reveal misconceptions.
        - Options must match the original answer format.
        - If original answers are mixed fractions, use mixed fractions.
        - If original answers are whole numbers, use whole numbers.
        - Do NOT use sentence-style options.
        - Keep options concise.
        
        DIAGNOSTIC ANALYSIS:
        You must also infer:
        - likely misconception
        - confidence level of the diagnosis
        - what skill the student likely lacks
        - what the scaffold is testing
        
        Original Question:
        %s
        
        Topic:
        %s
        
        Skill Type:
        %s
        
        Original Difficulty:
        %d
        
        Respond ONLY with valid JSON in this exact format:
        {
          "questionText": "diagnostic scaffolded question",
          "optionA": "value",
          "optionB": "value",
          "optionC": "value",
          "optionD": "value",
          "correctAnswer": "A/B/C/D",
          "correctValue": "actual correct answer",
          "explanation": "brief conceptual explanation",
          "diagnosedMisconception": "likely misunderstanding",
          "diagnosticFocus": "what this scaffold is testing",
          "suspectedWeakness": "concept/calculation/representation/application/procedural/etc",
          "confidence": "low/medium/high"
        }
        """,
                            scaffoldAttempt,
                            previousScaffold == null ? "None" : previousScaffold,
                            questionText,
                            topic,
                            skillTag,
                            difficulty
                    );

            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            Map response = webClientBuilder.build()
                    .post()
                    .uri(geminiApiUrl + "?key=" + geminiApiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (response != null) {
                List candidates = (List) response.get("candidates");

                if (candidates != null && !candidates.isEmpty()) {
                    Map candidate = (Map) candidates.get(0);
                    Map content = (Map) candidate.get("content");
                    List parts = (List) content.get("parts");
                    Map part = (Map) parts.get(0);

                    String text = part.get("text").toString()
                            .replace("```json", "")
                            .replace("```", "")
                            .trim();

                    Map<String, Object> scaffold =
                            objectMapper.readValue(text, Map.class);

                    if (isValidScaffold(scaffold)) {
                        return scaffold;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Gemini scaffolding failed: " + e.getMessage());
        }

        return getFallback();
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