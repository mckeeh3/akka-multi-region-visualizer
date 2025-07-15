package io.example.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import akka.javasdk.annotations.TypeName;

public interface AgentStep {

  public enum Status {
    empty,
    created,
    consumed
  }

  public record State(
      String id,
      String sequenceId,
      String stepId,
      Instant createdAt,
      String message,
      Status status) {

    public static State empty() {
      return new State("", "", "", Instant.now(), "", Status.empty);
    }

    public boolean isEmpty() {
      return id.isEmpty();
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
              command.sessionId,
              command.stepId,
              Instant.now(),
              command.message,
              Status.created,
              command.viewport));
    }

    public Optional<Event> onCommand(Command.ConsumedStep command) {
      if (isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(
          new Event.StepConsumed(
              id,
              Status.consumed));
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.StepCreated event) {
      return new State(
          event.id,
          event.sessionId,
          event.stepId,
          event.createdAt,
          event.message,
          event.status);
    }

    public State onEvent(Event.StepConsumed event) {
      return new State(
          event.id,
          sequenceId,
          stepId,
          createdAt,
          message,
          event.status);
    }

    public static String randomSequenceId() {
      return new Random().ints(5, 0, 36)
          .mapToObj(i -> i < 10 ? String.valueOf(i) : String.valueOf((char) ('a' + i - 10)))
          .collect(Collectors.joining());
    }

    public static String randomStepId() {
      return new Random().ints(6, 0, 36)
          .mapToObj(i -> i < 10 ? String.valueOf(i) : String.valueOf((char) ('a' + i - 10)))
          .collect(Collectors.joining());
    }
  }

  // ============================================================
  // Commands
  // ============================================================
  public sealed interface Command {

    public record CreateStep(
        String id,
        String sessionId,
        String stepId,
        Instant createdAt,
        String message,
        ViewPort viewport) implements Command {

      public static CreateStep of(String sessionId, String message, ViewPort viewport) {
        var stepId = State.randomStepId();
        var id = "%s-%s".formatted(sessionId, stepId);
        var createdAt = Instant.now();
        return new CreateStep(id, sessionId, stepId, createdAt, message, viewport);
      }
    }

    public record ConsumedStep(
        String id,
        Status status) implements Command {}
  }

  // ============================================================
  // Events
  // ============================================================
  public sealed interface Event {

    @TypeName("step-created")
    public record StepCreated(
        String id,
        String sessionId,
        String stepId,
        Instant createdAt,
        String message,
        Status status,
        ViewPort viewport) implements Event {}

    @TypeName("step-consumed")
    public record StepConsumed(
        String id,
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
