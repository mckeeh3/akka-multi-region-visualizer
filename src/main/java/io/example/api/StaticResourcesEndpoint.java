package io.example.api;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class StaticResourcesEndpoint {

  @Get("/")
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("/index.html")
  public HttpResponse indexHtml() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("/favicon.ico")
  public HttpResponse favicon() {
    return HttpResponses.staticResource("favicon.ico");
  }

  @Get("/index.js")
  public HttpResponse script() {
    return HttpResponses.staticResource("index.js");
  }

  @Get("/index.css")
  public HttpResponse style() {
    return HttpResponses.staticResource("index.css");
  }

  @Get("/help.html")
  public HttpResponse help() {
    return HttpResponses.staticResource("help.html");
  }

  @Get("/version.txt")
  public HttpResponse version() {
    return HttpResponses.staticResource("version.txt");
  }

  @Get("/3d-simulator.html")
  public HttpResponse simulator() {
    return HttpResponses.staticResource("3d-simulator.html");
  }

  @Get("/3d-simulator-help.html")
  public HttpResponse simulatorHelp() {
    return HttpResponses.staticResource("3d-simulator-help.html");
  }

  @Get("/static/**") // Serve static files (e.g. HTML, CSS, JS)
  public HttpResponse serveStatic(HttpRequest request) {
    return HttpResponses.staticResource(request, "/static/");
  }
}
