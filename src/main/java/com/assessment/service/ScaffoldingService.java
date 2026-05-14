package com.assessment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Map;

@Service
public class ScaffoldingService {

    @Value("${app.scaffolding.url}")
    private String scaffoldingUrl;

    private final WebClient.Builder webClientBuilder;

    public ScaffoldingService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public String generateScaffold(String questionText, String topic, String skillTag, int difficulty) {
        try {
            WebClient client = webClientBuilder.baseUrl(scaffoldingUrl).build();
            Map<String, Object> body = Map.of(
                "question_text", questionText,
                "topic", topic,
                "skill_tag", skillTag,
                "difficulty", difficulty
            );
            Map response = client.post()
                .uri("/generate-scaffold")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> Mono.empty())
                .block();

            if (response != null && response.containsKey("scaffolded_question")) {
                return (String) response.get("scaffolded_question");
            }
        } catch (Exception e) {
            System.out.println("Scaffolding service unavailable: " + e.getMessage());
        }
        return getFallback(topic, skillTag);
    }

    private String getFallback(String topic, String skillTag) {
        if ("concept".equals(skillTag)) {
            return "Can you explain in your own words what a " + topic.toLowerCase() + " is?";
        } else if ("application".equals(skillTag)) {
            return "Think of a real-life situation where you would use " + topic.toLowerCase() + ". What would it look like?";
        }
        return "Try breaking this problem into smaller steps. What is the first thing you need to find?";
    }
}
