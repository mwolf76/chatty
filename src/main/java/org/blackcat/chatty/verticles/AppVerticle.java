package org.blackcat.chatty.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.templ.PebbleTemplateEngine;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.http.RequestHandler;

import java.net.URL;
import java.net.URLClassLoader;

public class AppVerticle extends AbstractVerticle {

    private Logger logger;

    @Override
    public void start(Future<Void> startFuture) {

        logger = LoggerFactory.getLogger(AppVerticle.class);
        vertx.executeBlocking(future -> {

            ClassLoader cl = ClassLoader.getSystemClassLoader();
            URL[] urls = ((URLClassLoader)cl).getURLs();
            for(URL url: urls){
                logger.debug(url.getFile());
            }

            /* retrieve configuration object from vert.x ctx */
            Configuration configuration = new Configuration(vertx.getOrCreateContext().config());

            /* configure Pebble template engine */
            PebbleTemplateEngine pebbleEngine = PebbleTemplateEngine.create(vertx);

            /* configure request handler */
            Handler<HttpServerRequest> handler =
                    new RequestHandler(vertx, pebbleEngine, logger, configuration);

            HttpServerOptions httpServerOptions = new HttpServerOptions()
                    // in Vert.x 2x 100-continues was activated per default, in vert.x 3x it is off per default.
                    .setHandle100ContinueAutomatically(true);

            if (configuration.sslEnabled()) {
                httpServerOptions
                        .setSsl(true)
                        .setKeyStoreOptions(
                                new JksOptions()
                                        .setPath(configuration.getKeystoreFilename())
                                        .setPassword(configuration.getKeystorePassword()));
            }

            vertx.createHttpServer(httpServerOptions)
                    .requestHandler(handler)
                    .listen(configuration.getHttpPort(), result -> {
                        if (result.succeeded()) {
                            logger.info("Ready to accept requests on port {}.",
                                    String.valueOf(configuration.getHttpPort()));
                            future.complete();
                        } else {
                            future.fail(result.cause());
                        }
                    });

        }, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            }
            else {
                Throwable cause = res.cause();
                startFuture.fail(cause);
            }
        });
    }
}
