package io.example.api;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import io.example.application.SensorEntity;
import io.example.application.SensorView;
import io.example.domain.Sensor;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/sensor")
public class SensorEndpoint {
  private final Logger log = LoggerFactory.getLogger(SensorEndpoint.class);
  private final ComponentClient componentClient;

  public SensorEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Put("/update-status")
  public CompletionStage<Done> updateStatus(UpdateSensorRequest request) {
    log.info("{}", request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.UpdateStatus(request.id(), status, request.updatedAt(), Instant.now());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateStatus)
        .invokeAsync(command);
  }

  @Put("/span-status")
  public CompletionStage<Done> spanStatus(UpdateSensorRequest request) {
    log.info("{}", request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.SpanStatus(request.id(), status, request.updatedAt(), Instant.now(), request.centerX(), request.centerY(), request.radius());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateSpanStatus)
        .invokeAsync(command);
  }

  @Put("/fill-status")
  public CompletionStage<Done> fillStatus(UpdateSensorRequest request) {
    log.info("{}", request);
    var status = Sensor.Status.valueOf(request.status().equals("default") ? "inactive" : request.status());
    var command = new Sensor.Command.FillStatus(request.id(), status, request.updatedAt(), Instant.now(), request.centerX(), request.centerY(), request.radius());
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateFillStatus)
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

  @Get("/current-time")
  public HttpResponse streamCurrentTime() {
    return HttpResponses.serverSentEvents(
        Source.tick(Duration.ZERO, Duration.ofSeconds(5), "tick")
            .map(__ -> System.currentTimeMillis()));
  }

  public record UpdateSensorRequest(String id, String status, Instant updatedAt, Integer centerX, Integer centerY, Integer radius) {}
}
