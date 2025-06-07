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

    var shape = new GridCell.Shape(request.locationX(), request.locationY(), request.radius(), request.width(), request.height());
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

  @Get("/stream/{x1}/{y1}/{x2}/{y2}")
  public HttpResponse getGridCellsStream(Integer x1, Integer y1, Integer x2, Integer y2) {
    return HttpResponses.serverSentEvents(
        componentClient.forView()
            .stream(GridCellView::getGridCellsStream)
            .source(new GridCellView.StreamedGridCellsRequest(x1, y1, x2, y2)));
  }

  @Get("/list")
  public GridCellView.GridCells getGridCellsList() {
    return componentClient.forView()
        .method(GridCellView::getGridCellsList)
        .invoke();
  }

  @Get("/paginated-list/{x1}/{y1}/{x2}/{y2}/{pageTokenOffset}")
  public GridCellView.PagedGridCells getGridCellsPagedList(Integer x1, Integer y1, Integer x2, Integer y2, String pageTokenOffset) {
    pageTokenOffset = pageTokenOffset.equals("start") ? "" : pageTokenOffset;

    return componentClient.forView()
        .method(GridCellView::queryGridCellsPagedList)
        .invoke(new GridCellView.PagedGridCellsRequest(x1, y1, x2, y2, pageTokenOffset));
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

    var x1 = request.centerX() - request.radius();
    var y1 = request.centerY() - request.radius();
    var x2 = request.centerX() + request.radius();
    var y2 = request.centerY() + request.radius();
    var pageTokenOffset = "";

    var allGridCells = queryGridCellsInArea(x1, y1, x2, y2, pageTokenOffset);
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
            .method(GridCellView::queryActiveGridCells)
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

  record CreateShapeRequest(String id, String status, Instant clientAt, int locationX, int locationY, int radius, int width, int height) {}

  record UpdateGridCellRequest(String id, String status, Instant clientAt, Integer centerX, Integer centerY, Integer radius) {}

  record ScentCell(int x, int y, int maxIntensity) {}

  record ScentVector(double x, double y, double intensity) {}

  record DirectionVector(double x, double y) {}
}
