package io.example.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenAiClient {
  static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

  final String openaiModel;
  final String openaiApiKey;
  final HttpClient client;
  final ObjectMapper objectMapper;
  final String systemPrompt;

  public OpenAiClient(String systemPromptPath) {
    this(systemPromptPath, "gpt-4o-mini");
  }

  public OpenAiClient(String systemPromptPath, String openaiModel) {
    this.openaiModel = openaiModel;

    openaiApiKey = System.getenv("OPENAI_API_KEY");
    if (openaiApiKey == null || openaiApiKey.isEmpty()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
    }
    client = HttpClient.newHttpClient();
    objectMapper = new ObjectMapper();

    try {
      // Read system prompt from resources folder
      var inputStream = getClass().getResourceAsStream(systemPromptPath);
      if (inputStream == null) {
        throw new IOException("Could not find system prompt file: " + systemPromptPath);
      }
      systemPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("\"", "\\\"")
          .replaceAll("\n", " ");
    } catch (IOException e) {
      log.error("Failed to read system prompt", e);
      throw new IllegalStateException("Failed to read system prompt", e);
    }
  }

  public String chat(String userMessage) throws IOException, InterruptedException {
    log.info("User prompt: {}", userMessage);

    var request = new OpenAiRequest(
        openaiModel,
        List.of(
            new OpenAiRequest.Message("system", systemPrompt),
            new OpenAiRequest.Message("user", userMessage)));

    var jsonRequest = objectMapper.writeValueAsString(request);

    var httpRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
        .header("Authorization", "Bearer " + openaiApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonRequest, StandardCharsets.UTF_8))
        .build();

    var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      log.error("LLM response failure, status code: {}\n{}", response.statusCode(), response.body());
      throw new IOException("LLM response failure, status code: " + response.statusCode());
    }

    var responseBody = response.body();
    log.info("LLM response status code: {}\n{}", response.statusCode(), responseBody);

    var openAIResponse = objectMapper.readValue(responseBody, OpenAiResponse.class);
    log.info("LLM response: {}", openAIResponse.choices().get(0).message().content());

    if (openAIResponse.choices() != null && !openAIResponse.choices().isEmpty()) {
      return openAIResponse.choices().get(0).message().content();
    }

    throw new IOException("No response from LLM");
  }

  public record OpenAiRequest(
      String model,
      List<Message> messages) {

    public record Message(
        String role,
        String content,
        String refusal,
        List<String> annotations) {
      // Constructor for common use case with just role and content
      public Message(String role, String content) {
        this(role, content, null, null);
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiResponse(
      String id,
      String object,
      Long created,
      String model,
      List<Choice> choices,
      Usage usage,
      @JsonProperty("service_tier") String serviceTier,
      @JsonProperty("system_fingerprint") String systemFingerprint) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        Integer index,
        Message message,
        Object logprobs,
        @JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        String role,
        String content,
        Object refusal,
        Object annotations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens,
        @JsonProperty("prompt_tokens_details") Object promptTokensDetails,
        @JsonProperty("completion_tokens_details") Object completionTokensDetails) {}
  }
}