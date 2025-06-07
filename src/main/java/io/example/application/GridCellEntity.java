package io.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.GridCell;

@ComponentId("grid-cell-entity")
public class GridCellEntity extends EventSourcedEntity<GridCell.State, GridCell.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;
  private final String selfRegion;

  public GridCellEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
    this.selfRegion = context.selfRegion().isEmpty() ? "local-development" : context.selfRegion();
  }

  @Override
  public GridCell.State emptyState() {
    return GridCell.State.empty();
  }

  public Effect<Done> createShape(GridCell.Command.CreateShape command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateStatus(GridCell.Command.UpdateCell command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> createPredator(GridCell.Command.CreatePredator command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> movePredator(GridCell.Command.MovePredator command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updatePredator(GridCell.Command.UpdatePredator command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateSpanStatus(GridCell.Command.SpanCells command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateFillStatus(GridCell.Command.FillCells command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateClearStatus(GridCell.Command.ClearCells command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> updateEraseStatus(GridCell.Command.EraseCells command) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Command: {}", selfRegion, entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command.withRegion(selfRegion)).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<GridCell.State> get() {
    log.info("Region: {}, EntityId: {}\n_State: {}", selfRegion, entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("GridCell '%s' not found".formatted(entityId));
    }
    return effects().reply(currentState());
  }

  @Override
  public GridCell.State applyEvent(GridCell.Event event) {
    log.info("Region: {}, EntityId: {}\n_State: {}\n_Event: {}", selfRegion, entityId, currentState(), event);

    return switch (event) {
      case GridCell.Event.StatusUpdated e -> currentState().onEvent(e);
      case GridCell.Event.PredatorMoved e -> currentState().onEvent(e);
      case GridCell.Event.PredatorUpdated e -> currentState().onEvent(e);
      case GridCell.Event.SpanToNeighbor e -> currentState().onEvent(e);
      case GridCell.Event.FillToNeighbor e -> currentState().onEvent(e);
      case GridCell.Event.ClearToNeighbor e -> currentState().onEvent(e);
      case GridCell.Event.EraseToNeighbor e -> currentState().onEvent(e);
    };
  }
}
