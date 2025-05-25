package io.example.api;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.io.ByteArrayOutputStream;

public class AudioToTextTranscription {

  private final String openaiApiKey;
  private final HttpClient client;
  private final ObjectMapper objectMapper;

  public AudioToTextTranscription() {
    // Load API key from environment variable or properties file
    this.openaiApiKey = System.getenv("OPENAI_API_KEY");
    if (this.openaiApiKey == null || this.openaiApiKey.isEmpty()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
    }
    this.client = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  public String transcribeAudio(byte[] audioData) throws IOException, InterruptedException {
    String boundary = UUID.randomUUID().toString();
    ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

    // Add model part
    String modelPart = "--" + boundary + "\r\n" +
        "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
        "whisper-1\r\n";
    requestBody.write(modelPart.getBytes(StandardCharsets.UTF_8));

    // Add file part
    String filePart = "--" + boundary + "\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n" +
        "Content-Type: audio/wav\r\n\r\n";
    requestBody.write(filePart.getBytes(StandardCharsets.UTF_8));
    requestBody.write(audioData);
    requestBody.write("\r\n".getBytes(StandardCharsets.UTF_8));

    // Add closing boundary
    requestBody.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

    var request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .header("Authorization", "Bearer " + openaiApiKey)
        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody.toByteArray()))
        .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("Failed to transcribe audio: " + response.statusCode());
    }

    var jsonNode = objectMapper.readTree(response.body());
    return jsonNode.get("text").asText();
  }
}
