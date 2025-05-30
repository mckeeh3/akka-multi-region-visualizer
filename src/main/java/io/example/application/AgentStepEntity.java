package io.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.AgentStep;

@ComponentId("agent-step-entity")
public class AgentStepEntity extends EventSourcedEntity<AgentStep.State, AgentStep.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public AgentStepEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AgentStep.State emptyState() {
    return AgentStep.State.empty();
  }

  public Effect<Done> createStep(AgentStep.Command.CreateStep command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> processedStep(AgentStep.Command.ProcessedStep command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> consumeStep(AgentStep.Command.ConsumedStep command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  @Override
  public AgentStep.State applyEvent(AgentStep.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case AgentStep.Event.StepCreated e -> currentState().onEvent(e);
      case AgentStep.Event.StepProcessed e -> currentState().onEvent(e);
      case AgentStep.Event.StepConsumed e -> currentState().onEvent(e);
    };
  }
}
