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

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        List<AbstractVerticle> verticles = Arrays.asList(
                new DataStoreVerticle(),
                new PresenceVerticle(),
                new WebServerVerticle());

        AtomicInteger verticleCount = new AtomicInteger(verticles.size());
        JsonObject config = vertx.getOrCreateContext().config();

        verticles
                .stream()
                .forEach(verticle -> {
                    vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(config), deployResponse -> {
                        String simpleName = verticle.getClass().getSimpleName();
                        if (deployResponse.failed()) {
                            deployResponse.cause().printStackTrace();
                            logger.error("Unable to deploy verticle {} (cause: {})",
                                simpleName, deployResponse.cause());
                        } else {
                            logger.info("{} deployed successfully", verticle.getClass().getSimpleName());
                            if (verticleCount.decrementAndGet() == 0) {
                                logger.info("All services up and running.");
                                startFuture.complete();
                            }
                        }
                    });
                });

        Configuration configuration = new Configuration(config);
        logger.info("Configuration: {}", configuration.toString());

        int timeout = configuration.getStartTimeout();
        vertx.setTimer(TimeUnit.SECONDS.toMillis(timeout), event -> {
            if (verticleCount.get() != 0) {
                logger.error("One or more verticles could not be deployed within {} seconds. Aborting ...", timeout);
                vertx.close();
            }
        });
    }
}
