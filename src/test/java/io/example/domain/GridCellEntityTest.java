package io.example.domain;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.application.GridCellEntity;

public class GridCellEntityTest {
  @Test
  void testUpdateStatus() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "1x2";
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";
    var command = new GridCell.Command.UpdateStatus(id, status, now, now, region);
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
      var command = new GridCell.Command.UpdateStatus(id, status, now, now, region);
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
      var command = new GridCell.Command.SpanStatus(id, status, clientAt, endpointAt, centerX, centerY, radius, region);
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
        assertEquals(centerX, event.centerX());
        assertEquals(centerY, event.centerY());
        assertEquals(radius, event.radius());
      }

      var state = testKit.getState();
      assertEquals(status, state.status());
      assertEquals(clientAt, state.clientAt());
      assertEquals(endpointAt, state.endpointAt());
    }
  }

  @Test
  void testSpanWhenSersorStatusMatchesSpanStatus() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "2x3";
    var status = GridCell.Status.green;
    var now = Instant.now();
    var region = "test";

    {
      var command = new GridCell.Command.UpdateStatus(id, status, now, now, region);
      var result = testKit.method(GridCellEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, attempt to span with the same status
      var command = new GridCell.Command.SpanStatus(id, status, now, now, 2, 3, 5, region);
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
      var command = new GridCell.Command.UpdateStatus(id, status, now, now, region);
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
      var command = new GridCell.Command.FillStatus(id, status, clientAt, endpointAt, centerX, centerY, radius, region);
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
      var command = new GridCell.Command.UpdateStatus(id, status, now, now, region);
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
      var command = new GridCell.Command.FillStatus(id, status, clientAt, endpointAt, centerX, centerY, radius, region);
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

    var command = new GridCell.Command.UpdateStatus(id, status, now, now, region);
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
    var status = GridCell.Status.predator;
    var range = 5;
    var linger = 2;
    var now = Instant.now();
    var nextCellId = "7x9";
    var region = "test";

    var command = new GridCell.Command.CreatePredator(id, status, now, now, range, linger, nextCellId, region);
    var result = testKit.method(GridCellEntity::createPredator).invoke(command);

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
      assertEquals(GridCell.Status.predator, event.status());
    }

    {
      var event = result.getNextEventOfType(GridCell.Event.PredatorLingered.class);
      assertEquals(id, event.id());
      assertEquals(GridCell.Status.predator, event.status());
      assertEquals(linger, event.linger());
    }

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(GridCell.Status.predator, state.status());
  }

  @Test
  void testMovePredator() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "7x8";
    var status = GridCell.Status.predator;
    var range = 5;
    var linger = 2;
    var now = Instant.now();
    var nextCellId = "7x9";
    var region = "test";

    {
      var command = new GridCell.Command.MovePredator(id, status, now, now, range, linger, nextCellId, region);
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
        assertEquals(GridCell.Status.predator, event.status());
      }

      {
        var event = result.getNextEventOfType(GridCell.Event.PredatorLingered.class);
        assertEquals(id, event.id());
        assertEquals(GridCell.Status.predator, event.status());
        assertEquals(linger, event.linger());
      }
    }

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(GridCell.Status.predator, state.status());
  }

  @Test
  void testLingerPredator() {
    var testKit = EventSourcedTestKit.of(GridCellEntity::new);
    var id = "7x8";
    var status = GridCell.Status.predator;
    var range = 5;
    var linger = 2;
    var now = Instant.now();
    var nextCellId = "7x9";
    var region = "test";

    {
      var command = new GridCell.Command.MovePredator(id, status, now, now, range, linger, nextCellId, region);
      var result = testKit.method(GridCellEntity::movePredator).invoke(command);
      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(3, result.getAllEvents().size());
    }

    {
      var command = new GridCell.Command.LingerPredator(id, status, now, now, linger, region);
      var result = testKit.method(GridCellEntity::lingerPredator).invoke(command);
      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(GridCell.Event.StatusUpdated.class);
        assertEquals(id, event.id());
        assertEquals(GridCell.Status.predator, event.status());
      }

      {
        var event = result.getNextEventOfType(GridCell.Event.PredatorLingered.class);
        assertEquals(id, event.id());
        assertEquals(GridCell.Status.predator, event.status());
        assertEquals(linger - 1, event.linger());
      }
    }

    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(GridCell.Status.predator, state.status());
  }
}
