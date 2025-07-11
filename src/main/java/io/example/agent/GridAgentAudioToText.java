package io.example.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.javasdk.client.ComponentClient;
import io.example.application.AgentStepEntity;
import io.example.application.VisualizerAgent;
import io.example.domain.AgentStep;

/**
 * The GridAgentAudioToText class serves as the entry point for voice command processing in the Akka Multi-Region
 * Visualizer application. It handles the conversion of audio recordings from the web interface into text that can be
 * processed by subsequent components in the voice command pipeline.
 *
 * <h2>Core Functionality</h2>
 * <ol>
 * <li><b>Audio Transcription</b>: Processes audio data from HTTP requests and transcribes it to text using OpenAI's
 * Whisper API.</li>
 * <li><b>Multipart Form Handling</b>: Parses multipart form data to extract audio content from HTTP requests.</li>
 * <li><b>Agent Step Creation</b>: Initiates the voice command processing pipeline by creating the first AgentStep
 * entity (step zero) with the transcribed text.</li>
 * <li><b>Asynchronous Processing</b>: Manages the audio transcription process asynchronously using virtual threads to
 * ensure responsive user experience.</li>
 * </ol>
 *
 * <h2>Technical Details</h2>
 * <ul>
 * <li>Communicates with OpenAI's Whisper API for high-quality speech-to-text conversion</li>
 * <li>Requires an OpenAI API key to be set as an environment variable</li>
 * <li>Uses HTTP multipart form data parsing to handle audio uploads</li>
 * <li>Creates AgentStep entities to track the processing pipeline</li>
 * <li>Runs in virtual threads to handle potentially long-running API calls efficiently</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 * <li>Receives audio data from the web interface's voice recording feature</li>
 * <li>Works with the ComponentClient to create AgentStep entities</li>
 * <li>Integrates with the AgentStep domain model for tracking the processing pipeline</li>
 * <li>Feeds into the GridAgent which will process the transcribed text</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>
 * This class is typically invoked by the GridCellEndpoint when handling voice command HTTP requests. The resulting
 * transcribed text becomes the input for the next stage in the voice command processing pipeline, where it will be
 * interpreted and converted into specific grid manipulation commands.
 * </p>
 *
 * <h2>Error Handling</h2>
 * <p>
 * The class provides robust error handling for common issues such as missing audio data, API failures, and
 * transcription errors. These are wrapped in AudioToTextException instances with descriptive messages to aid in
 * debugging and user feedback.
 * </p>
 */
public class GridAgentAudioToText {
  final static Logger log = LoggerFactory.getLogger(GridAgentAudioToText.class);
  final ComponentClient componentClient;
  final AgentStep.ViewPort viewport;

  final String openaiApiKey;
  final HttpClient client;
  final ObjectMapper objectMapper;

  /**
   * Creates a new instance of the AgentAudioToText class.
   *
   * This agent is responsible for processing audio input from the Akka Multi-Region Visualizer's voice recording feature.
   * It handles the following tasks:
   *
   * 1. Parses multipart form data to extract audio content from HTTP requests 2. Transcribes the audio to text using
   * OpenAI's Whisper API 3. Creates an AgentStep entity to track the transcription process 4. Stores viewport information
   * to provide context for subsequent LLM processing 5. Manages asynchronous processing through virtual threads
   *
   * The agent acts as the first step in the voice command processing pipeline, converting raw audio into text that can be
   * further processed by language models for grid visualization commands.
   *
   * @param componentClient The Akka component client for interacting with the entity system
   * @param viewport        The current viewport information containing grid position context
   */
  public GridAgentAudioToText(ComponentClient componentClient, AgentStep.ViewPort viewport) {
    this.componentClient = componentClient;
    this.viewport = viewport;

    this.openaiApiKey = System.getenv("OPENAI_API_KEY");
    if (this.openaiApiKey == null || this.openaiApiKey.isEmpty()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
    }

    this.client = java.net.http.HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Transcribes the audio to text.
   *
   * @param contentType The HTTP content type of the request
   * @param audioInput  The input stream containing the audio data
   * @param sessionId   The user session id
   * @return The transcribed text
   * @throws IOException          If an I/O error occurs
   * @throws InterruptedException If the thread is interrupted
   */
  public String transcribeAudio(String contentType, InputStream audioInput, String sessionId) throws IOException, InterruptedException {
    // Parse the HTTP multipart content data and extract the audio data
    var parser = new MultipartFormDataParser(contentType, audioInput);
    try {
      parser.parse();
    } catch (IOException e) {
      log.error("Failed to parse multipart form data", e);
      throw new GridAgentAudioToTextException("Failed to parse multipart form data");
    }

    var audioData = parser.getFile();
    if (audioData == null) {
      log.error("No audio data found");
      throw new GridAgentAudioToTextException("No audio data found");
    }
    log.info("Audio data length: {}", audioData.length);

    // Transcribe the audio to text
    try {
      var audioToText = transcribeAudio(audioData);
      log.info("Audio to text: {}", audioToText);

      // var agentViewPort = new VisualizerAgent.ViewPort(
      // new VisualizerAgent.Location(viewport.topLeft().row(), viewport.topLeft().col()),
      // new VisualizerAgent.Location(viewport.bottomRight().row(), viewport.bottomRight().col()),
      // new VisualizerAgent.Location(viewport.mouse().row(), viewport.mouse().col()));
      // var prompt = new VisualizerAgent.Prompt(sessionId, audioToText, agentViewPort);

      // componentClient.forAgent()
      // .inSession(sessionId)
      // .method(VisualizerAgent::chat)
      // .invoke(prompt);

      // var command = AgentStep.Command.CreateStep.of(sessionId, audioToText, viewport);

      // componentClient.forEventSourcedEntity(command.id())
      // .method(AgentStepEntity::createStep)
      // .invoke(command);

      return audioToText;
    } catch (IOException | InterruptedException e) {
      log.error("Failed to transcribe audio", e);
      throw new GridAgentAudioToTextException("Failed to transcribe audio", e);
    }
  }

  public String transcribeAudio(byte[] audioData) throws IOException, InterruptedException {
    var boundary = UUID.randomUUID().toString();
    var requestBody = new ByteArrayOutputStream();

    // Add model part
    var modelPart = "--" + boundary + "\r\n" +
        "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
        "whisper-1\r\n";
    requestBody.write(modelPart.getBytes(StandardCharsets.UTF_8));

    // Add file part
    var filePart = "--" + boundary + "\r\n" +
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

  /**
   * Runs the agent in a virtual thread to process audio transcription asynchronously.
   *
   * This method initiates the audio processing pipeline by: 1. Creating a virtual thread to handle the audio
   * transcription 2. Processing the audio data to extract text content 3. Creating an AgentStep entity with the
   * transcribed text 4. Returning the sequence ID that uniquely identifies this processing flow
   *
   * The returned sequence ID is crucial as it serves as the identifier for accessing the Server-Sent Events (SSE) stream
   * through the AgentStepView. Clients can use this sequence ID to subscribe to the SSE stream by calling:
   *
   * GET /agent/agent-step-stream/{sequenceId}
   *
   * This allows the client to receive real-time updates as the agent processes the audio, transcribes it, and executes
   * any commands derived from the voice input. The stream will provide updates for all steps in the processing pipeline
   * that share this sequence ID.
   *
   * @param componentClient The Akka component client for entity interactions
   * @param viewport        The current viewport information for context
   * @param contentType     The HTTP content type of the request containing audio data
   * @param audioInput      The input stream containing the raw audio data
   * @param userSessionId   The user's web app session ID
   * @return A CompletionStage that completes with the the transcribed text
   */
  public static CompletionStage<String> convertAudioToText(
      ComponentClient componentClient,
      AgentStep.ViewPort viewport,
      String contentType,
      InputStream audioInput,
      String userSessionId) {

    var future = new CompletableFuture<String>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executor.submit(() -> {
        try {
          var agent = new GridAgentAudioToText(componentClient, viewport);
          var audioToText = agent.transcribeAudio(contentType, audioInput, userSessionId);
          future.complete(audioToText);
        } catch (Exception e) {
          log.error("Error processing audio in virtual thread", e);
          future.completeExceptionally(e);
        }
        return null;
      });
    }

    return future;
  }

  public static class GridAgentAudioToTextException extends RuntimeException {
    public GridAgentAudioToTextException(String message) {
      super(message);
    }

    public GridAgentAudioToTextException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
