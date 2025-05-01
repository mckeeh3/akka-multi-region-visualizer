package io.example;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.application.SensorEntity;
import io.example.domain.Sensor;

public class SensorEntityTest {
  @Test
  void testUpdateStatus() {
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var id = "1x2";
    var status = Sensor.Status.green;
    var now = Instant.now();
    var command = new Sensor.Command.UpdateStatus(id, status, now, now);
    var result = testKit.method(SensorEntity::updateStatus).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    var event = result.getNextEventOfType(Sensor.Event.StatusUpdated.class);
    assertEquals(id, event.id());
    assertEquals(status, event.status());
    var state = testKit.getState();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }

  @Test
  void testUpdateSpanStatus() {
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var id = "2x3";

    { // first, create a sensor with red status
      var status = Sensor.Status.red;
      var now = Instant.now();
      var command = new Sensor.Command.UpdateStatus(id, status, now, now);
      var result = testKit.method(SensorEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, update span status
      var status = Sensor.Status.green;
      var centerX = 2;
      var centerY = 3;
      var radius = 5;
      var clientAt = Instant.now();
      var endpointAt = Instant.now();
      var command = new Sensor.Command.SpanStatus(id, status, clientAt, endpointAt, centerX, centerY, radius);
      var result = testKit.method(SensorEntity::updateSpanStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(9, result.getAllEvents().size()); // expects update event for this entity and 8 neighbor entities

      {
        var event = result.getNextEventOfType(Sensor.Event.StatusUpdated.class);
        assertEquals(id, event.id());
        assertEquals(status, event.status());
      }

      {
        var event = result.getNextEventOfType(Sensor.Event.SpanStatusUpdated.class);
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
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var id = "2x3";
    var status = Sensor.Status.green;
    var now = Instant.now();

    {
      var command = new Sensor.Command.UpdateStatus(id, status, now, now);
      var result = testKit.method(SensorEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, attempt to span with the same status
      var command = new Sensor.Command.SpanStatus(id, status, now, now, 2, 3, 5);
      var result = testKit.method(SensorEntity::updateSpanStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(0, result.getAllEvents().size()); // expect no events
    }
  }

  @Test
  void testUpdateFillStatus() {
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var id = "3x4";
    var now = Instant.now();

    { // first, create a sensor with default status
      var status = Sensor.Status.inactive;
      var command = new Sensor.Command.UpdateStatus(id, status, now, now);
      var result = testKit.method(SensorEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, update fill status
      var status = Sensor.Status.inactive;
      var centerX = 3;
      var centerY = 4;
      var radius = 2;
      var clientAt = Instant.now();
      var endpointAt = Instant.now();
      var command = new Sensor.Command.FillStatus(id, status, clientAt, endpointAt, centerX, centerY, radius);
      var result = testKit.method(SensorEntity::updateFillStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());

      var event = result.getNextEventOfType(Sensor.Event.StatusUpdated.class);
      assertEquals(id, event.id());
      assertEquals(status, event.status());

      var state = testKit.getState();
      assertEquals(id, state.id());
      assertEquals(status, state.status());
    }
  }

  @Test
  void testFillStatusWhenSensorStatusIsNotDefault() {
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var id = "5x6";
    var now = Instant.now();

    {
      var status = Sensor.Status.green;
      var command = new Sensor.Command.UpdateStatus(id, status, now, now);
      var result = testKit.method(SensorEntity::updateStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    { // then, attempt to update fill status
      var status = Sensor.Status.red;
      var centerX = 3;
      var centerY = 4;
      var radius = 2;
      var clientAt = Instant.now();
      var endpointAt = Instant.now();
      var command = new Sensor.Command.FillStatus(id, status, clientAt, endpointAt, centerX, centerY, radius);
      var result = testKit.method(SensorEntity::updateFillStatus).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(0, result.getAllEvents().size()); // expect no events
    }
  }

  @Test
  void testGetOnEmptyState() {
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var result = testKit.method(SensorEntity::get).invoke();
    assertTrue(result.isError());
    assertEquals("Sensor not found", result.getError());
  }

  @Test
  void testGetOnNonEmptyState() {
    var testKit = EventSourcedTestKit.of(SensorEntity::new);
    var id = "5x6";
    var status = Sensor.Status.green;
    var now = Instant.now();

    var command = new Sensor.Command.UpdateStatus(id, status, now, now);
    testKit.method(SensorEntity::updateStatus).invoke(command);

    var result = testKit.method(SensorEntity::get).invoke();
    assertTrue(result.isReply());

    var state = result.getReply();
    assertEquals(id, state.id());
    assertEquals(status, state.status());
  }
}
