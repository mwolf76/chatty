package org.blackcat.chatty.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.blackcat.chatty.conf.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        List<AbstractVerticle> verticles = Arrays.asList(
                new DataStoreVerticle(),
                new PresenceVerticle(),
                new WebVerticle());

        AtomicInteger verticleCount = new AtomicInteger(verticles.size());

        /* retrieve configuration object from vert.x ctx */
        JsonObject config = vertx.getOrCreateContext().config();

        verticles
                .stream()
                .forEach(verticle -> {
                    vertx.deployVerticle(verticle, new DeploymentOptions()
                            .setConfig(config), deployResponse -> {
                        if (deployResponse.failed()) {
                            deployResponse.cause().printStackTrace();
                            logger.error("Unable to deploy verticle {} (cause: {})",
                                    verticle.getClass().getSimpleName(),
                                    deployResponse.cause());
                        } else {
                            logger.info("{} deployed successfully", verticle.getClass().getSimpleName());

                            if (verticleCount.decrementAndGet() == 0) {
                                startFuture.complete();
                            }
                        }
                    });
                });

        Configuration configuration = new Configuration(config);
        logger.info("Configuration: {}", configuration.toString());

        int timeout = configuration.getTimeout();
        vertx.setTimer(TimeUnit.SECONDS.toMillis(timeout), event -> {
            if (verticleCount.get() != 0) {
                logger.error("One or more verticles could not be deployed within {} seconds. Aborting ...", timeout);
                vertx.close();
            }
        });
    }
}
