package io.example.domain;

import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import akka.javasdk.annotations.TypeName;

public interface AgentStep {

  public enum Status {
    empty,
    pending,
    processed,
    consumed
  }

  public record State(
      String id,
      String sequenceId,
      int stepNumber,
      String llmPrompt,
      String llmResponse,
      String llmNextPrompt,
      Status status) {

    public static State empty() {
      return new State("", "", 0, "", "", "", Status.empty);
    }

    public boolean isEmpty() {
      return id.isEmpty();
    }

    public static String randomSequenceId() {
      return new Random().ints(5, 0, 36)
          .mapToObj(i -> i < 10 ? String.valueOf(i) : String.valueOf((char) ('a' + i - 10)))
          .collect(Collectors.joining());
    }

    // ============================================================
    // Command handlers
    // ============================================================
    public Optional<Event> onCommand(Command.CreateStep command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(
          new Event.StepCreated(
              command.id,
              command.sequenceId,
              command.stepNumber,
              Status.pending,
              command.llmPrompt,
              command.llmNextPrompt,
              command.viewport,
              command.userSessionId));
    }

    public Optional<Event> onCommand(Command.ProcessedStep command) {
      if (isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(
          new Event.StepProcessed(
              id,
              sequenceId,
              stepNumber,
              Status.processed,
              command.llmResponse,
              command.viewport));
    }

    public Optional<Event> onCommand(Command.ConsumedStep command) {
      if (isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(
          new Event.StepConsumed(
              id,
              sequenceId,
              stepNumber,
              Status.consumed));
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.StepCreated event) {
      return new State(
          event.id,
          event.sequenceId,
          event.stepNumber,
          event.llmPrompt,
          llmResponse,
          event.llmNextPrompt,
          event.status);
    }

    public State onEvent(Event.StepProcessed event) {
      return new State(
          event.id,
          event.sequenceId,
          event.stepNumber,
          llmPrompt,
          event.llmResponse,
          llmNextPrompt,
          event.status);
    }

    public State onEvent(Event.StepConsumed event) {
      return new State(
          event.id,
          event.sequenceId,
          event.stepNumber,
          llmPrompt,
          llmResponse,
          llmNextPrompt,
          event.status);
    }
  }

  // ============================================================
  // Commands
  // ============================================================
  public sealed interface Command {

    public record CreateStep(
        String id,
        String sequenceId,
        int stepNumber,
        String llmPrompt,
        String llmNextPrompt,
        ViewPort viewport,
        String userSessionId) implements Command {

      public static CreateStep of(String sequenceId, int stepNumber, String llmPrompt, String llmNextPrompt, ViewPort viewport, String userSessionId) {
        var id = "%s-%d".formatted(sequenceId, stepNumber);
        return new CreateStep(id, sequenceId, stepNumber, llmPrompt, llmNextPrompt, viewport, userSessionId);
      }

      public static CreateStep ofStepZero(String llmPrompt, String llmNextPrompt, ViewPort viewport, String userSessionId) {
        var sequenceId = State.randomSequenceId();
        return of(sequenceId, 0, llmPrompt, llmNextPrompt, viewport, userSessionId);
      }
    }

    public record ProcessedStep(
        String id,
        String sequenceId,
        int stepNumber,
        String llmResponse,
        ViewPort viewport) implements Command {

      public static ProcessedStep of(String sequenceId, int stepNumber, String llmResponse, ViewPort viewport) {
        var id = "%s-%d".formatted(sequenceId, stepNumber);
        return new ProcessedStep(id, sequenceId, stepNumber, llmResponse, viewport);
      }
    }

    public record ConsumedStep(
        String id,
        String sequenceId,
        int stepNumber) implements Command {

      public static ConsumedStep of(String sequenceId, int stepNumber) {
        var id = "%s-%d".formatted(sequenceId, stepNumber);
        return new ConsumedStep(id, sequenceId, stepNumber);
      }
    }
  }

  // ============================================================
  // Events
  // ============================================================
  public sealed interface Event {

    @TypeName("step-created")
    public record StepCreated(
        String id,
        String sequenceId,
        int stepNumber,
        Status status,
        String llmPrompt,
        String llmNextPrompt,
        ViewPort viewport,
        String userSessionId) implements Event {}

    @TypeName("step-processed")
    public record StepProcessed(
        String id,
        String sequenceId,
        int stepNumber,
        Status status,
        String llmResponse,
        ViewPort viewport) implements Event {}

    @TypeName("step-consumed")
    public record StepConsumed(
        String id,
        String sequenceId,
        int stepNumber,
        Status status) implements Event {}
  }

  public record Location(int row, int col) {}

  public record ViewPort(
      Location topLeft,
      Location bottomRight,
      Location mouse) {

    public static ViewPort of(int topLeftRow, int topLeftCol, int bottomRightRow, int bottomRightCol, int mouseRow, int mouseCol) {
      return new ViewPort(
          new Location(topLeftRow, topLeftCol),
          new Location(bottomRightRow, bottomRightCol),
          new Location(mouseRow, mouseCol));
    }
  }
}
