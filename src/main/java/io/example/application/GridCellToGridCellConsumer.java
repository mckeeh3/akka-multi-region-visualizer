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
      log.info("Ignore event: {}\n_HasLocalOrigin: {}, OriginRegion: {}, SelfRegion: {}",
          event,
          messageContext().hasLocalOrigin(),
          messageContext().originRegion(),
          messageContext().selfRegion());
      return effects().ignore();
    }

    return switch (event) {
      case GridCell.Event.PredatorMoved e -> onEvent(e);
      case GridCell.Event.PredatorLingered e -> onEvent(e);
      case GridCell.Event.SpanToNeighbor e -> onEvent(e);
      case GridCell.Event.FillToNeighbor e -> onEvent(e);
      case GridCell.Event.ClearToNeighbor e -> onEvent(e);
      case GridCell.Event.EraseToNeighbor e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  Effect onEvent(GridCell.Event.PredatorMoved event) {
    log.info("Region: {}, Event: {}", region(), event);

    var s = event.id().split("x");
    var x = Integer.parseInt(s[1]);
    var y = Integer.parseInt(s[0]);
    var x1 = x - event.range();
    var y1 = y - event.range();
    var x2 = x + event.range();
    var y2 = y + event.range();
    var pageTokenOffset = "";
    var allGridCells = queryGridCellsInArea(x1, y1, x2, y2, pageTokenOffset);
    String nextGridCellId = Predator.nextCell(event.id(), allGridCells, x, y, event.range());

    var command = new GridCell.Command.MovePredator(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.range(),
        event.linger(),
        nextGridCellId,
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::movePredator)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.PredatorLingered event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.LingerPredator(
        event.id(),
        event.status(),
        event.clientAt(),
        event.endpointAt(),
        event.linger(),
        region());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::lingerPredator)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.SpanToNeighbor event) {
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

  Effect onEvent(GridCell.Event.FillToNeighbor event) {
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

  Effect onEvent(GridCell.Event.ClearToNeighbor event) {
    log.info("Region: {}, Event: {}", region(), event);

    var command = new GridCell.Command.ClearStatus(
        event.id(),
        event.status());
    componentClient.forEventSourcedEntity(event.id())
        .method(GridCellEntity::updateClearStatus)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(GridCell.Event.EraseToNeighbor event) {
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

  List<GridCellRow> queryGridCellsInArea(int x1, int y1, int x2, int y2, String pageTokenOffset) {
    return Stream.generate(new Supplier<GridCellView.PagedGridCells>() {
      String currentPageToken = pageTokenOffset;
      boolean hasMore = true;

      @Override
      public GridCellView.PagedGridCells get() {
        if (!hasMore) {
          return null;
        }

        var pagedGridCells = componentClient.forView()
            .method(GridCellView::getGridCellsPagedList)
            .invoke(new GridCellView.PagedGridCellsRequest(x1, y1, x2, y2, currentPageToken));

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
