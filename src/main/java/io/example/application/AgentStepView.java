package io.example.application;

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
        WHERE sequenceId = :sequenceId
        ORDER BY stepNumber ASC
      """)
  public QueryEffect<AgentSteps> getSequenceAgentSteps(String sequenceId) {
    return queryResult();
  }

  @Query(value = """
      SELECT *
        FROM agent_step_view
        WHERE userSessionId = :userSessionId
        AND status != 'consumed'
      """, streamUpdates = true)
  public QueryStreamEffect<AgentStepRow> getActiveAgentSteps(String userSessionId) {
    return queryStreamResult();
  }

  @Consume.FromEventSourcedEntity(AgentStepEntity.class)
  public static class AgentStepBySequence extends TableUpdater<AgentStepRow> {

    public Effect<AgentStepRow> onEvent(AgentStep.Event event) {
      log.info("Event: {}", event);

      return switch (event) {
        case AgentStep.Event.StepCreated e -> effects().updateRow(onEvent(e));
        case AgentStep.Event.StepProcessed e -> effects().updateRow(onEvent(e));
        case AgentStep.Event.StepConsumed e -> effects().updateRow(onEvent(e));
        default -> effects().ignore();
      };
    }

    AgentStepRow onEvent(AgentStep.Event.StepCreated event) {
      return new AgentStepRow(
          event.id(),
          event.sequenceId(),
          event.stepNumber(),
          event.status().toString(),
          event.llmPrompt(),
          "",
          event.viewport(),
          event.userSessionId());
    }

    AgentStepRow onEvent(AgentStep.Event.StepProcessed event) {
      return new AgentStepRow(
          rowState().id(),
          rowState().sequenceId(),
          rowState().stepNumber(),
          event.status().toString(),
          rowState().llmPrompt(),
          event.llmResponse(),
          event.viewport(),
          rowState().userSessionId());
    }

    AgentStepRow onEvent(AgentStep.Event.StepConsumed event) {
      return new AgentStepRow(
          rowState().id(),
          rowState().sequenceId(),
          rowState().stepNumber(),
          event.status().toString(),
          rowState().llmPrompt(),
          rowState().llmResponse(),
          rowState().viewport(),
          rowState().userSessionId());
    }
  }

  public record AgentStepRow(
      String id,
      String sequenceId,
      int stepNumber,
      String status,
      String llmPrompt,
      String llmResponse,
      ViewPort viewport,
      String userSessionId) {}

  public record AgentSteps(List<AgentStepRow> agentSteps) {}
}
