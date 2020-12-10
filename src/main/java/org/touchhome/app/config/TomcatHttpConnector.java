package org.touchhome.app.config;

import lombok.RequiredArgsConstructor;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TomcatHttpConnector implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final TouchHomeProperties touchHomeProperties;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(touchHomeProperties.getHttpPort());
        connector.setParseBodyMethods("POST,PUT,DELETE");
        factory.addAdditionalTomcatConnectors(connector);
    }
}
