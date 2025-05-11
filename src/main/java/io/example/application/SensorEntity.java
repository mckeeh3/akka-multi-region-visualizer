package io.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.Sensor;

@ComponentId("sensor")
public class SensorEntity extends EventSourcedEntity<Sensor.State, Sensor.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String selfRegion;
  private final String entityId;

  public SensorEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
    this.selfRegion = context.selfRegion().isEmpty() ? "local-development" : context.selfRegion();
  }

  @Override
  public Sensor.State emptyState() {
    return Sensor.State.empty();
  }

  public Effect<Done> updateStatus(Sensor.Command.UpdateStatus command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateSpanStatus(Sensor.Command.SpanStatus command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateFillStatus(Sensor.Command.FillStatus command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateClearStatus(Sensor.Command.ClearStatus command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateEraseStatus(Sensor.Command.EraseStatus command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<Sensor.State> get() {
    log.info("Region: {}, EntityId: {}\n_State: {}", selfRegion, entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("Sensor '%s' not found".formatted(entityId));
    }
    return effects().reply(currentState());
  }

  @Override
  public Sensor.State applyEvent(Sensor.Event event) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Event: {}", selfRegion, entityId, currentState(), event);

    return switch (event) {
      case Sensor.Event.StatusUpdated e -> currentState().onEvent(e);
      case Sensor.Event.SpanToNeighbor e -> currentState().onEvent(e);
      case Sensor.Event.FillToNeighbor e -> currentState().onEvent(e);
      case Sensor.Event.ClearToNeighbor e -> currentState().onEvent(e);
      case Sensor.Event.EraseToNeighbor e -> currentState().onEvent(e);
    };
  }
}
