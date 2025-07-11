package io.example.application;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.domain.AgentStep;
import io.example.domain.AgentStep.ViewPort;

@ComponentId("agent-step-view")
public class AgentStepView extends View {
  static final Logger log = LoggerFactory.getLogger(AgentStepView.class);

  @Query("""
      SELECT * AS agentSteps
        FROM agent_step_view
        WHERE sessionId = :sessionId
        ORDER BY createdAt ASC
      """)
  public QueryEffect<AgentSteps> getSequenceAgentSteps(String sessionId) {
    return queryResult();
  }

  @Query(value = """
      SELECT *
        FROM agent_step_view
        WHERE sessionId = :sessionId
        AND status != 'consumed'
      """, streamUpdates = true)
  public QueryStreamEffect<AgentStepRow> getActiveAgentSteps(String sessionId) {
    return queryStreamResult();
  }

  @Consume.FromEventSourcedEntity(AgentStepEntity.class)
  public static class AgentStepBySequence extends TableUpdater<AgentStepRow> {

    public Effect<AgentStepRow> onEvent(AgentStep.Event event) {
      log.info("Event: {}", event);

      return switch (event) {
        case AgentStep.Event.StepCreated e -> effects().updateRow(onEvent(e));
        case AgentStep.Event.StepConsumed e -> effects().updateRow(onEvent(e));
        default -> effects().ignore();
      };
    }

    AgentStepRow onEvent(AgentStep.Event.StepCreated event) {
      return new AgentStepRow(
          event.id(),
          event.sessionId(),
          event.stepId(),
          event.createdAt(),
          event.status().toString(),
          event.message(),
          event.viewport());
    }

    AgentStepRow onEvent(AgentStep.Event.StepConsumed event) {
      return new AgentStepRow(
          rowState().id(),
          rowState().sessionId(),
          rowState().stepId(),
          rowState().createdAt(),
          event.status().toString(),
          rowState().message(),
          rowState().viewport());
    }
  }

  public record AgentStepRow(
      String id,
      String sessionId,
      String stepId,
      Instant createdAt,
      String status,
      String message,
      ViewPort viewport) {}

  public record AgentSteps(List<AgentStepRow> agentSteps) {}
}
