package org.blackcat.chatty.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.mappers.Queries;
import org.blackcat.chatty.mappers.UserMapper;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

// TODO: integrate configuration
public class PresenceVerticle extends AbstractVerticle {

    final public static String ADDRESS = "webchat.presence";

    private Logger logger;
    private RedisClient redisClient;

    @Override
    public void start(Future<Void> startFuture) {
        EventBus eventBus = vertx.eventBus();

        logger = LoggerFactory.getLogger(PresenceVerticle.class);
        vertx.executeBlocking(future -> {

            /* retrieve configuration object from vert.x ctx */
            final Configuration configuration = new Configuration(vertx.getOrCreateContext().config());

            RedisOptions config = new RedisOptions()
                    .setHost("127.0.0.1");

            redisClient = RedisClient.create(vertx, config).select(0, done -> {
                logger.info("selected database 0");
            });

            eventBus.consumer(ADDRESS, msg -> {
                JsonObject params = (JsonObject) msg.body();
                updateUserPresence(params, done -> {
                });
            });

            future.complete();
        }, res -> {
            if (res.succeeded()) {
                initPeriodicUpdates(done -> {
                    startFuture.complete();
                });

            } else {
                Throwable cause = res.cause();
                logger.error(cause.toString());
                startFuture.fail(cause);
            }
        });
    }

    private void initPeriodicUpdates(Handler<Void> handler) {
        EventBus eventBus = vertx.eventBus();
        vertx.setPeriodic(2000, id -> {
            redisClient.keys("*", arrayAsyncResult -> {
                if (arrayAsyncResult.succeeded()) {
                    JsonArray jsonArray = arrayAsyncResult.result();

                    vertx.executeBlocking(future -> {
                        Map<String, List<String>> map = new HashMap<>();
                        jsonArray.stream().forEach(x -> {
                            String[] split = ((String) x).split(":");
                            String userID = split[0];
                            String roomID = split[1];

                            List<String> users = map.get(roomID);
                            if (users == null) {
                                users = new ArrayList<>();
                                users.add(userID);
                                map.put(roomID, users);
                            } else if (! users.contains(userID)) {
                                users.add(userID);
                            }
                        });

                        future.complete(map);
                    }, objectAsyncResult -> {
                        if (objectAsyncResult.succeeded()) {
                            final Map<String, List<String>> map =
                                    (Map<String, List<String>>) objectAsyncResult.result();

                            /* publish updates for all known rooms */
                            for (Map.Entry<String, List<String>> stringListEntry : map.entrySet()) {
                                String roomID = stringListEntry.getKey();
                                List<String> userIDs = stringListEntry.getValue();

                                /* first link of the chain */
                                Future<Void> initFuture = Future.future(event -> {
                                    logger.info("Started presence updates ...");
                                });
                                Future<Void> prevFuture = initFuture;
                                for (String userID : userIDs) {

                                    Future chainFuture = Future.future();
                                    prevFuture.compose(v -> {
                                        chainFuture.complete();
                                    }, chainFuture);

                                    prevFuture = chainFuture;
                                }
                                prevFuture.compose(v -> {
                                    final JsonArray users = new JsonArray(userIDs);
                                    eventBus.publish("webchat.partakers." + roomID, new JsonObject()
                                            .put("users", users));
                                }, initFuture);

                                /* let's get this thing started ... */
                                initFuture.complete();
                            }
                        }
                    });
                }
            });
        });

        logger.info("Initialized presence periodic updates");
        handler.handle(null);
    }

    private void updateUserPresence(JsonObject params, Handler<Void> handler) {
        /* fetch params */
        String userID = params.getString("userID");
        String roomID = params.getString("roomID");
        String key = userID + ":" + roomID;
        redisClient.psetex(key, 30000, "", done -> {
            logger.info(key);
            handler.handle(null);
        });
    }
}
