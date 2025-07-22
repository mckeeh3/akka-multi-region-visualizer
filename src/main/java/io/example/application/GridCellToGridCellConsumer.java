package io.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.application.GridCellView.GridCellRow;
import io.example.domain.GridCell;
import io.example.domain.Predator;

@ComponentId("grid-cell-to-grid-cell-consumer")
@Consume.FromEventSourcedEntity(GridCellEntity.class)
public class GridCellToGridCellConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public GridCellToGridCellConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(GridCell.Event event) {
    if (!messageContext().hasLocalOrigin()) {
      log.debug("Ignore event: {}\n_HasLocalOrigin: {}, OriginRegion: {}, SelfRegion: {}",
          event,
          messageContext().hasLocalOrigin(),
          messageContext().originRegion(),
          messageContext().selfRegion());
      return effects().ignore();
    }

    return switch (event) {
      case GridCell.Event.PredatorMoved e -> onEvent(e);
      case GridCell.Event.PredatorUpdated e -> onEvent(e);
      case GridCell.Event.SpanToNeighbor e -> onEvent(e);
      case GridCell.Event.FillToNeighbor e -> onEvent(e);
      case GridCell.Event.DrawToNeighbor e -> onEvent(e);
      case GridCell.Event.ClearToNeighbor e -> onEvent(e);
      case GridCell.Event.EraseToNeighbor e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  Effect onEvent(GridCell.Event.PredatorMoved event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var gridCellsInRange = queryGridCellsInRange(event.id(), event.range());
    var nextGridCellId = Predator.nextGridCellId(event.id(), gridCellsInRange, event.range());

    var command = new GridCell.Command.MovePredator(
        event.id(),
        event.predatorId(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.range(),
        nextGridCellId,
        event.tail(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::movePredator)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.PredatorUpdated event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.UpdatePredator(
        event.id(),
        event.predatorId(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updatePredator)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.SpanToNeighbor event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.SpanCells(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.shape(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateSpanStatus)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.FillToNeighbor event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.FillCells(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.shape(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateFillStatus)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.DrawToNeighbor event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.DrawCells(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.shape(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::drawCells)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.ClearToNeighbor event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.ClearCells(
        event.id(),
        event.status());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateClearStatus)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.EraseToNeighbor event) {
    log.debug("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.EraseCells(event.id());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateEraseStatus)
        .invoke(command);

    return effects().done();
  }

  String region() {
    var region = messageContext().selfRegion();
    return region.isEmpty() ? "local-development" : region;
  }

  List<GridCellRow> queryGridCellsInRange(String id, int range) {
    var s = id.split("x"); // RxC, YxX
    var row = Integer.parseInt(s[0]);
    var col = Integer.parseInt(s[1]);

    // Try a short range query first for nearby grid cells
    if (range > 32) {
      var shortRange = 24;
      var row1 = row - shortRange;
      var col1 = col - shortRange;
      var row2 = row + shortRange;
      var col2 = col + shortRange;
      var gridCellsInRange = queryGridCellsInRange(row1, col1, row2, col2, "").stream()
          .filter(cell -> !cell.status().equals("predator"))
          .toList();

      if (!gridCellsInRange.isEmpty()) {
        return gridCellsInRange;
      }
    }

    var row1 = row - range;
    var col1 = col - range;
    var row2 = row + range;
    var col2 = col + range;
    var gridCellsInRange = queryGridCellsInRange(row1, col1, row2, col2, "").stream()
        .filter(cell -> !cell.status().equals("predator"))
        .toList();

    return gridCellsInRange;
  }

  List<GridCellRow> queryGridCellsInRange(int row1, int col1, int row2, int col2, String pageTokenOffset) {
    return Stream.generate(new Supplier<GridCellView.PagedGridCells>() {
      String currentPageToken = pageTokenOffset;
      boolean hasMore = true;

      @Override
      public GridCellView.PagedGridCells get() {
        if (!hasMore) {
          return null;
        }

        var pagedGridCells = componentClient.forView()
            .method(GridCellView::queryActiveGridCells)
            .invoke(new GridCellView.PagedGridCellsRequest(row1, col1, row2, col2, currentPageToken));

        currentPageToken = pagedGridCells.nextPageToken();
        hasMore = pagedGridCells.hasMore();

        return pagedGridCells;
      }
    })
        .takeWhile(pagedGridCells -> pagedGridCells != null)
        .flatMap(pagedGridCells -> pagedGridCells.gridCells().stream())
        .toList();
  }
}
