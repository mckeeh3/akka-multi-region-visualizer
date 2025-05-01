package io.example.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface Sensor {

  public enum Status {
    inactive,
    red,
    green,
    blue,
    orange
  }

  public record State(
      String id,
      Status status,
      Instant createdAt,
      Instant updatedAt,
      Instant clientAt,
      Instant endpointAt) {

    public static State empty() {
      return new State("", Status.inactive, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
    }

    public boolean isEmpty() {
      return this.id.isEmpty();
    }

    public Optional<Sensor.Event> onCommand(Command.UpdateStatus command) {
      if (isEmpty() && command.status().equals("default")) {
        return Optional.empty();
      }
      return Optional.of(new Event.StatusUpdated(
          command.id,
          command.status,
          isEmpty() ? Instant.now() : createdAt,
          Instant.now(),
          command.clientAt,
          command.endpointAt));
    }

    public List<Sensor.Event> onCommand(Command.SpanStatus command) {
      if (isEmpty() || status.equals("default")) {
        return List.of();
      }
      if (status.equals(command.status())) {
        return List.of();
      }
      if (!insideRadius(command.id, command.centerX, command.centerY, command.radius)) {
        return List.of();
      }

      var statusUpdatedEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          isEmpty() ? Instant.now() : createdAt,
          Instant.now(),
          command.clientAt,
          command.endpointAt);

      var neighborSpanStatusUpdatedEvents = neighborIds(command.id).stream()
          .map(id -> new Event.SpanStatusUpdated(
              id,
              command.status,
              command.clientAt,
              command.endpointAt,
              command.centerX,
              command.centerY,
              command.radius))
          .toList();

      return Stream.<Event>concat(Stream.of(statusUpdatedEvent), neighborSpanStatusUpdatedEvents.stream()).toList();
    }

    public List<Sensor.Event> onCommand(Command.FillStatus command) {
      if (!isEmpty() && !status.equals("default")) {
        return List.of();
      }
      if (!insideRadius(command.id, command.centerX, command.centerY, command.radius)) {
        return List.of();
      }

      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          isEmpty() ? Instant.now() : createdAt,
          Instant.now(),
          command.clientAt,
          command.endpointAt);

      var neighborFillEvents = neighborIds(command.id).stream()
          .map(id -> new Event.FillStatusUpdated(
              id,
              command.status,
              command.clientAt,
              command.endpointAt,
              command.centerX,
              command.centerY,
              command.radius))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborFillEvents.stream()).toList();
    }

    public State onEvent(Event.StatusUpdated event) {
      return new State(
          event.id,
          event.status,
          event.createdAt,
          event.updatedAt,
          event.clientAt,
          event.endpointAt);
    }

    public State onEvent(Event.SpanStatusUpdated event) {
      return this;
    }

    public State onEvent(Event.FillStatusUpdated event) {
      return this;
    }

    boolean insideRadius(String id, int centerX, int centerY, int radius) {
      var rc = id.split("x"); // RxC / YxX
      var x = Integer.parseInt(rc[1]);
      var y = Integer.parseInt(rc[0]);
      return Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2) <= Math.pow(radius, 2);
    }

    List<String> neighborIds(String centerId) {
      var rc = centerId.split("x"); // RxC / YxX
      var x = Integer.parseInt(rc[1]);
      var y = Integer.parseInt(rc[0]);
      return List.of(
          String.format("%dx%d", y - 1, x - 1),
          String.format("%dx%d", y - 1, x),
          String.format("%dx%d", y - 1, x + 1),
          String.format("%dx%d", y, x - 1),
          String.format("%dx%d", y, x + 1),
          String.format("%dx%d", y + 1, x - 1),
          String.format("%dx%d", y + 1, x),
          String.format("%dx%d", y + 1, x + 1));
    }
  }

  public sealed interface Command {
    public record UpdateStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt) implements Command {}

    public record SpanStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius) implements Command {}

    public record FillStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius) implements Command {}
  }

  public sealed interface Event {
    public record StatusUpdated(
        String id,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt) implements Event {}

    public record SpanStatusUpdated(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius) implements Event {}

    public record FillStatusUpdated(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius) implements Event {}
  }
}
