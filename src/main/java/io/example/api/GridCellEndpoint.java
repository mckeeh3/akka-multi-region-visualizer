package io.example.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import io.example.application.GridCellEntity;
import io.example.application.GridCellView;
import io.example.application.GridCellView.GridCellRow;
import io.example.domain.GridCell;
import io.example.domain.Predator;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/grid-cell")
public class GridCellEndpoint extends AbstractHttpEndpoint {
  final Logger log = LoggerFactory.getLogger(GridCellEndpoint.class);
  final ComponentClient componentClient;
  final Materializer materializer;
  final Config config;

  public GridCellEndpoint(ComponentClient componentClient, Materializer materializer, Config config) {
    this.componentClient = componentClient;
    this.materializer = materializer;
    this.config = config;
  }

  @Put("/create-shape")
  public Done createShape(CreateShapeRequest request) {
    log.info("Region: {}, {}", region(), request);

    GridCell.Shape shape;
    if (request.radius() > 0) {
      var row = request.locationY();
      var col = request.locationX();
      var color = GridCell.Color.of(request.status());
      shape = GridCell.Shape.ofCircle(row, col, request.radius(), color);
    } else if (request.width() > 0 && request.height() > 0) {
      var topLeftRow = request.locationY();
      var topLeftCol = request.locationX();
      var bottomRightRow = topLeftRow + request.height() - 1;
      var bottomRightCol = topLeftCol + request.width() - 1;
      var color = GridCell.Color.of(request.status());
      shape = GridCell.Shape.ofRectangle(topLeftRow, topLeftCol, bottomRightRow, bottomRightCol, color);
    } else {
      var row = request.locationY();
      var col = request.locationX();
      var color = GridCell.Color.of(request.status());
      shape = GridCell.Shape.ofSingleCell(row, col, color);
    }

    var command = new GridCell.Command.CreateShape(
        request.id(),
        GridCell.Status.valueOf(request.status()),
        request.clientAt(),
        Instant.now(),
        shape,
        region());

    return componentClient.forEventSourcedEntity(request.id())
        .method(GridCellEntity::createShape)
        .invoke(command);
  }

  @Put("/update-status")
  public Done updateStatus(UpdateGridCellRequest request) {
    log.info("Region: {}, {}", region(), request);

    var status = GridCell.Status.valueOf(request.status());
    var clientAt = request.clientAt();
    var endpointAt = Instant.now();
    var command = new GridCell.Command.UpdateCell(
        request.id(),
        status,
        clientAt,
        endpointAt,
        region());

    return componentClient.forEventSourcedEntity(command.id())
        .method(GridCellEntity::updateStatus)
        .invoke(command);
  }

  @Put("/clear-cells")
  public Done clearCells(UpdateGridCellRequest request) {
    log.info("Region: {}, {}", region(), request);

    var status = GridCell.Status.valueOf(request.status());
    var command = new GridCell.Command.ClearCells(request.id(), status);

    return componentClient.forEventSourcedEntity(command.id())
        .method(GridCellEntity::updateClearStatus)
        .invoke(command);
  }

  @Put("/erase-cells")
  public Done eraseCells(UpdateGridCellRequest request) {
    log.info("Region: {}, {}", region(), request);

    var command = new GridCell.Command.EraseCells(request.id());

    return componentClient.forEventSourcedEntity(command.id())
        .method(GridCellEntity::updateEraseStatus)
        .invoke(command);
  }

  @Get("/entity-by-id/{id}")
  public GridCell.State getEntityById(String id) {
    return componentClient.forEventSourcedEntity(id)
        .method(GridCellEntity::get)
        .invoke();
  }

  @Get("/view-row-by-id/{id}")
  public GridCellView.GridCellRow getViewRowById(String id) {
    return componentClient.forView()
        .method(GridCellView::getGridCell)
        .invoke(id);
  }

  @Get("/stream/{row1}/{col1}/{row2}/{col2}")
  public HttpResponse getGridCellsStream(Integer row1, Integer col1, Integer row2, Integer col2) {
    return HttpResponses.serverSentEvents(
        componentClient.forView()
            .stream(GridCellView::getGridCellsStream)
            .source(new GridCellView.StreamedGridCellsRequest(row1, col1, row2, col2)));
  }

  @Get("/list")
  public GridCellView.GridCells getGridCellsList() {
    return componentClient.forView()
        .method(GridCellView::getGridCellsList)
        .invoke();
  }

  @Get("/paginated-list/{row1}/{col1}/{row2}/{col2}/{pageTokenOffset}")
  public GridCellView.PagedGridCells getGridCellsPagedList(Integer row1, Integer col1, Integer row2, Integer col2, String pageTokenOffset) {
    pageTokenOffset = pageTokenOffset.equals("start") ? "" : pageTokenOffset;

    return componentClient.forView()
        .method(GridCellView::queryGridCellsPagedList)
        .invoke(new GridCellView.PagedGridCellsRequest(row1, col1, row2, col2, pageTokenOffset));
  }

  @Get("/region")
  public String getRegion() {
    return region();
  }

  @Get("/multi-region-routes")
  public List<String> getRoutes() {
    if (region().equals("local-development")) {
      var port = config.getInt("akka.javasdk.dev-mode.http-port");
      return List.of("localhost:" + port);
    }

    // First try to get from environment variable
    var splitOn = "\\|";
    try {
      var routes = System.getenv("MULTI_REGION_ROUTES");
      if (routes != null && !routes.isEmpty()) {
        return List.of(routes.split(splitOn));
      }
    } catch (Exception e) {
      log.error("Failed to get routes from environment variable", e);
    }

    // Then try to get from config
    try {
      var routes = config.getString("multi-region-routes");
      return List.of(routes.split(splitOn));
    } catch (Exception e) {
      log.error("Failed to get routes from config", e);
      throw HttpException.error(StatusCodes.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  @Put("/create-predator")
  public Done createPredator(UpdateGridCellRequest request) {
    log.info("Region: {}, {}", region(), request);

    var row1 = request.centerY() - request.radius();
    var col1 = request.centerX() - request.radius();
    var row2 = request.centerY() + request.radius();
    var col2 = request.centerX() + request.radius();
    var pageTokenOffset = "";

    var allGridCells = queryGridCellsInArea(row1, col1, row2, col2, pageTokenOffset);
    log.info("Found {} grid cells in the rectangle area", allGridCells.size());

    String nextGridCellId = Predator.nextGridCellId(request.id(), allGridCells, request.radius());
    log.info("Predator cell: {}, Next cell: {}", request.id(), nextGridCellId);

    var range = request.radius();
    var predatorId = Predator.parentId();
    var command = new GridCell.Command.CreatePredator(
        request.id(),
        predatorId,
        GridCell.Status.predator,
        request.clientAt(),
        Instant.now(),
        range,
        nextGridCellId,
        region());

    componentClient.forEventSourcedEntity(request.id())
        .method(GridCellEntity::createPredator)
        .invoke(command);

    return Done.done();
  }

  @Get("/config")
  public Config getConfig() {
    return config;
  }

  @Get("/system-properties")
  public Properties getSystemProperties() {
    return System.getProperties();
  }

  @Get("/system-environment")
  public Map<String, String> getSystemEnvironment() {
    return System.getenv();
  }

  @Get("/current-time")
  public HttpResponse streamCurrentTime() {
    return HttpResponses.serverSentEvents(
        Source.tick(Duration.ZERO, Duration.ofSeconds(5), "tick")
            .map(__ -> System.currentTimeMillis()));
  }

  String region() {
    return requestContext().selfRegion().isEmpty() ? "local-development" : requestContext().selfRegion();
  }

  List<GridCellRow> queryGridCellsInArea(int row1, int col1, int row2, int col2, String pageTokenOffset) {
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

  record CreateShapeRequest(String id, String status, Instant clientAt, int locationX, int locationY, int radius, int width, int height,
      int row1, int col1, int row2, int col2, int row3, int col3) {}

  record UpdateGridCellRequest(String id, String status, Instant clientAt, Integer centerX, Integer centerY, Integer radius) {}

  record ScentCell(int row, int col, int maxIntensity) {}

  record ScentVector(double row, double col, double intensity) {}

  record DirectionVector(double row, double col) {}
}
