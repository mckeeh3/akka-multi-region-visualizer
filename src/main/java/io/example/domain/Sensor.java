package io.example.domain;

import java.time.Instant;
import java.util.Optional;

public interface Sensor {

  public record State(
      String id,
      String status,
      Instant updatedAt) {

    public static State empty() {
      return new State("", "", Instant.EPOCH);
    }

    public boolean isEmpty() {
      return this.id.isEmpty();
    }

    public Optional<Sensor.Event> onCommand(Command.UpdateStatus command) {
      if (isEmpty() && command.status().equals("default")) {
        return Optional.empty();
      }
      return Optional.of(new Event.StatusUpdated(command.id, command.status, command.updatedAt));
    }

    public State onEvent(Event.StatusUpdated event) {
      return new State(event.id, event.status, event.updatedAt);
    }
  }

  public sealed interface Command {
    public record UpdateStatus(String id, String status, Instant updatedAt) implements Command {}
  }

  public sealed interface Event {
    public record StatusUpdated(String id, String status, Instant updatedAt) implements Event {}
  }
}
