package io.example.application;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.UpdateContext;
import akka.javasdk.view.View;
import io.example.domain.GridCell;

@ComponentId("grid-cell-view")
public class GridCellView extends View {
  static final Logger log = LoggerFactory.getLogger(GridCellView.class);

  @Query("""
      SELECT *
        FROM grid_cell_view
        WHERE id = :id
          """)
  public QueryEffect<GridCellRow> getGridCell(String id) {
    return queryResult();
  }

  @Query(value = """
      SELECT *
        FROM grid_cell_view
        WHERE cellRow >= :row1 AND cellRow <= :row2 AND cellCol >= :col1 AND cellCol <= :col2
          """, streamUpdates = true)
  public QueryStreamEffect<GridCellRow> getGridCellsStream(StreamedGridCellsRequest request) {
    return queryStreamResult();
  }

  @Query("""
      SELECT * as gridCells
        FROM grid_cell_view
        LIMIT 1000
          """)
  public QueryEffect<GridCells> getGridCellsList() {
    return queryResult();
  }

  @Query("""
      SELECT * as gridCells, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM grid_cell_view
        WHERE cellRow >= :row1 AND cellRow <= :row2 AND cellCol >= :col1 AND cellCol <= :col2
        LIMIT 1000
        OFFSET page_token_offset(:pageTokenOffset)
          """)
  public QueryEffect<PagedGridCells> queryGridCellsPagedList(PagedGridCellsRequest request) {
    return queryResult();
  }

  @Query("""
      SELECT * as gridCells, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM grid_cell_view
        WHERE cellRow >= :row1 AND cellRow <= :row2 AND cellCol >= :col1 AND cellCol <= :col2
        AND status != 'inactive'
        LIMIT 1000
        OFFSET page_token_offset(:pageTokenOffset)
          """)
  public QueryEffect<PagedGridCells> queryActiveGridCells(PagedGridCellsRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(GridCellEntity.class)
  public static class GridCellsByStatus extends TableUpdater<GridCellRow> {

    public Effect<GridCellRow> onEvent(GridCell.Event event) {
      return switch (event) {
        case GridCell.Event.StatusUpdated e -> effects().updateRow(onEvent(e));
        default -> effects().ignore();
      };
    }

    GridCellRow onEvent(GridCell.Event.StatusUpdated event) {
      log.debug("Region: {}, Event: {}\n_State: {}", region(updateContext()), event, rowState());

      var rc = event.id().split("x"); // RxC / YxX
      var viewAt = Instant.now();
      var elapsedMs = (int) (viewAt.toEpochMilli() - event.updatedAt().toEpochMilli());

      return new GridCellRow(
          event.id(),
          event.status().toString(),
          event.color().toHex(),
          Integer.parseInt(rc[0]), // row
          Integer.parseInt(rc[1]), // col
          event.clientAt(),
          event.endpointAt(),
          event.createdAt(),
          event.updatedAt(),
          viewAt,
          elapsedMs,
          event.created(),
          event.updated(),
          region(updateContext()));
    }

    String region(UpdateContext updateContext) {
      var region = updateContext.selfRegion();
      return region.isEmpty() ? "local-development" : region;
    }
  }

  public record GridCellRow(
      String id,
      String status,
      String color,
      int cellRow,
      int cellCol,
      Instant clientAt,
      Instant endpointAt,
      Instant createdAt,
      Instant updatedAt,
      Instant viewAt,
      int elapsedMs,
      String created,
      String updated,
      String view) {}

  public record GridCells(List<GridCellRow> gridCells) {}

  public record StreamedGridCellsRequest(Integer row1, Integer col1, Integer row2, Integer col2) {}

  public record PagedGridCellsRequest(Integer row1, Integer col1, Integer row2, Integer col2, String pageTokenOffset) {}

  public record PagedGridCells(List<GridCellRow> gridCells, String nextPageToken, boolean hasMore) {}
}
