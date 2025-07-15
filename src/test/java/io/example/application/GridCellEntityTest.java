package io.example.application;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.GridCell;

public class GridCellEntityTest {
  @Test
  void testCreateSingleCellShape() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "1x2";
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";
    var shape = GridCell.Shape.ofSingleCell();
    var command = new GridCell.Command.CreateShape(id, status, now, now, shape, region);
    var result = testKit.method(GridCellEntity::createShape).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
    assertEquals(id, event.id());
    assertEquals(status, event.status());

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  // rectangle with top left (10,10), bottom right (20,20) with status: red
  @Test
  void testCreateRectangleShape() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "10x10";
    var status = GridCell.Status.red;
    var now = Instant.now();
    var region = "test";
    var shape = GridCell.Shape.ofRectangle(10, 10, 20, 20);
    var command = new GridCell.Command.CreateShape(id, status, now, now, shape, region);
    var result = testKit.method(GridCellEntity::createShape).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var events = result.getAllEvents();
    assertEquals(9, events.size());

    var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
    assertEquals(id, event.id());
    assertEquals(status, event.status());

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  @Test
  void testCreateTriangleShape() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "22x37";
    var status = GridCell.Status.red;
    var now = Instant.now();
    var region = "test";
    var shape = GridCell.Shape.ofTriangle(22, 37, 22, 56, 12, 47);
    var command = new GridCell.Command.CreateShape(id, status, now, now, shape, region);
    var result = testKit.method(GridCellEntity::createShape).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var events = result.getAllEvents();
    assertEquals(9, events.size());

    var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
    assertEquals(id, event.id());
    assertEquals(status, event.status());

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  @Test
  void testCreateShape() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var centerX = 10;
    var centerY = 20;
    var radius = 5;
    var id = "%dx%d".formatted(centerY, centerX); // RxC, YxX
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";
    var shape = GridCell.Shape.ofCircle(centerX, centerY, radius);
    var command = new GridCell.Command.CreateShape(id, status, now, now, shape, region);
    var result = testKit.method(GridCellEntity::createShape).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    var events = result.getAllEvents();
    assertEquals(9, events.size());

    var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
    assertEquals(id, event.id());
    assertEquals(status, event.status());

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  @Test
  void testUpdateStatus() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "1x2";
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";
    var command = new GridCell.Command.UpdateCell(id, status, now, now, region);
    var result = testKit.method(GridCellEntity::updateStatus).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
    assertEquals(id, event.id());
    assertEquals(status, event.status());
    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  @Test
  void testUpdateSpanStatus() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "2x3";

    { // first, create a grid cell with red status
      var status = GridCell.Status.red;
      var now = Instant.now();
      var region = "test";
      var command = new GridCell.Command.UpdateCell(id, status, now, now, region);
      var result = testKit.method(GridCellEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, update span status
      var status = GridCell.Status.green;
      var centerX = 2;
      var centerY = 3;
      var radius = 5;
      var clientAt = Instant.now();
      var endpointAt = Instant.now();
      var region = "test";
      var shape = GridCell.Shape.ofCircle(centerX, centerY, radius);
      var command = new GridCell.Command.SpanCells(id, status, clientAt, endpointAt, shape, region);
      var result = testKit.method(GridCellEntity::updateSpanStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(9, result.getAllEvents().size()); // expects update event for this entity and 8 neighbor entities

      {
        var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
        assertEquals(id, event.id());
        assertEquals(status, event.status());
      }

      {
        var event = result.getNextEventOfType(GridCell.Event.SpanToNeighbor.class);
        assertNotEquals(id, event.id());
        assertEquals(status, event.status());
        assertEquals(shape, event.shape());
      }

      var state = testKit.getState();
      assertEquals(status, state.status());
      assertEquals(clientAt, state.clientAt());
      assertEquals(endpointAt, state.endpointAt());
    }
  }

  @Test
  void testSpanWhenSensorStatusMatchesSpanStatus() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "2x3";
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";

    {
      var command = new GridCell.Command.UpdateCell(id, status, now, now, region);
      var result = testKit.method(GridCellEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, attempt to span with the same status
      var shape = GridCell.Shape.ofCircle(2, 3, 5);
      var command = new GridCell.Command.SpanCells(id, status, now, now, shape, region);
      var result = testKit.method(GridCellEntity::updateSpanStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(0, result.getAllEvents().size()); // expect no events
    }
  }

  @Test
  void testUpdateFillStatus() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "3x4";
    var now = Instant.now();
    var region = "test";

    { // first, create a grid cell with default status
      var status = GridCell.Status.inactive;
      var command = new GridCell.Command.UpdateCell(id, status, now, now, region);
      var result = testKit.method(GridCellEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, update fill status
      var status = GridCell.Status.green;
      var centerX = 3;
      var centerY = 4;
      var radius = 2;
      var clientAt = Instant.now();
      var endpointAt = Instant.now();
      var shape = GridCell.Shape.ofCircle(centerX, centerY, radius);
      var command = new GridCell.Command.FillCells(id, status, clientAt, endpointAt, shape, region);
      var result = testKit.method(GridCellEntity::updateFillStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());

      var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
      assertEquals(id, event.id());
      assertEquals(status, event.status());

      var state = testKit.getState();
      assertEquals(id, state.id());
      assertEquals(status, state.status());
    }
  }

  @Test
  void testFillStatusWhenGridCellStatusIsNotDefault() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "5x6";
    var now = Instant.now();
    var region = "test";

    {
      var status = GridCell.Status.green;
      var command = new GridCell.Command.UpdateCell(id, status, now, now, region);
      var result = testKit.method(GridCellEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, attempt to update fill status
      var status = GridCell.Status.red;
      var centerX = 3;
      var centerY = 4;
      var radius = 2;
      var clientAt = Instant.now();
      var endpointAt = Instant.now();
      var shape = GridCell.Shape.ofCircle(centerX, centerY, radius);
      var command = new GridCell.Command.FillCells(id, status, clientAt, endpointAt, shape, region);
      var result = testKit.method(GridCellEntity::updateFillStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(0, result.getAllEvents().size()); // expect no events
    }
  }

  @Test
  void testGetOnEmptyState() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var result = testKit.method(GridCellEntity::get).invoke();
    assertTrue(result.isError());
    assertEquals("GridCell 'testkit-entity-id' not found", result.getError());
  }

  @Test
  void testGetOnNonEmptyState() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "5x6";
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";

    var command = new GridCell.Command.UpdateCell(id, status, now, now, region);
    testKit.method(GridCellEntity::updateStatus).invoke(command);

    var result = testKit.method(GridCellEntity::get).invoke();
    assertTrue(result.isReply());

    var state = result.getReply();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  @Test
  void testCreatePredator() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "7x8";
    var predatorId = "predator";
    var status = GridCell.Status.predator;
    var range = 5;
    var now = Instant.now();
    var nextCellId = "7x9";
    var region = "test";

    var command = new GridCell.Command.CreatePredator(id, predatorId, status, now, now, range, nextCellId, region);
    var result = testKit.method(GridCellEntity::createPredator).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
      assertEquals(id, event.id());
      assertEquals(GridCell.Status.predator, event.status());
    }

    {
      var event = result.getNextEventOfType(GridCell.Event.PredatorMoved.class);
      assertEquals(nextCellId, event.id());
      assertEquals(predatorId, event.predatorId());
      assertEquals(GridCell.Status.predator, event.status());
    }

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(GridCell.Status.predator, state.status());
  }

  @Test
  void testMovePredator() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "7x8";
    var predatorId = "predator";
    var status = GridCell.Status.predator;
    var range = 5;
    var now = Instant.now();
    var nextCellId = "7x9";
    var tail = new LinkedList<String>();
    tail.add("tail");
    var region = "test";

    {
      var command = new GridCell.Command.MovePredator(id, predatorId, status, now, now, range, nextCellId, tail, region);
      var result = testKit.method(GridCellEntity::movePredator).invoke(command);
      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
        assertEquals(id, event.id());
        assertEquals(GridCell.Status.predator, event.status());
      }

      {
        var event = result.getNextEventOfType(GridCell.Event.PredatorMoved.class);
        assertEquals(nextCellId, event.id());
        assertEquals(predatorId, event.predatorId());
        assertEquals(GridCell.Status.predator, event.status());
      }
    }

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(GridCell.Status.predator, state.status());
  }

  @Test
  void testMovePredatorWhenTailIsTooLong() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "7x8";
    var predatorId = "predator";
    var status = GridCell.Status.predator;
    var range = 5;
    var now = Instant.now();
    var nextCellId = "7x9";
    var tail = new LinkedList<String>();
    tail.add("7x3");
    tail.add("7x4");
    tail.add("7x5");
    tail.add("7x6");
    tail.add("7x7");
    var region = "test";

    {
      var command = new GridCell.Command.MovePredator(id, predatorId, status, now, now, range, nextCellId, tail, region);
      var result = testKit.method(GridCellEntity::movePredator).invoke(command);
      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(3, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
        assertEquals(id, event.id());
        assertEquals(GridCell.Status.predator, event.status());
      }

      {
        var event = result.getNextEventOfType(GridCell.Event.PredatorMoved.class);
        assertEquals(nextCellId, event.id());
        assertEquals(predatorId, event.predatorId());
        assertEquals(GridCell.Status.predator, event.status());
      }

      {
        var event = result.getNextEventOfType(GridCell.Event.PredatorUpdated.class);
        assertEquals("7x3", event.id());
        assertEquals(predatorId, event.predatorId());
        assertEquals(GridCell.Status.inactive, event.status());
      }
    }

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(GridCell.Status.predator, state.status());
  }

  @Test
  void testTriangleShape() {
    var shape = GridCell.Shape.ofTriangle(1, 1, 3, 1, 2, 3);
    assertTrue(shape.isInside(1, 1));
    assertTrue(shape.isInside(3, 1));
    assertTrue(shape.isInside(2, 3));
    assertTrue(shape.isInside(2, 2)); // Center of triangle
    assertFalse(shape.isInside(0, 0));
    assertFalse(shape.isInside(4, 4));
    assertFalse(shape.isInside(2, 0));
  }

  @Test
  void testTriangleVertices() {
    var shape = GridCell.Shape.ofTriangle(22, 37, 22, 56, 12, 47);
    assertTrue(shape.isInside(22, 37)); // Vertex 1
    assertTrue(shape.isInside(22, 56)); // Vertex 2
    assertTrue(shape.isInside(12, 47)); // Vertex 3
  }

  // create a test for the rectangle shape with top left (10,10), bottom right (20,15) with status: red
  // test that all 4 corners are inside the shape
  // test that all cells between the top left and bottom right are inside the shape
  // test that all cells outside the shape are not inside the shape
  @Test
  void testRectangleShape() {
    var shape = GridCell.Shape.ofRectangle(10, 10, 20, 15);
    assertTrue(shape.isInside(10, 10));
    assertTrue(shape.isInside(20, 15));
    assertTrue(shape.isInside(10, 15));
    assertTrue(shape.isInside(20, 10));
    assertFalse(shape.isInside(9, 10));
    assertFalse(shape.isInside(21, 10));
    assertFalse(shape.isInside(10, 9));
  }
}
