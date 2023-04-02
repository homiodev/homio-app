package org.homio.app.config;

import lombok.RequiredArgsConstructor;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

//TODO: remove later!!!!
//@Component
@RequiredArgsConstructor
public class TomcatHttpConnector implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

  private final AppProperties appProperties;

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
    connector.setPort(appProperties.getHttpPort());
    connector.setParseBodyMethods("POST,PUT,DELETE");
    factory.addAdditionalTomcatConnectors(connector);
  }
}
