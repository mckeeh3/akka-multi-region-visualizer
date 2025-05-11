package io.example.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
import akka.stream.javadsl.Source;
import io.example.application.SensorEntity;
import io.example.application.SensorView;
import io.example.domain.Sensor;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/sensor")
public class SensorEndpoint extends AbstractHttpEndpoint {
  private final Logger log = LoggerFactory.getLogger(SensorEndpoint.class);
  private final ComponentClient componentClient;
  private final Config config;

  public SensorEndpoint(ComponentClient componentClient, Config config) {
    this.componentClient = componentClient;
    this.config = config;
  }

  @Put("/update-status")
  public CompletionStage<Done> updateStatus(UpdateSensorRequest request) {
    log.info("Region: {}, {}", region(), request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.UpdateStatus(
        request.id(),
        status,
        request.updatedAt(),
        Instant.now(),
        region());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateStatus)
        .invokeAsync(command);
  }

  @Put("/span-status")
  public CompletionStage<Done> spanStatus(UpdateSensorRequest request) {
    log.info("Region: {}, {}", region(), request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.SpanStatus(
        request.id(),
        status,
        request.updatedAt(),
        Instant.now(),
        request.centerX(),
        request.centerY(),
        request.radius(),
        region());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateSpanStatus)
        .invokeAsync(command);
  }

  @Put("/fill-status")
  public CompletionStage<Done> fillStatus(UpdateSensorRequest request) {
    log.info("Region: {}, {}", region(), request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.FillStatus(
        request.id(),
        status,
        request.updatedAt(),
        Instant.now(),
        request.centerX(),
        request.centerY(),
        request.radius(),
        region());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateFillStatus)
        .invokeAsync(command);
  }

  @Put("/clear-status")
  public CompletionStage<Done> clearStatus(UpdateSensorRequest request) {
    log.info("Region: {}, {}", region(), request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.ClearStatus(request.id(), status);
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateClearStatus)
        .invokeAsync(command);
  }

  @Put("/erase-status")
  public CompletionStage<Done> eraseStatus(UpdateSensorRequest request) {
    log.info("Region: {}, {}", region(), request);
    var command = new Sensor.Command.EraseStatus(request.id());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateEraseStatus)
        .invokeAsync(command);
  }

  @Get("/entity-by-id/{id}")
  public CompletionStage<Sensor.State> getEntityById(String id) {
    return componentClient.forEventSourcedEntity(id)
        .method(SensorEntity::get)
        .invokeAsync();
  }

  @Get("/view-row-by-id/{id}")
  public CompletionStage<SensorView.SensorRow> getViewRowById(String id) {
    return componentClient.forView()
        .method(SensorView::getSensor)
        .invokeAsync(id);
  }

  @Get("/stream/{x1}/{y1}/{x2}/{y2}")
  public HttpResponse getSensorsStream(Integer x1, Integer y1, Integer x2, Integer y2) {
    return HttpResponses.serverSentEvents(
        componentClient.forView()
            .stream(SensorView::getSensorsStream)
            .source(new SensorView.StreamedSensorsRequest(x1, y1, x2, y2)));
  }

  @Get("/list")
  public CompletionStage<SensorView.Sensors> getSensorsList() {
    return componentClient.forView()
        .method(SensorView::getSensorsList)
        .invokeAsync();
  }

  @Get("/paginated-list/{x1}/{y1}/{x2}/{y2}/{pageTokenOffset}")
  public CompletionStage<SensorView.PagedSensors> getSensorsPagedList(Integer x1, Integer y1, Integer x2, Integer y2, String pageTokenOffset) {
    pageTokenOffset = pageTokenOffset.equals("start") ? "" : pageTokenOffset;
    return componentClient.forView()
        .method(SensorView::getSensorsPagedList)
        .invokeAsync(new SensorView.PagedSensorsRequest(x1, y1, x2, y2, pageTokenOffset));
  }

  @Get("/region")
  public CompletionStage<String> getRegion() {
    return CompletableFuture.completedFuture(region());
  }

  @Get("/routes")
  public CompletionStage<List<String>> getRoutes() {
    if (region().equals("local-development")) {
      var port = config.getInt("akka.javasdk.dev-mode.http-port");
      return CompletableFuture.completedFuture(List.of("localhost:" + port));
    }
    try {
      var routes = config.getString("multi-region-routes");
      return CompletableFuture.completedFuture(List.of(routes.split(",")));
    } catch (Exception e) {
      log.error("Failed to get routes from config", e);
      throw HttpException.error(StatusCodes.INTERNAL_SERVER_ERROR, e.getMessage());
    }
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

  public record UpdateSensorRequest(String id, String status, Instant updatedAt, Integer centerX, Integer centerY, Integer radius) {}
}
