package org.blackcat.chatty.verticles;

import com.mitchellbosecke.pebble.PebbleEngine;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.mappers.RoomMapper;

import java.util.*;
import java.util.stream.Collectors;

public class PresenceVerticle extends AbstractVerticle {

    final public static String ADDRESS = "webchat.presence";

    /* queries */
    final public static String UPDATE_PRESENCE = "update-presence";

    /* how long does a presence message persist? */
    final private static int PRESENCE_PERSISTENCE_DURATION = 2000; /* ms */

    /* how long between presence broadcast updates? */
    final private static int PRESENCE_BROADCAST_INTERVAL = 1000; /* ms */
    final private static int ROOMLIST_BROADCAST_INTERVAL = 1000; /* ms */

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
                    .setHost(configuration.getRedisHost());

            int databaseIndex = configuration.getRedisDatabaseIndex();
            redisClient = RedisClient.create(vertx, config).select(databaseIndex, done -> {
                logger.info("Redis client initialized, selected database {}", databaseIndex);
            });

            eventBus.consumer(ADDRESS, msg -> {
                JsonObject obj = (JsonObject) msg.body();
                String queryType = obj.getString("type");
                JsonObject params = obj.getJsonObject("params");

                /* msg dispatch */
                if (queryType.equals(UPDATE_PRESENCE)) {
                    updateUserPresence(params, done -> {
                        logger.debug("Received presence update message: {}",
                                params.toString());
                    });
                } else {
                    logger.error("Unsupported query type: {}", queryType);
                }
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

        /* setting up presence broadcast */
        vertx.setPeriodic(PRESENCE_BROADCAST_INTERVAL, tick -> {
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
                                    logger.debug("Started presence updates ...");
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
                                    final String channel = "webchat.partakers." + roomID;
                                    eventBus.publish(channel, new JsonObject().put("users", users));
                                    logger.info("{} <-- {}", channel, users.toString());
                                }, initFuture);

                                /* let's get this thing started ... */
                                initFuture.complete();
                            }
                        }
                    });
                }
            });
        });

        /* setting up room list broadcast */
        vertx.setPeriodic(ROOMLIST_BROADCAST_INTERVAL, tick -> {
            findRooms(vertx, roomsAsyncResult -> {
                if (roomsAsyncResult.failed()) {
                    logger.error(roomsAsyncResult.cause().toString());
                } else {
                    final List<RoomMapper> rooms = roomsAsyncResult.result();
                    eventBus.publish("webchat.rooms", new JsonObject().put("rooms", new JsonArray(rooms
                            .stream().map(JsonObject::mapFrom).collect(Collectors.toList()))));
                }
            });
        });

        logger.info("Initialized presence periodic updates");
        handler.handle(null);
    }

    private void updateUserPresence(JsonObject params, Handler<Void> handler) {
        String userID = params.getString("userID");
        String roomID = params.getString("roomID");

        String key = userID + ":" + roomID;
        redisClient.psetex(key, PRESENCE_PERSISTENCE_DURATION, "", done -> {
            logger.debug("Key {}", key);
            handler.handle(null);
        });
    }

    /**
     * Find all defined rooms.
     *
     * @param handler
     */
    private void findRooms(Vertx vertx, Handler<AsyncResult<List<RoomMapper>>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_ROOMS)
                .put("params", new JsonObject());

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Empty reply message"));
                } else {
                    final JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        try {
                            final JsonArray jsonArray = result.getJsonArray("rooms");
                            if (Objects.isNull(jsonArray)) {
                                handler.handle(Future.failedFuture("Null rooms list"));
                            } else {
                                final List<JsonObject> list = jsonArray.getList();
                                final List<RoomMapper> rooms = list.stream()
                                        .map(x -> x.mapTo(RoomMapper.class))
                                        .collect(Collectors.toList());

                                handler.handle(Future.succeededFuture(rooms));
                            }
                        } catch (ClassCastException cce) {
                            handler.handle(Future.failedFuture(cce));
                        }
                    }
                }
            }
        });
    }
}
