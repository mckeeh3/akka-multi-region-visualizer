package io.example.domain;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import akka.javasdk.annotations.TypeName;

/**
 * Represents a cell in a multi-region grid visualization system with event-driven drawing capabilities.
 * 
 * <p>
 * This interface defines the core domain model for grid cells that participate in an organic, event-driven drawing
 * process. When drawing operations are performed, individual cells are modified which triggers events that propagate to
 * neighboring cells, creating a cascading effect until the requested operation is completed.
 * </p>
 * 
 * <h3>Event-Driven Drawing Process</h3>
 * <p>
 * The drawing system operates through a command-event pattern where:
 * </p>
 * <ul>
 * <li><strong>Commands</strong> represent user actions (draw, fill, span, clear, erase)</li>
 * <li><strong>Events</strong> are generated when cells change state and propagate to neighbors</li>
 * <li><strong>Organic Propagation</strong> occurs as events trigger reactions in neighboring cells, creating a ripple
 * effect that continues until the operation completes</li>
 * </ul>
 * 
 * <h3>Drawing Operation Types</h3>
 * <ul>
 * <li><strong>Draw</strong> ({@link Command.DrawShape}): Changes cell color within a shape boundary. Only affects cells
 * that are inside the specified shape and propagates to neighbors.</li>
 * 
 * <li><strong>Fill</strong> ({@link Command.FillCells}): Fills only inactive (empty) cells within a shape. Used when
 * starting with inactive cells to populate empty areas.</li>
 * 
 * <li><strong>Span</strong> ({@link Command.SpanCells}): Spreads color to active cells within a shape. Used when
 * starting with active cells to expand existing colored areas.</li>
 * 
 * <li><strong>Clear</strong> ({@link Command.ClearCells}): Removes cells of a specific color. Only affects cells
 * matching the target status and propagates to neighbors of the same color.</li>
 * 
 * <li><strong>Erase</strong> ({@link Command.EraseCells}): Removes all active cells regardless of color. Clears any
 * colored cell and propagates to all neighboring active cells.</li>
 * </ul>
 * 
 * <h3>Cell States</h3>
 * <p>
 * Cells can be in various states represented by the {@link Status} enum:
 * </p>
 * <ul>
 * <li><code>inactive</code> - Empty cell with no color</li>
 * <li><code>red</code>, <code>green</code>, <code>blue</code>, <code>orange</code> - Colored cells</li>
 * <li><code>predator</code> - Special state for predator entities (see {@link Predator} class)</li>
 * </ul>
 * 
 * <h3>Shape Support</h3>
 * <p>
 * The system supports multiple geometric shapes for drawing operations:
 * </p>
 * <ul>
 * <li><strong>Circle</strong> - Circular areas with radius-based containment</li>
 * <li><strong>Rectangle</strong> - Rectangular areas with width/height boundaries</li>
 * <li><strong>Triangle</strong> - Triangular areas using barycentric coordinate calculations</li>
 * <li><strong>Single Cell</strong> - Individual cell operations</li>
 * </ul>
 * 
 * <h3>Predator System</h3>
 * <p>
 * The system includes a complex predator entity system where predators can move between cells, maintain tails, spawn
 * children, and interact with the grid. Predator behavior is fully described in the {@link Predator} class and related
 * predator commands/events.
 * </p>
 * 
 * <h3>Multi-Region Support</h3>
 * <p>
 * The system supports multi-region operations where cells can be associated with different regions, enabling
 * distributed visualization across multiple areas.
 * </p>
 * 
 * <h3>Rate Limiting</h3>
 * <p>
 * Cells implement a 3-second rate limiting mechanism to prevent excessive state changes and ensure stable visualization
 * behavior.
 * </p>
 * 
 * @see Command for available operations
 * @see Event for state change notifications
 * @see Shape for geometric definitions
 * @see Predator for predator entity behavior
 */
public interface GridCell {

  public enum Status {
    inactive,
    red,
    green,
    blue,
    orange,
    predator,
    custom
  }

  public record Color(int r, int g, int b, double a) {
    public Color(int r, int g, int b) {
      this(r, g, b, 1.0);
    }

    public static Color of(String hexOrNamedColor) {
      return switch (hexOrNamedColor.toLowerCase()) {
        case String s when s.matches("^#[0-9A-Fa-f]{3}$") -> new Color( // matches #RGB
            Integer.parseInt(s.substring(1, 2), 16),
            Integer.parseInt(s.substring(2, 3), 16),
            Integer.parseInt(s.substring(3, 4), 16));
        case String s when s.matches("^#[0-9A-Fa-f]{6}$") -> new Color( // matches #RRGGBB
            Integer.parseInt(s.substring(1, 3), 16),
            Integer.parseInt(s.substring(3, 5), 16),
            Integer.parseInt(s.substring(5, 7), 16));
        case String s when s.matches("^[0-9A-Fa-f]{6}$") -> new Color( // matches RRGGBB
            Integer.parseInt(s.substring(0, 2), 16),
            Integer.parseInt(s.substring(2, 4), 16),
            Integer.parseInt(s.substring(4, 6), 16));
        case "red" -> new Color(138, 15, 48, 1.0); // rgba(138, 15, 48, 1)
        case "green" -> new Color(6, 74, 62, 1.0); // rgba(6, 74, 62, 1)
        case "blue" -> new Color(6, 48, 78, 1.0); // rgba(6, 48, 78, 1)
        case "orange" -> new Color(255, 125, 0, 0.5); // rgba(255, 125, 0, 0.5)
        case "predator" -> new Color(115, 1, 146, 0.5); // rgba(115, 1, 146, 0.5)
        default -> throw new IllegalArgumentException("Invalid color format: " + hexOrNamedColor);
      };
    }

    public static Color of(Status status) {
      return switch (status) {
        case red -> new Color(138, 15, 48, 1.0);
        case green -> new Color(6, 74, 62, 1.0);
        case blue -> new Color(6, 48, 78, 1.0);
        case orange -> new Color(255, 125, 0, 0.5);
        case predator -> new Color(115, 1, 146, 0.5);
        default -> new Color(0, 0, 0, 1.0);
      };
    }

    public String toHex() {
      if (a == 1.0) {
        return String.format("#%02x%02x%02x", r, g, b);
      } else {
        // Convert alpha from 0.0-1.0 to 0-255 and format as 8-digit hex
        int alpha = (int) Math.round(a * 255);
        return String.format("#%02x%02x%02x%02x", r, g, b, alpha);
      }
    }

    public String toRgba() {
      return String.format("rgba(%d, %d, %d, %.1f)", r, g, b, a);
    }
  }

  // ============================================================
  // State
  // ============================================================
  public record State(
      String id,
      Status status,
      Color color,
      Instant createdAt,
      Instant updatedAt,
      Instant clientAt,
      Instant endpointAt,
      String created,
      String updated) {

    public static State empty() {
      return new State("", Status.inactive, Color.of("#000000"), Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, "", "");
    }

    public boolean isEmpty() {
      return this.id.isEmpty();
    }

    // ============================================================
    // Command.CreateShape
    // ============================================================
    public List<Event> onCommand(Command.CreateShape command) {
      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;

      if (command.shape().isSingleCell()) {
        return List.of(new Event.StatusUpdated(
            command.id,
            command.status,
            Color.of(command.status),
            newCreatedAt,
            newUpdatedAt,
            command.clientAt,
            command.endpointAt,
            newCreated,
            command.region));
      }

      // If first cell is inactive, fill the shape, which fills only empty (no color) cells
      // Otherwise, span the shape, which spans only active (has color) cells
      if (status.equals(Status.inactive)) {
        var fillCommand = new Command.FillCells(
            command.id,
            command.status,
            command.clientAt,
            command.endpointAt,
            command.shape(),
            command.region);
        return onCommand(fillCommand);
      } else {
        var spanCommand = new Command.SpanCells(
            command.id,
            command.status,
            command.clientAt,
            command.endpointAt,
            command.shape(),
            command.region);
        return onCommand(spanCommand);
      }
    }

    // ============================================================
    // Command.UpdateStatus
    // ============================================================
    public Optional<Event> onCommand(Command.UpdateCell command) {
      if (!isEmpty() && status.equals(command.status)) {
        return Optional.empty();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;

      return Optional.of(new Event.StatusUpdated(
          command.id,
          command.status,
          Color.of(command.status),
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
    public List<Event> onCommand(Command.CreatePredator command) {
      if (!isEmpty() && status.equals(command.status)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;

      var newRange = switch (status) {
        case red -> command.range + 1;
        case orange -> command.range + 2;
        case green -> command.range + 3;
        case blue -> command.range + 4;
        default -> command.range - 1;
      };

      // Predator is dead
      if (command.nextCellId.isEmpty() || newRange <= 0) {
        return List.of(new Event.StatusUpdated(
            command.id,
            Status.inactive,
            Color.of(Status.inactive),
            newCreatedAt,
            newUpdatedAt,
            command.clientAt,
            command.endpointAt,
            newCreated,
            command.region));
      }

      var movedToCellId = command.nextCellId;
      var newLastCellId = command.id;
      var tail = new LinkedList<String>();
      tail.add(command.id);
      return List.of(
          new Event.StatusUpdated(
              command.id,
              Status.predator,
              Color.of(Status.predator),
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.region),
          new Event.PredatorMoved(
              movedToCellId,
              command.predatorId,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.range,
              newLastCellId,
              tail,
              command.region));
    }

    // ============================================================
    // Command.MovePredator
    // ============================================================
    public List<Event> onCommand(Command.MovePredator command) {
      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      var newRange = switch (status) {
        case red -> command.range + 1;
        case orange -> command.range + 2;
        case green -> command.range + 3;
        case blue -> command.range + 4;
        default -> command.range - 1;
      };

      // Predator is dead, clear head and tail
      if (command.nextCellId.isEmpty() || newRange <= 0) {
        var tailEvents = command.tail.stream()
            .map(id -> new Event.PredatorUpdated(
                id,
                command.predatorId,
                Status.inactive,
                newUpdatedAt,
                command.clientAt,
                command.endpointAt,
                command.region))
            .toList();
        return Stream.concat(
            Stream.<Event>of(new Event.StatusUpdated(
                command.id,
                Status.inactive,
                Color.of(Status.inactive),
                newCreatedAt,
                newUpdatedAt,
                command.clientAt,
                command.endpointAt,
                newCreated,
                command.region)),
            tailEvents.stream()).toList();
      }

      var movedToCellId = command.nextCellId;
      var newLastCellId = command.id;

      var tail = command.tail;
      tail.add(command.id);
      var tailTooLong = tail.size() > 5;
      var tailEndId = tailTooLong ? tail.remove() : "";
      var childMinRange = 1000;

      return List.of(
          Optional.<Event>of(new Event.StatusUpdated(
              command.id,
              Status.predator,
              Color.of(Status.predator),
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              command.region)),
          Optional.<Event>of(new Event.PredatorMoved(
              movedToCellId,
              command.predatorId,
              command.status,
              newCreatedAt,
              newUpdatedAt,
              command.clientAt,
              command.endpointAt,
              newCreated,
              newRange > 2 * childMinRange ? newRange - childMinRange : newRange,
              newLastCellId,
              tail,
              command.region)),
          tailTooLong
              ? Optional.<Event>of(new Event.PredatorUpdated(
                  tailEndId,
                  command.predatorId,
                  Status.inactive,
                  newUpdatedAt,
                  command.clientAt,
                  command.endpointAt,
                  command.region))
              : Optional.<Event>empty(),
          newRange > 2 * childMinRange // Spawn child predator
              ? Optional.<Event>of(new Event.PredatorMoved(
                  movedToCellId,
                  Predator.childId(command.predatorId),
                  command.status,
                  newCreatedAt,
                  newUpdatedAt,
                  command.clientAt,
                  command.endpointAt,
                  newCreated,
                  childMinRange,
                  newLastCellId,
                  tail,
                  command.region))
              : Optional.<Event>empty())
          .stream()
          .flatMap(Optional::stream)
          .toList();
    }

    // ============================================================
    // Command.UpdatePredator
    // ============================================================
    public Optional<Event> onCommand(Command.UpdatePredator command) {
      if (isEmpty() || status.equals(Status.inactive)) {
        return Optional.empty();
      }
      if (!status.equals(Status.predator)) {
        return Optional.empty();
      }

      var newUpdatedAt = Instant.now();
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          Color.of(command.status),
          createdAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          created,
          command.region);

      return Optional.of(updateStatusEvent);
    }

    // ============================================================
    // Command.SpanStatus
    // ============================================================
    public List<Event> onCommand(Command.SpanCells command) {
      if (isEmpty() || status.equals(Status.inactive)) {
        return List.of();
      }
      if (status.equals(command.status())) {
        return List.of();
      }
      if (!insideShape(command.id, command.shape)) {
        return List.of();
      }
      if (isTooSoonToChange(updatedAt)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      var statusUpdatedEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          Color.of(command.status),
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
              command.shape,
              newCreated,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(statusUpdatedEvent), neighborSpanStatusUpdatedEvents.stream()).toList();
    }

    // ============================================================
    // Command.FillStatus
    // ============================================================
    public List<Event> onCommand(Command.FillCells command) {
      if (!isEmpty() && !status.equals(Status.inactive)) {
        return List.of();
      }
      if (status.equals(command.status)) {
        return List.of();
      }
      if (!insideShape(command.id, command.shape)) {
        return List.of();
      }
      if (isTooSoonToChange(updatedAt)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          Color.of(command.status),
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
              command.shape,
              newCreated,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborFillEvents.stream()).toList();
    }

    // ============================================================
    // Command.DrawShape
    // ============================================================
    public List<Event> onCommand(Command.DrawShape command) {
      if (status.equals(command.status)) {
        return List.of();
      }
      if (!insideShape(command.id, command.shape)) {
        return List.of();
      }
      if (isTooSoonToChange(updatedAt)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;

      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          command.color,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          newCreated,
          command.region);

      var neighborDrawEvents = neighborIds(command.id).stream()
          .map(id -> new Event.DrawToNeighbor(
              id,
              command.status,
              command.color,
              command.clientAt,
              command.endpointAt,
              command.shape,
              newCreated,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborDrawEvents.stream()).toList();
    }

    // ============================================================
    // Command.DrawCells
    // ============================================================
    public List<Event> onCommand(Command.DrawCells command) {
      if (status.equals(command.status)) {
        return List.of();
      }
      if (!insideShape(command.id, command.shape)) {
        return List.of();
      }
      if (isTooSoonToChange(updatedAt)) {
        return List.of();
      }

      var newCreatedAt = isEmpty() ? Instant.now() : createdAt;
      var newUpdatedAt = Instant.now();
      var newCreated = isEmpty() ? command.region : created;

      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          command.status,
          command.color,
          newCreatedAt,
          newUpdatedAt,
          command.clientAt,
          command.endpointAt,
          newCreated,
          command.region);

      var neighborDrawEvents = neighborIds(command.id).stream()
          .map(id -> new Event.DrawToNeighbor(
              id,
              command.status,
              command.color,
              command.clientAt,
              command.endpointAt,
              command.shape,
              newCreated,
              command.region))
          .toList();

      return Stream.<Event>concat(Stream.of(updateStatusEvent), neighborDrawEvents.stream()).toList();
    }

    // ============================================================
    // Command.ClearStatus
    // ============================================================
    public List<Event> onCommand(Command.ClearCells command) {
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
          Color.of(Status.inactive),
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
    public List<Event> onCommand(Command.EraseCells command) {
      if (isEmpty() || status.equals(Status.inactive)) {
        return List.of();
      }

      var newUpdatedAt = Instant.now();
      var updateStatusEvent = new Event.StatusUpdated(
          command.id,
          Status.inactive,
          Color.of(Status.inactive),
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
          event.color,
          event.createdAt,
          event.updatedAt,
          event.clientAt,
          event.endpointAt,
          event.created,
          event.updated);
    }

    public State onEvent(Event.PredatorMoved event) {
      return this;
    }

    public State onEvent(Event.PredatorUpdated event) {
      return this;
    }

    public State onEvent(Event.SpanToNeighbor event) {
      return this;
    }

    public State onEvent(Event.FillToNeighbor event) {
      return this;
    }

    public State onEvent(Event.DrawToNeighbor event) {
      return this;
    }

    public State onEvent(Event.ClearToNeighbor event) {
      return this;
    }

    public State onEvent(Event.EraseToNeighbor event) {
      return this;
    }

    static boolean isTooSoonToChange(Instant lastUpdatedAt) {
      var now = Instant.now();
      return now.isBefore(lastUpdatedAt.plusSeconds(3));
    }

    public static boolean insideShape(String id, Shape shape) {
      var rc = id.split("x"); // RxC / YxX
      var row = Integer.parseInt(rc[0]);
      var col = Integer.parseInt(rc[1]);
      return shape.isInside(row, col);
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

    public record CreateShape(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String region) implements Command {

      public CreateShape withRegion(String newRegion) {
        return new CreateShape(id, status, clientAt, endpointAt, shape, newRegion);
      }
    }

    public record DrawShape(
        String id,
        Status status,
        Color color,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String region) implements Command {

      public DrawShape withRegion(String newRegion) {
        return new DrawShape(id, status, color, clientAt, endpointAt, shape, newRegion);
      }
    }

    public record UpdateCell(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        String region) implements Command {

      public UpdateCell withRegion(String newRegion) {
        return new UpdateCell(id, status, clientAt, endpointAt, newRegion);
      }
    }

    public record CreatePredator(
        String id,
        String predatorId,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer range,
        String nextCellId,
        String region) implements Command {

      public CreatePredator withRegion(String newRegion) {
        return new CreatePredator(id, predatorId, status, clientAt, endpointAt, range, nextCellId, newRegion);
      }
    }

    public record MovePredator(
        String id,
        String predatorId,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Integer range,
        String nextCellId,
        Queue<String> tail,
        String region) implements Command {

      public MovePredator withRegion(String newRegion) {
        return new MovePredator(id, predatorId, status, clientAt, endpointAt, range, nextCellId, tail, newRegion);
      }
    }

    public record UpdatePredator(
        String id,
        String predatorId,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        String region) implements Command {

      public UpdatePredator withRegion(String newRegion) {
        return new UpdatePredator(id, predatorId, status, clientAt, endpointAt, newRegion);
      }
    }

    public record SpanCells(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String region) implements Command {

      public SpanCells withRegion(String newRegion) {
        return new SpanCells(id, status, clientAt, endpointAt, shape, newRegion);
      }
    }

    public record FillCells(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String region) implements Command {

      public FillCells withRegion(String newRegion) {
        return new FillCells(id, status, clientAt, endpointAt, shape, newRegion);
      }
    }

    public record DrawCells(
        String id,
        Status status,
        Color color,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String region) implements Command {

      public DrawCells withRegion(String newRegion) {
        return new DrawCells(id, status, color, clientAt, endpointAt, shape, newRegion);
      }
    }

    public record ClearCells(
        String id,
        Status status) implements Command {

      public ClearCells withRegion(String newRegion) {
        return new ClearCells(id, status);
      }
    }

    public record EraseCells(
        String id) implements Command {

      public EraseCells withRegion(String newRegion) {
        return new EraseCells(id);
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
        Color color,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String created,
        String updated) implements Event {}

    @TypeName("predator-moved")
    public record PredatorMoved(
        String id,
        String predatorId,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String created,
        Integer range,
        String lastCellId,
        Queue<String> tail,
        String updated) implements Event {}

    @TypeName("predator-updated")
    public record PredatorUpdated(
        String id,
        String predatorId,
        Status status,
        Instant updatedAt,
        Instant clientAt,
        Instant endpointAt,
        String updated) implements Event {}

    @TypeName("span-to-neighbor")
    public record SpanToNeighbor(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String created,
        String updated) implements Event {}

    @TypeName("fill-to-neighbor")
    public record FillToNeighbor(
        String id,
        Status status,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
        String created,
        String updated) implements Event {}

    @TypeName("draw-to-neighbor")

    public record DrawToNeighbor(
        String id,
        Status status,
        Color color,
        Instant clientAt,
        Instant endpointAt,
        Shape shape,
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

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = Shape.Circle.class, name = "circle"),
      @JsonSubTypes.Type(value = Shape.Rectangle.class, name = "rectangle"),
      @JsonSubTypes.Type(value = Shape.Triangle.class, name = "triangle"),
      @JsonSubTypes.Type(value = Shape.SingleCell.class, name = "single-cell")
  })
  public sealed interface Shape {
    boolean isInside(int row, int col);

    default boolean isSingleCell() {
      return false;
    }

    @TypeName("shape-circle")
    public record Circle(int centerRow, int centerCol, int radius) implements Shape {
      @Override
      public boolean isInside(int row, int col) {
        var maxRadius = Math.min(30, radius);
        return Math.pow(col - centerCol, 2) + Math.pow(row - centerRow, 2) <= Math.pow(maxRadius, 2);
      }
    }

    @TypeName("shape-rectangle")
    public record Rectangle(int topLeftRow, int topLeftCol, int width, int height) implements Shape {
      @Override
      public boolean isInside(int row, int col) {
        var maxWidth = Math.min(60, width);
        var maxHeight = Math.min(60, height);
        return row >= topLeftRow && row < topLeftRow + maxHeight && col >= topLeftCol && col < topLeftCol + maxWidth;
      }
    }

    @TypeName("shape-single-cell")
    public record SingleCell() implements Shape {
      @Override
      public boolean isInside(int row, int col) {
        return true;
      }

      @Override
      public boolean isSingleCell() {
        return true;
      }
    }

    @TypeName("shape-triangle")
    public record Triangle(int row1, int col1, int row2, int col2, int row3, int col3) implements Shape {
      @Override
      public boolean isInside(int row, int col) {
        // Barycentric coordinate system check
        // Calculate barycentric coordinates
        double denom = ((col2 - col3) * (row1 - row3) + (row3 - row2) * (col1 - col3));
        if (denom == 0)
          return false; // Degenerate triangle

        double alpha = ((col2 - col3) * (row - row3) + (row3 - row2) * (col - col3)) / denom;
        double beta = ((col3 - col1) * (row - row3) + (row1 - row3) * (col - col3)) / denom;
        double gamma = 1.0 - alpha - beta;

        return alpha >= 0 && beta >= 0 && gamma >= 0;
      }
    }

    public static Shape ofCircle(int centerRow, int centerCol, int radius) {
      return new Circle(centerRow, centerCol, radius);
    }

    public static Shape ofRectangle(int topLeftRow, int topLeftCol, int bottomRightRow, int bottomRightCol) {
      var width = Math.abs(bottomRightCol - topLeftCol) + 1;
      var height = Math.abs(bottomRightRow - topLeftRow) + 1;
      return new Rectangle(topLeftRow, topLeftCol, width, height);
    }

    public static Shape ofSingleCell() {
      return new SingleCell();
    }

    public static Shape ofTriangle(int row1, int col1, int row2, int col2, int row3, int col3) {
      return new Triangle(row1, col1, row2, col2, row3, col3);
    }
  }
}
