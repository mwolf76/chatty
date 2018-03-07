package org.blackcat.chatty.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.http.requests.MainHandler;

public class WebServerVerticle extends AbstractVerticle {

    final private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void start(Future<Void> startFuture) {

        vertx.executeBlocking(future -> {

            /* retrieve configuration object from vert.x ctx */
            Configuration configuration = new Configuration(vertx.getOrCreateContext().config());

            HttpServerOptions httpServerOptions =
                new HttpServerOptions()
                    // in vertx 2x 100-continues was activated per default, in vertx 3x it is off per default.
                    .setHandle100ContinueAutomatically(true);

            boolean sslEnabled = configuration.isSSLEnabled();
            if (sslEnabled) {
                String keystoreFilename = configuration.getKeystoreFilename();
                String keystorePassword = configuration.getKeystorePassword();

                httpServerOptions
                    .setSsl(true)
                    .setKeyStoreOptions(
                        new JksOptions()
                            .setPath(keystoreFilename)
                            .setPassword(keystorePassword));
            }

            MainHandler mainHandler = MainHandler.create(vertx, configuration);
            int httpPort = configuration.getHttpPort();
            vertx.createHttpServer(httpServerOptions)
                    .requestHandler(mainHandler)
                    .listen(httpPort, result -> {
                        if (result.succeeded()) {
                            logger.info("Web server is now ready to accept requests on port {} {}.",
                                httpPort, sslEnabled ? "(ssl enabled)" : "(ssl disabled)");
                            future.complete();
                        } else {
                            future.fail(result.cause());
                        }
                    });

        }, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(res.cause());
            }
        });
    }
}
