package io.example.api;

import java.time.Duration;
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
  public CompletionStage<Done> updateStatus(Sensor.Command.UpdateStatus command) {
    log.info("{}", command);
    return componentClient.forEventSourcedEntity(command.id())
        .method(SensorEntity::updateStatus)
        .invokeAsync(command);
  }

  @Get("/{entityId}")
  public CompletionStage<Sensor.State> get(String entityId) {
    return componentClient.forEventSourcedEntity(entityId)
        .method(SensorEntity::get)
        .invokeAsync();
  }

  @Get("/view/stream")
  public HttpResponse getViewStream() {
    return HttpResponses.serverSentEvents(
        componentClient.forView()
            .stream(SensorView::getViewStream)
            .source());
  }

  @Get("/view/list")
  public CompletionStage<SensorView.Sensors> getViewList() {
    return componentClient.forView()
        .method(SensorView::getViewList)
        .invokeAsync();
  }

  @Get("/current-time")
  public HttpResponse streamCurrentTime() {
    return HttpResponses.serverSentEvents(
        Source.tick(Duration.ZERO, Duration.ofSeconds(5), "tick")
            .map(__ -> System.currentTimeMillis()));
  }
}
