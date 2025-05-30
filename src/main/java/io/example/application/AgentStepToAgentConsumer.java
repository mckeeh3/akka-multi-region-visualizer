package io.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.agent.GridAgentTool;
import io.example.agent.GridAgent;
import io.example.domain.AgentStep;

@ComponentId("agent-step-to-agent-consumer")
@Consume.FromEventSourcedEntity(AgentStepEntity.class)
public class AgentStepToAgentConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public AgentStepToAgentConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(AgentStep.Event event) {
    if (!messageContext().hasLocalOrigin()) {
      log.info("Ignore event: {}\n_HasLocalOrigin: {}, OriginRegion: {}, SelfRegion: {}",
          event,
          messageContext().hasLocalOrigin(),
          messageContext().originRegion(),
          messageContext().selfRegion());
      return effects().ignore();
    }

    return switch (event) {
      case AgentStep.Event.StepCreated e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  Effect onEvent(AgentStep.Event.StepCreated event) {
    log.info("Region: {}, Event: {}", region(), event);

    switch (event.stepNumber()) {
      case 0 -> processStepZero(event);
      case 1 -> processStepOne(event);
      default -> processStepNext(event);
    }
    return effects().done();
  }

  void processStepZero(AgentStep.Event.StepCreated event) {
    log.info("Region: {}, Event: {}", region(), event);
    GridAgent.chat(
        event.llmNextPrompt(),
        event.sequenceId(),
        event.userSessionId(),
        event.viewport(),
        componentClient);
  }

  void processStepOne(AgentStep.Event.StepCreated event) {
    log.info("Region: {}, Event: {}", region(), event);
  }

  void processStepNext(AgentStep.Event.StepCreated event) {
    log.info("Region: {}, Event: {}", region(), event);
    GridAgentTool.chat(
        event.llmPrompt(),
        event.sequenceId(),
        event.stepNumber(),
        event.userSessionId(),
        event.viewport(),
        componentClient,
        region());
  }

  String region() {
    var region = messageContext().selfRegion();
    return region.isEmpty() ? "local-development" : region;
  }
}
