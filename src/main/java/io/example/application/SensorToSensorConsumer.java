package io.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.Sensor;

@ComponentId("sensor-to-sensor-consumer")
@Consume.FromEventSourcedEntity(SensorEntity.class)
public class SensorToSensorConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public SensorToSensorConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(Sensor.Event event) {
    if (!messageContext().hasLocalOrigin()) {
      log.info("Ignore event: {}\n_HasLocalOrigin: {}, OriginRegion: {}, SelfRegion: {}",
          event,
          messageContext().hasLocalOrigin(),
          messageContext().originRegion(),
          messageContext().selfRegion());
      return effects().ignore();
    }

    return switch (event) {
      case Sensor.Event.SpanToNeighbor e -> onEvent(e);
      case Sensor.Event.FillToNeighbor e -> onEvent(e);
      case Sensor.Event.ClearToNeighbor e -> onEvent(e);
      case Sensor.Event.EraseToNeighbor e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(Sensor.Event.SpanToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new Sensor.Command.SpanStatus(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.centerX(),
        event.centerY(),
        event.radius(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(SensorEntity::updateSpanStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onEvent(Sensor.Event.FillToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new Sensor.Command.FillStatus(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.centerX(),
        event.centerY(),
        event.radius(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(SensorEntity::updateFillStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onEvent(Sensor.Event.ClearToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new Sensor.Command.ClearStatus(
        event.id(),
        event.status());
    componentClient.forEventSourcedEntity(event.id())
        .method(SensorEntity::updateClearStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onEvent(Sensor.Event.EraseToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new Sensor.Command.EraseStatus(event.id());
    componentClient.forEventSourcedEntity(event.id())
        .method(SensorEntity::updateEraseStatus)
        .invoke(command);

    return effects().done();
  }

  String region() {
    var region = messageContext().selfRegion();
    return region.isEmpty() ? "unknown" : region;
  }
}
