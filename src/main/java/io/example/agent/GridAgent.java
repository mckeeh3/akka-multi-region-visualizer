package io.example.agent;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import akka.javasdk.client.ComponentClient;
import io.example.application.AgentStepEntity;
import io.example.domain.AgentStep;

public class GridAgent {
  static final Logger log = LoggerFactory.getLogger(GridAgent.class);
  final ComponentClient componentClient;
  final String sequenceId;
  final String userSessionId;
  final AgentStep.ViewPort viewport;
  final ObjectMapper objectMapper;

  public GridAgent(ComponentClient componentClient, String sequenceId, String userSessionId, AgentStep.ViewPort viewport) {
    this.componentClient = componentClient;
    this.sequenceId = sequenceId;
    this.userSessionId = userSessionId;
    this.viewport = viewport;
    objectMapper = new ObjectMapper();
  }

  List<String> chat(String userPrompt) {
    var llmClient = new OpenAiClient("/grid-agent-system-prompt.txt");
    try {
      var response = llmClient.chat(userPrompt);
      log.info("LLM response: {}", response);

      var toolCommands = parseJsonResponse(response);

      IntStream.range(0, toolCommands.size())
          .forEach(step -> {
            var llmPrompt = toolCommands.get(step);
            var llmNextPrompt = step + 1 < toolCommands.size() ? toolCommands.get(step + 1) : "";
            var command = AgentStep.Command.CreateStep.of(sequenceId, step + 2, llmPrompt, llmNextPrompt, viewport, userSessionId);
            componentClient.forEventSourcedEntity(command.id())
                .method(AgentStepEntity::createStep)
                .invoke(command);
          });

      return toolCommands;
    } catch (IOException | InterruptedException e) {
      log.error("Voice command: Failed to get LLM response", e);
      throw new AgentUserPromptException("Failed to get LLM response", e);
    }
  }

  public static class AgentUserPromptException extends RuntimeException {
    public AgentUserPromptException(String message) {
      super(message);
    }

    public AgentUserPromptException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  List<String> parseJsonResponse(String input) {
    // First try code block extraction
    var pattern = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```", Pattern.MULTILINE);
    var matcher = pattern.matcher(input);
    if (matcher.find()) {
      var jsonContent = matcher.group(1).trim();
      try {
        return objectMapper.readValue(jsonContent, new TypeReference<List<String>>() {});
      } catch (IOException e) {
        log.error("Failed to parse JSON block", e);
      }
    }

    // If no code block, try to find raw JSON array
    var rawArrayPattern = Pattern.compile("\\[[^\\[\\]]*(?:\"[^\"]*\"[^\\[\\]]*)*\\]");
    var rawMatcher = rawArrayPattern.matcher(input);
    if (rawMatcher.find()) {
      var jsonContent = rawMatcher.group(0).trim();
      try {
        return objectMapper.readValue(jsonContent, new TypeReference<List<String>>() {});
      } catch (IOException e) {
        log.error("Failed to parse JSON block", e);
      }
    }

    return List.of();
  }

  /**
   * Runs the agent in a virtual thread to process audio transcription asynchronously.
   *
   * This method initiates the user prompt processing pipeline by:
   *
   * <ol>
   * <li>Creating a virtual thread to handle the user prompt</li>
   * <li>Processing the user prompt to extract a list of tool commands</li>
   * <li>Creating an AgentStep entity for each tool command to track the tool command processing</li>
   * <li>Returning the list of tool commands</li>
   * </ol>
   *
   * The tool command entities emit events that are accessible via Server-Sent Events (SSE) stream through the
   * AgentStepView. Clients can use this list of tool commands to subscribe to the SSE stream by calling:
   *
   * GET /agent/agent-step-stream/{sequenceId}
   *
   * This allows the client to receive real-time updates as the agent processes the user prompt, and executes any commands
   * derived from the user prompt. The stream will provide updates for all steps in the processing pipeline that share
   * this sequence ID.
   *
   * @param componentClient The Akka component client for entity interactions
   * @param sequenceId      The sequence ID that uniquely identifies this processing flow
   * @param userSessionId   The user's web app session ID
   * @param userPrompt      The user's prompt
   * @param viewport        The current viewport information for context
   * @return A CompletionStage that completes with the list of tool commands when processing starts
   */
  public static CompletionStage<List<String>> chat(
      String userPrompt,
      String sequenceId,
      String userSessionId,
      AgentStep.ViewPort viewport,
      ComponentClient componentClient) {

    var future = new CompletableFuture<List<String>>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executor.submit(() -> {
        try {
          var agent = new GridAgent(componentClient, sequenceId, userSessionId, viewport);
          var response = agent.chat(userPrompt);
          future.complete(response);
        } catch (Exception e) {
          log.error("Error processing user prompt in virtual thread", e);
          future.completeExceptionally(e);
        }
        return null;
      });
    }

    return future;
  }
}
