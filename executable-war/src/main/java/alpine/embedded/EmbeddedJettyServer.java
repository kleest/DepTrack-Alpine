/*
 * This file is part of Alpine.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package alpine.embedded;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.bridge.SLF4JBridgeHandler;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * The primary class that starts an embedded Jetty server
 * @author Steve Springett
 * @since 1.0.0
 */
public final class EmbeddedJettyServer {

    /**
     * Private constructor.
     */
    private EmbeddedJettyServer() {
    }

    public static void main(final String[] args) throws Exception {
        final CliArgs cliArgs = new CliArgs(args);
        final String contextPath = cliArgs.switchValue("-context", "/");
        final String host = cliArgs.switchValue("-host", "0.0.0.0");
        final int port = cliArgs.switchIntegerValue("-port", 8080);

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        final Server server = new Server();
        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer( new org.eclipse.jetty.server.ForwardedRequestCustomizer() ); // Add support for X-Forwarded headers

        final HttpConnectionFactory connectionFactory = new HttpConnectionFactory( httpConfig );
        final ServerConnector connector = new ServerConnector(server, connectionFactory);
        connector.setHost(host);
        connector.setPort(port);
        disableServerVersionHeader(connector);
        server.setConnectors(new Connector[]{connector});

        final WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath(contextPath);
        context.setErrorHandler(new ErrorHandler());
        context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*/[^/]*taglibs.*\\.jar$");
        context.setAttribute("org.eclipse.jetty.containerInitializers", jspInitializers());
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        context.addBean(new ServletContainerInitializersStarter(context), true);

        // Prevent loading of logging classes
        context.getSystemClasspathPattern().add("org.apache.log4j.");
        context.getSystemClasspathPattern().add("org.slf4j.");
        context.getSystemClasspathPattern().add("org.apache.commons.logging.");

        final ProtectionDomain protectionDomain = EmbeddedJettyServer.class.getProtectionDomain();
        final URL location = protectionDomain.getCodeSource().getLocation();
        context.setWar(location.toExternalForm());

        server.setHandler(context);
        server.addBean(new ErrorHandler());
        try {
            server.start();
            addJettyShutdownHook(server);
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static List<ContainerInitializer> jspInitializers() {
        final JettyJasperInitializer sci = new JettyJasperInitializer();
        final ContainerInitializer initializer = new ContainerInitializer(sci, null);
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(initializer);
        return initializers;
    }

    private static void disableServerVersionHeader(Connector connector) {
        connector.getConnectionFactories().stream()
                .filter(cf -> cf instanceof HttpConnectionFactory)
                .forEach(cf -> ((HttpConnectionFactory) cf)
                        .getHttpConfiguration().setSendServerVersion(false));
    }

    /**
     * Dummy error handler that disables any error pages or jetty related messages and an empty page with a status code.
     */
    private static class ErrorHandler extends ErrorPageErrorHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setStatus(response.getStatus());
        }
    }

    private static void addJettyShutdownHook(final Server server) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Shutting down application");
                    server.stop();
                } catch (Exception e) {
                    //System.err.println("Exception occurred shutting down: " + e.getMessage());
                }
            }
        });
    }
}
