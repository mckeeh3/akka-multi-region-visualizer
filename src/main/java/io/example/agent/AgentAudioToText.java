package io.example.agent;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.javasdk.client.ComponentClient;
import io.example.application.AgentStepEntity;
import io.example.domain.AgentStep;

public class AgentAudioToText {
  final static Logger log = LoggerFactory.getLogger(AgentAudioToText.class);
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
  public AgentAudioToText(ComponentClient componentClient, AgentStep.ViewPort viewport) {
    this.componentClient = componentClient;
    this.viewport = viewport;

    this.openaiApiKey = System.getenv("OPENAI_API_KEY");
    if (this.openaiApiKey == null || this.openaiApiKey.isEmpty()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
    }

    this.client = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Transcribes the audio to text.
   *
   * @param contentType The HTTP content type of the request
   * @param audioInput  The input stream containing the audio data
   * @return The transcribed text
   * @throws IOException          If an I/O error occurs
   * @throws InterruptedException If the thread is interrupted
   */
  public String transcribeAudio(String contentType, InputStream audioInput, String userSessionId) throws IOException, InterruptedException {
    // Parse the HTTP multipart content data and extract the audio data
    var parser = new MultipartFormDataParser(contentType, audioInput);
    try {
      parser.parse();
    } catch (IOException e) {
      log.error("Failed to parse multipart form data", e);
      throw new AudioToTextException("Failed to parse multipart form data");
    }

    var audioData = parser.getFile();
    if (audioData == null) {
      log.error("No audio data found");
      throw new AudioToTextException("No audio data found");
    }
    log.info("Audio data length: {}", audioData.length);

    // Transcribe the audio to text
    try {
      var textFromAudio = transcribeAudio(audioData);
      log.info("Text from audio: {}", textFromAudio);

      var llmPrompt = "Transcribe the user's audio to text";
      var llmNextPrompt = textFromAudio;
      var command = AgentStep.Command.CreateStep.ofFirstStep(llmPrompt, llmNextPrompt, viewport, userSessionId);

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);

      return command.sequenceId();
    } catch (IOException | InterruptedException e) {
      log.error("Failed to transcribe audio", e);
      throw new AudioToTextException("Failed to transcribe audio", e);
    }
  }

  static String transcribeAudio(byte[] audioData) throws IOException, InterruptedException {
    return new AudioToTextTranscription().transcribeAudio(audioData);
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
   * @return A CompletionStage that completes with the sequence ID when processing starts
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
          var agent = new AgentAudioToText(componentClient, viewport);
          var sequenceId = agent.transcribeAudio(contentType, audioInput, userSessionId);
          future.complete(sequenceId);
        } catch (Exception e) {
          log.error("Error processing audio in virtual thread", e);
          future.completeExceptionally(e);
        }
        return null;
      });
    }

    return future;
  }

  public class AudioToTextException extends RuntimeException {
    public AudioToTextException(String message) {
      super(message);
    }

    public AudioToTextException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
