package io.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.domain.Sensor;

@ComponentId("sensor_view")
public class SensorView extends View {
  private static final Logger log = LoggerFactory.getLogger(SensorView.class);

  @Query("""
      SELECT *
        FROM sensor_view
        LIMIT 1000
          """)
  public QueryStreamEffect<SensorView.SensorRow> getAllSensors() {
    log.info("Getting all sensors");
    return queryStreamResult();
  }

  @Consume.FromEventSourcedEntity(SensorEntity.class)
  public static class SensorsByStatus extends TableUpdater<SensorRow> {
    public Effect<SensorRow> onEvent(Sensor.Event event) {
      return switch (event) {
        case Sensor.Event.StatusUpdated e -> effects().updateRow(onEvent(e));
      };
    }

    private SensorRow onEvent(Sensor.Event.StatusUpdated event) {
      log.info("Event: {}\n_State: {}", event, rowState());

      return new SensorRow(
          event.id(),
          event.status(),
          event.updatedAt());
    }
  }

  public record SensorRow(
      String id,
      String status,
      Instant updatedAt) {}
}
