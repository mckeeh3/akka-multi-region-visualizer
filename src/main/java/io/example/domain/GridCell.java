package io.example.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import akka.javasdk.annotations.TypeName;

public interface GridCell {

  public enum Status {
    inactive,
    red,
    green,
    blue,
    orange,
    predator
  }

  // ============================================================
  // State
  // ============================================================
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
    public Optional<GridCell.Event> onCommand(Command.UpdateStatus command) {
      if (!isEmpty() && status.equals(command.status)) {
        return Optional.empty();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      return Optional.of(new Event.StatusUpdated(
          command.id,
          command.status,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          newCreated,
          command.region));
    }

    // ============================================================
    // Command.CreatePredator
    // ============================================================
    public List<GridCell.Event> onCommand(Command.CreatePredator command) {
      if (!isEmpty() && status.equals(command.status)) {
        return List.of();
      }

      var newLinger = command.linger - 1;
      var newRange = switch (status) {
        case red -> command.range + 1;
        case orange -> command.range + 2;
        case green -> command.range + 3;
        case blue -> command.range + 4;
        default -> command.range - 1;
      };

      // Predator is dead
      if (command.nextCellId.isEmpty() || newRange <= 0 || newLinger <= 0) {
        var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
        var newUpdatedAt = Instant.now();
        var newCreated = isEmpty() ? command.region : created;
        return List.of(new Event.StatusUpdated(
            command.id,
            Status.inactive,
            newCreatedAt,
            newUpdatedAt,
            command.clientAt,
            command.endpointAt,
            newCreated,
            command.region));
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      var movedCellId = command.nextCellId;
      var newNextCellId = "";
      return List.of(
          new Event.StatusUpdated(
              command.id,
              Status.predator,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.region),
          new Event.PredatorMoved(
              movedCellId,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.range,
              command.linger,
              newNextCellId,
              command.region),
          new Event.PredatorLingered(
              command.id,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.linger,
              command.region));
    }

    // ============================================================
    // Command.MovePredator
    // ============================================================
    public List<GridCell.Event> onCommand(Command.MovePredator command) {
      var newLinger = command.linger - 1;
      var newRange = switch (status) {
        case red -> command.range + 1;
        case orange -> command.range + 2;
        case green -> command.range + 3;
        case blue -> command.range + 4;
        default -> command.range - 1;
      };

      // Predator is dead
      if (command.nextCellId.isEmpty() || newRange <= 0 || newLinger <= 0) {
        var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
        var newUpdatedAt = Instant.now();
        var newCreated = isEmpty() ? command.region : created;
        return List.of(new Event.StatusUpdated(
            command.id,
            Status.inactive,
            newCreatedAt,
            newUpdatedAt,
            command.clientAt,
            command.endpointAt,
            newCreated,
            command.region));
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      var movedCellId = command.nextCellId;
      var newNextCellId = "";
      return List.of(
          new Event.StatusUpdated(
              command.id,
              Status.predator,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.region),
          new Event.PredatorMoved(
              movedCellId,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              newRange,
              command.linger,
              newNextCellId,
              command.region),
          new Event.PredatorLingered(
              command.id,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.linger,
              command.region));
    }

    // ============================================================
    // Command.LingerPredator
    // ============================================================
    public List<GridCell.Event> onCommand(Command.LingerPredator command) {
      if (isEmpty() || !status.equals(Status.predator)) {
        return List.of();
      }

      var newLinger = command.linger - 1;

      // Predator is dead
      if (newLinger <= 0) {
        var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
        var newUpdatedAt = Instant.now();
        var newCreated = isEmpty() ? command.region : created;
        return List.of(new Event.StatusUpdated(
            command.id,
            Status.inactive,
            newCreatedAt,
            newUpdatedAt,
            command.clientAt,
            command.endpointAt,
            newCreated,
            command.region));
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      return List.of(
          new Event.StatusUpdated(
              command.id,
              Status.predator,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.region),
          new Event.PredatorLingered(
              command.id,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              newLinger,
              command.region));
    }

    // ============================================================
    // Command.SpanStatus
    // ============================================================
    public List<GridCell.Event> onCommand(Command.SpanStatus command) {
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
      var newCreated = isEmpty() ? command.region : created;
      var statusUpdatedEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          newCreated,
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
              newCreated,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(statusUpdatedEvent), neighborSpanStatusUpdatedEvents.stream()).toList();
    }

    // ============================================================
    // Command.FillStatus
    // ============================================================
    public List<GridCell.Event> onCommand(Command.FillStatus command) {
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
      var newCreated = isEmpty() ? command.region : created;
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          newCreated,
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
              newCreated,
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

    public State onEvent(Event.PredatorCreated event) {
      return this;
    }

    public State onEvent(Event.PredatorMoved event) {
      return this;
    }

    public State onEvent(Event.PredatorLingered event) {
      return this;
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

    // Radius is limited to min(50, radius)
    static boolean insideRadius(String id, int centerX, int centerY, int radius) {
      var rc = id.split("x"); // RxC / YxX
      var x = Integer.parseInt(rc[1]);
      var y = Integer.parseInt(rc[0]);
      return Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2) <= Math.pow(Math.min(50, radius), 2);
    }

    static List<String> neighborIds(String centerId) {
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

  // ============================================================
  // Commands
  // ============================================================
  public sealed interface Command {
    public record UpdateStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        String region) implements Command {

      public UpdateStatus withRegion(String newRegion) {
        return new UpdateStatus(id, status, clientAt, endpointAt, newRegion);
      }
    }

    public record CreatePredator(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer range,
        Integer linger,
        String nextCellId,
        String region) implements Command {

      public CreatePredator withRegion(String newRegion) {
        return new CreatePredator(id, status, clientAt, endpointAt, range, linger, nextCellId, newRegion);
      }
    }

    public record MovePredator(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer range,
        Integer linger,
        String nextCellId,
        String region) implements Command {

      public MovePredator withRegion(String newRegion) {
        return new MovePredator(id, status, clientAt, endpointAt, range, linger, nextCellId, newRegion);
      }
    }

    public record LingerPredator(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer linger,
        String region) implements Command {

      public LingerPredator withRegion(String newRegion) {
        return new LingerPredator(id, status, clientAt, endpointAt, linger, newRegion);
      }
    }

    public record SpanStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius,
        String region) implements Command {

      public SpanStatus withRegion(String newRegion) {
        return new SpanStatus(id, status, clientAt, endpointAt, centerX, centerY, radius, newRegion);
      }
    }

    public record FillStatus(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer centerX,
        Integer centerY,
        Integer radius,
        String region) implements Command {

      public FillStatus withRegion(String newRegion) {
        return new FillStatus(id, status, clientAt, endpointAt, centerX, centerY, radius, newRegion);
      }
    }

    public record ClearStatus(
        String id,
        Status status) implements Command {

      public ClearStatus withRegion(String newRegion) {
        return new ClearStatus(id, status);
      }
    }

    public record EraseStatus(
        String id) implements Command {

      public EraseStatus withRegion(String newRegion) {
        return new EraseStatus(id);
      }
    }
  }

  // ============================================================
  // Events
  // ============================================================
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

    @TypeName("predator-created")
    public record PredatorCreated(
        String id,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String created,
        Integer range,
        Integer linger,
        String nextCell,
        String updated) implements Event {}

    @TypeName("predator-moved")
    public record PredatorMoved(
        String id,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String created,
        Integer range,
        Integer linger, // Number of update cycles before predator's tail leaves the cell
        String nextCell,
        String updated) implements Event {}

    @TypeName("predator-lingered")
    public record PredatorLingered(
        String id,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String created,
        Integer linger, // Number of update cycles before predator's tail leaves the cell
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
