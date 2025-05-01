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
    return switch (event) {
      case Sensor.Event.SpanStatusUpdated e -> onSpanStatusUpdated(e);
      case Sensor.Event.FillStatusUpdated e -> onFillStatusUpdated(e);
      default -> effects().ignore();
    };
  }

  private Effect onSpanStatusUpdated(Sensor.Event.SpanStatusUpdated event) {
    log.info("Event: {}", event);

    var command = new Sensor.Command.SpanStatus(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.centerX(),
        event.centerY(),
        event.radius());
    componentClient.forEventSourcedEntity(event.id())
        .method(SensorEntity::updateSpanStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onFillStatusUpdated(Sensor.Event.FillStatusUpdated event) {
    log.info("Event: {}", event);

    var command = new Sensor.Command.FillStatus(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.centerX(),
        event.centerY(),
        event.radius());
    componentClient.forEventSourcedEntity(event.id())
        .method(SensorEntity::updateFillStatus)
        .invoke(command);

    return effects().done();
  }
}
