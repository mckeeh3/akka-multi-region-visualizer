package io.example;

import akka.javasdk.annotations.Setup;
import akka.javasdk.ServiceSetup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

@Setup
public class Bootstrap implements ServiceSetup {
  private final Logger log = LoggerFactory.getLogger(Bootstrap.class);
  private final Config config;

  public Bootstrap(Config config) {
    this.config = config;
  }

  @Override
  public void onStartup() {
    log.info("Service started");
    config.entrySet().stream()
        .filter(entry -> entry.getKey().startsWith("akka.javasdk"))
        .forEach(entry -> log.info("{} = {}", entry.getKey(), entry.getValue()));

    log.info("Multi-region routes: {}", System.getenv("MULTI_REGION_ROUTES"));
    log.info("OpenAI API key: {}", System.getenv("OPENAI_API_KEY") != null ? "********" : "not set");
  }
}
