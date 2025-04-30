package io.example.application;

import java.time.Instant;
import java.util.List;

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
          """)
  public QueryStreamEffect<SensorRow> getSensorsStreamOld() {
    log.info("Getting all sensors");
    return queryStreamResult();
  }

  @Query("""
      SELECT *
        FROM sensor_view
        WHERE x >= :x1 AND x <= :x2 AND y >= :y1 AND y <= :y2
          """)
  public QueryStreamEffect<SensorRow> getSensorsStream(StreamedSensorsRequest request) {
    log.info("Getting all sensors");
    return queryStreamResult();
  }

  @Query("""
      SELECT * as sensors
        FROM sensor_view
        LIMIT 1000
          """)
  public QueryEffect<Sensors> getSensorsList() {
    log.info("Getting sensors by status");
    return queryResult();
  }

  @Query("""
      SELECT * as sensors, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM sensor_view
        WHERE x >= :x1 AND x <= :x2 AND y >= :y1 AND y <= :y2
        LIMIT 1000
        OFFSET page_token_offset(:pageTokenOffset)
          """)
  public QueryEffect<PagedSensors> getSensorsPagedList(PagedSensorsRequest request) {
    log.info("Getting sensors by status");
    return queryResult();
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

      var rc = event.id().split("x"); // RxC / YxX
      return new SensorRow(
          event.id(),
          event.status(),
          Integer.parseInt(rc[1]),
          Integer.parseInt(rc[0]),
          event.createdAt(),
          event.updatedAt(),
          event.clientAt(),
          event.endpointAt(),
          Instant.now());
    }
  }

  public record SensorRow(
      String id,
      String status,
      Integer x,
      Integer y,
      Instant createdAt,
      Instant updatedAt,
      Instant clientAt,
      Instant endpointAt,
      Instant viewAt) {}

  public record Sensors(List<SensorRow> sensors) {}

  public record StreamedSensorsRequest(Integer x1, Integer y1, Integer x2, Integer y2) {}

  public record PagedSensorsRequest(Integer x1, Integer y1, Integer x2, Integer y2, String pageTokenOffset) {}

  public record PagedSensors(List<SensorRow> sensors, String nextPageToken, boolean hasMore) {}
}
