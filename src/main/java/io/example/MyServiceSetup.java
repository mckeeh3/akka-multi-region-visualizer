package io.example;

import akka.javasdk.annotations.Setup;
import akka.javasdk.ServiceSetup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

@Setup
public class MyServiceSetup implements ServiceSetup {
  private final Logger log = LoggerFactory.getLogger(MyServiceSetup.class);
  private final Config config;

  public MyServiceSetup(Config config) {
    this.config = config;
  }

  @Override
  public void onStartup() {
    log.info("Service started");
    config.entrySet().stream().forEach(entry -> log.info("{} = {}", entry.getKey(), entry.getValue()));
  }
}
