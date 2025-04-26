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

  @Get("/favicon.ico")
  public HttpResponse favicon() {
    return HttpResponses.staticResource("favicon.ico");
  }

  @Get("/script.js")
  public HttpResponse script() {
    return HttpResponses.staticResource("script.js");
  }

  @Get("/style.css")
  public HttpResponse style() {
    return HttpResponses.staticResource("style.css");
  }

  @Get("/static/**") // Serve static files (e.g. HTML, CSS, JS)
  public HttpResponse serveStatic(HttpRequest request) {
    return HttpResponses.staticResource(request, "/static/");
  }
}
