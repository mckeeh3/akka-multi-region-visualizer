package io.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.GridCell;

@ComponentId("grid-cell-to-grid-cell-consumer")
@Consume.FromEventSourcedEntity(GridCellEntity.class)
public class GridCellToGridCellConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public GridCellToGridCellConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(GridCell.Event event) {
    if (!messageContext().hasLocalOrigin()) {
      log.info("Ignore event: {}\n_HasLocalOrigin: {}, OriginRegion: {}, SelfRegion: {}",
          event,
          messageContext().hasLocalOrigin(),
          messageContext().originRegion(),
          messageContext().selfRegion());
      return effects().ignore();
    }

    return switch (event) {
      case GridCell.Event.SpanToNeighbor e -> onEvent(e);
      case GridCell.Event.FillToNeighbor e -> onEvent(e);
      case GridCell.Event.ClearToNeighbor e -> onEvent(e);
      case GridCell.Event.EraseToNeighbor e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(GridCell.Event.SpanToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.SpanStatus(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.centerX(),
        event.centerY(),
        event.radius(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateSpanStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onEvent(GridCell.Event.FillToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.FillStatus(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.centerX(),
        event.centerY(),
        event.radius(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateFillStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onEvent(GridCell.Event.ClearToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.ClearStatus(
        event.id(),
        event.status());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateClearStatus)
        .invoke(command);

    return effects().done();
  }

  private Effect onEvent(GridCell.Event.EraseToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.EraseStatus(event.id());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateEraseStatus)
        .invoke(command);

    return effects().done();
  }

  String region() {
    var region = messageContext().selfRegion();
    return region.isEmpty() ? "local-development" : region;
  }
}
