package io.example.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import akka.javasdk.annotations.TypeName;

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
      Instant endpointAt,
      String created,
      String updated) {

    public static State empty() {
      return new State("", Status.inactive, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, "", "");
    }

    public boolean isEmpty() {
      return this.id.isEmpty();
    }

    // ============================================================
    // Command.UpdateStatus
    // ============================================================
    public Optional<Sensor.Event> onCommand(Command.UpdateStatus command) {
      if (!isEmpty() && status.equals(command.status)) {
        return Optional.empty();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      return Optional.of(new Event.StatusUpdated(
          command.id,
          command.status,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          created.isEmpty() ? command.region : created,
          command.region));
    }

    // ============================================================
    // Command.SpanStatus
    // ============================================================
    public List<Sensor.Event> onCommand(Command.SpanStatus command) {
      if (isEmpty() || status.equals(Status.inactive)) {
        return List.of();
      }
      if (status.equals(command.status())) {
        return List.of();
      }
      if (!insideRadius(command.id, command.centerX, command.centerY, command.radius)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var statusUpdatedEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          created.isEmpty() ? command.region : created,
          command.region);

      var neighborSpanStatusUpdatedEvents = neighborIds(command.id).stream()
          .map(id -> new Event.SpanToNeighbor(
              id,
              command.status,
              command.clientAt,
              command.endpointAt,
              command.centerX,
              command.centerY,
              command.radius,
              created.isEmpty() ? command.region : created,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(statusUpdatedEvent), neighborSpanStatusUpdatedEvents.stream()).toList();
    }

    // ============================================================
    // Command.FillStatus
    // ============================================================
    public List<Sensor.Event> onCommand(Command.FillStatus command) {
      if (!isEmpty() && !status.equals(Status.inactive)) {
        return List.of();
      }
      if (status.equals(command.status)) {
        return List.of();
      }
      if (!insideRadius(command.id, command.centerX, command.centerY, command.radius)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          created.isEmpty() ? command.region : created,
          command.region);

      var neighborFillEvents = neighborIds(command.id).stream()
          .map(id -> new Event.FillToNeighbor(
              id,
              command.status,
              command.clientAt,
              command.endpointAt,
              command.centerX,
              command.centerY,
              command.radius,
              created.isEmpty() ? command.region : created,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborFillEvents.stream()).toList();
    }

    // ============================================================
    // Command.ClearStatus
    // ============================================================
    public List<Event> onCommand(Command.ClearStatus command) {
      if (isEmpty() || status.equals(Status.inactive)) {
        return List.of();
      }
      if (!status.equals(command.status)) {
        return List.of();
      }

      var newUpdatedAt = Instant.now();
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          Status.inactive,
          createdAt,
          newUpdatedAt,
          clientAt,
          endpointAt,
          created,
          updated);

      var neighborClearEvents = neighborIds(command.id).stream()
          .map(id -> new Event.ClearToNeighbor(id, command.status))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborClearEvents.stream()).toList();
    }

    // ============================================================
    // Command.EraseStatus
    // ============================================================
    public List<Event> onCommand(Command.EraseStatus command) {
      if (isEmpty() || status.equals(Status.inactive)) {
        return List.of();
      }

      var newUpdatedAt = Instant.now();
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          Status.inactive,
          createdAt,
          newUpdatedAt,
          clientAt,
          endpointAt,
          created,
          updated);

      var neighborEraseEvents = neighborIds(command.id).stream()
          .map(id -> new Event.EraseToNeighbor(id))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborEraseEvents.stream()).toList();
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.StatusUpdated event) {
      return new State(
          event.id,
          event.status,
          event.createdAt,
          event.updatedAt,
          event.clientAt,
          event.endpointAt,
          event.created,
          event.updated);
    }

    public State onEvent(Event.SpanToNeighbor event) {
      return this;
    }

    public State onEvent(Event.FillToNeighbor event) {
      return this;
    }

    public State onEvent(Event.ClearToNeighbor event) {
      return this;
    }

    public State onEvent(Event.EraseToNeighbor event) {
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
        Instant endpointAt,
        String region) implements Command {}

    public record SpanStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius,
        String region) implements Command {}

    public record FillStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius,
        String region) implements Command {}

    public record ClearStatus(
        String id,
        Status status) implements Command {}

    public record EraseStatus(
        String id) implements Command {}
  }

  public sealed interface Event {
    @TypeName("status-updated")
    public record StatusUpdated(
        String id,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String created,
        String updated) implements Event {}

    @TypeName("span-to-neighbor")
    public record SpanToNeighbor(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius,
        String created,
        String updated) implements Event {}

    @TypeName("fill-to-neighbor")
    public record FillToNeighbor(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius,
        String created,
        String updated) implements Event {}

    @TypeName("clear-to-neighbor")
    public record ClearToNeighbor(
        String id,
        Status status) implements Event {}

    @TypeName("erase-to-neighbor")
    public record EraseToNeighbor(
        String id) implements Event {}
  }
}
