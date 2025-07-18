package io.example.application;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import io.example.agent.GridAgentAudioToText;
import io.example.domain.AgentStep;
import io.example.domain.ViewPort;

@ComponentId("voice-command-workflow")
public class VisualizerWorkflow extends Workflow<VisualizerWorkflow.State> {
  private static final Logger log = LoggerFactory.getLogger(VisualizerWorkflow.class);

  public record State(
      String sessionId,
      ViewPort viewport,
      String audioToText,
      String enhancedPrompt,
      String visualizerResponse,
      Status status) {

    public static State init(String sessionId) {
      return new State(sessionId, null, "", "", "", Status.started);
    }

    public State withViewport(ViewPort viewport) {
      return new State(sessionId, viewport, audioToText, enhancedPrompt, visualizerResponse, Status.started);
    }

    public State withAudioToText(String audioToText) {
      return new State(sessionId, viewport, audioToText, enhancedPrompt, visualizerResponse, Status.audioProcessed);
    }

    public State withEnhancedPrompt(String enhancedPrompt) {
      return new State(sessionId, viewport, audioToText, enhancedPrompt, visualizerResponse, Status.promptEnhanced);
    }

    public State withVisualizerResponse(String visualizerResponse) {
      return new State(sessionId, viewport, audioToText, enhancedPrompt, visualizerResponse, Status.completed);
    }

    public State withError(String error) {
      return new State(sessionId, viewport, audioToText, enhancedPrompt, visualizerResponse, Status.error);
    }
  }

  public enum Status {
    started,
    audioProcessed,
    promptEnhanced,
    completed,
    error
  }

  enum Steps {
    audioToText,
    promptEnhancement,
    recordPromptEnhancementAgent,
    visualizerAgent,
    recordVisualizer,
    error
  }

  public record VoiceCommandRequest(
      String sessionId,
      String contentType,
      byte[] audioData,
      ViewPort viewport) {}

  private final ComponentClient componentClient;

  public VisualizerWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(VoiceCommandRequest request) {
    log.info("Workflow start: {}", request);

    if (currentState() != null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' already started");
    }

    return effects()
        .updateState(State.init(request.sessionId()))
        .transitionTo(Steps.audioToText.name(), request)
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<String> getResult() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else if (currentState().status() != Status.completed) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not completed. Status: " + currentState().status());
    } else {
      return effects().reply(currentState().visualizerResponse());
    }
  }

  public ReadOnlyEffect<String> getAudioToText() {
    if (currentState() == null || currentState().audioToText().isEmpty()) {
      return effects().error("Audio to text not available");
    } else {
      return effects().reply(currentState().audioToText());
    }
  }

  public ReadOnlyEffect<String> getEnhancedPrompt() {
    if (currentState() == null || currentState().enhancedPrompt().isEmpty()) {
      return effects().error("Enhanced prompt not available");
    } else {
      return effects().reply(currentState().enhancedPrompt());
    }
  }

  @Override
  public WorkflowDef<State> definition() {
    return workflow()
        .addStep(audioToTextStep())
        .addStep(promptEnhancementStep())
        .addStep(recordPromptEnhancementStep())
        .addStep(visualizerStep())
        .addStep(recordVisualizerStep())
        .addStep(errorStep())
        .defaultStepRecoverStrategy(maxRetries(3).failoverTo(Steps.error.name()))
        .defaultStepTimeout(Duration.ofMinutes(5));
  }

  private Step audioToTextStep() {
    return step(Steps.audioToText.name())
        .call(VoiceCommandRequest.class, request -> {
          log.info("Workflow step: {}\n_state: {}\n_request: {}", Steps.audioToText.name(), currentState(), request);

          try {
            var audioInput = new ByteArrayInputStream(request.audioData());
            var audioToText = GridAgentAudioToText.convertAudioToText(
                request.viewport(),
                request.contentType(),
                audioInput,
                request.sessionId())
                .toCompletableFuture()
                .get(); // Wait for completion

            var newState = currentState().withViewport(request.viewport()).withAudioToText(audioToText);
            log.info("Workflow step: {}\n_state: {}", Steps.audioToText.name(), newState);
            return currentState().withViewport(request.viewport()).withAudioToText(audioToText);
          } catch (Exception e) {
            log.error("Error processing audio to text", e);
            throw new RuntimeException("Audio processing failed: " + e.getMessage(), e);
          }
        })
        .andThen(State.class, newState -> {
          log.info("Workflow step: {}\n_newState: {}", Steps.audioToText.name(), newState);

          return effects()
              .updateState(newState)
              .transitionTo(Steps.promptEnhancement.name(), newState.audioToText());
        })
        .timeout(Duration.ofMinutes(5));
  }

  private Step promptEnhancementStep() {
    return step(Steps.promptEnhancement.name())
        .call(String.class, audioToText -> {
          log.info("Workflow step: {}\n_state: {}", Steps.promptEnhancement.name(), currentState());

          var request = new PromptEnhancementAgent.EnhancementRequest(currentState().sessionId(), audioToText, currentState().viewport());

          return componentClient.forAgent()
              .inSession(currentState().sessionId())
              .method(PromptEnhancementAgent::enhancePrompt)
              .invoke(request);
        })
        .andThen(String.class, enhancedPrompt -> {
          log.info("Prompt enhancement completed: {}", enhancedPrompt);

          return effects()
              .updateState(currentState().withEnhancedPrompt(enhancedPrompt))
              .transitionTo(Steps.recordPromptEnhancementAgent.name(), enhancedPrompt);
        })
        .timeout(Duration.ofMinutes(5));
  }

  private Step recordPromptEnhancementStep() {
    return step(Steps.recordPromptEnhancementAgent.name())
        .call(String.class, enhancedPrompt -> {
          log.info("Recording prompt enhancement: {}", enhancedPrompt);

          // Create an AgentStep to record the prompt enhancement
          var command = AgentStep.Command.CreateStep.of(
              currentState().sessionId(),
              "PromptEnhancementAgent: " + enhancedPrompt,
              currentState().viewport());

          componentClient.forEventSourcedEntity(command.id())
              .method(AgentStepEntity::createStep)
              .invoke(command);

          return enhancedPrompt;
        })
        .andThen(String.class, enhancedPrompt -> {
          return effects()
              .transitionTo(Steps.visualizerAgent.name(), enhancedPrompt);
        })
        .timeout(Duration.ofMinutes(1));
  }

  private Step visualizerStep() {
    return step(Steps.visualizerAgent.name())
        .call(String.class, enhancedPrompt -> {
          log.info("Workflow step: {}\n_state: {}", Steps.visualizerAgent.name(), currentState());

          var prompt = new VisualizerAgent.Prompt(currentState().sessionId(), enhancedPrompt, currentState().viewport());

          return componentClient
              .forAgent()
              .inSession(currentState().sessionId())
              .method(VisualizerAgent::ask)
              .invoke(prompt);
        })
        .andThen(String.class, response -> {
          log.info("Visualizer agent completed: {}", response);

          return effects()
              .updateState(currentState().withVisualizerResponse(response))
              .transitionTo(Steps.recordVisualizer.name(), response);
        })
        .timeout(Duration.ofMinutes(5));
  }

  private Step recordVisualizerStep() {
    return step(Steps.recordVisualizer.name())
        .call(String.class, response -> {
          log.info("Recording visualizer response: {}", response);

          // Create an AgentStep to record the visualizer response
          var command = AgentStep.Command.CreateStep.of(
              currentState().sessionId(),
              "VisualizerAgent: " + response,
              currentState().viewport());

          componentClient.forEventSourcedEntity(command.id())
              .method(AgentStepEntity::createStep)
              .invoke(command);

          return response;
        })
        .andThen(String.class, response -> {
          return effects()
              .end();
        })
        .timeout(Duration.ofMinutes(1));
  }

  private Step errorStep() {
    return step(Steps.error.name())
        .call(() -> {
          log.error("Workflow failed: {}", currentState());
          return "Workflow failed";
        })
        .andThen(String.class, error -> {
          return effects()
              .updateState(currentState().withError(error))
              .end();
        });
  }
}