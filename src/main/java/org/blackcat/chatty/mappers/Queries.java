package org.blackcat.chatty.mappers;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.blackcat.chatty.verticles.DataStoreVerticle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Queries {

    public static final Queries INSTANCE = new Queries();
    private static final Logger logger = LoggerFactory.getLogger(Queries.class);
    private Queries() {}

    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email - the user's email
     * @param handler
     */
    public static void findCreateUserEntityByEmail(Vertx vertx, String email, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_CREATE_USER_BY_EMAIL)
                .put("params", new JsonObject()
                        .put("email", email));
        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                UserMapper userMapper = obj.getJsonObject("result").mapTo(UserMapper.class);
                handler.handle(userMapper);
            }
        });
    }

    /**
     * Retrieves a Room entity by name, or creates a new one if no such entity exists.
     *
     * @param name - the room name
     * @param handler
     */
    public static void findCreateRoomByName(Vertx vertx, String name, Handler<RoomMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_CREATE_ROOM_BY_NAME)
                .put("params", new JsonObject()
                        .put("name", name));
        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                RoomMapper roomMapper = obj.getJsonObject("result").mapTo(RoomMapper.class);
                handler.handle(roomMapper);
            }
        });
    }

    /**
     * Retrieves a User entity by uuid.
     *
     * @param uuid
     * @param handler
     */
    public static void findUserByUUID(Vertx vertx, String uuid, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_USER_BY_UUID)
                .put("params", new JsonObject()
                        .put("uuid", uuid));
        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject obj = (JsonObject) reply.result().body();
                if (! Objects.isNull(obj)) {
                    UserMapper userMapper = obj.getJsonObject("result").mapTo(UserMapper.class);
                    handler.handle(userMapper);
                } else handler.handle(null);
            }
        });
    }

    /**
     * Retrieves a Room entity by uuid.
     *
     * @param uuid
     * @param handler
     */
    public static void findRoomByUUID(Vertx vertx, String uuid, Handler<RoomMapper> handler) {
        if (uuid.isEmpty()) {
            getGeneralRoomUUID(vertx, generalUUID -> {
                JsonObject query = new JsonObject()
                        .put("type", DataStoreVerticle.FIND_ROOM_BY_UUID)
                        .put("params", new JsonObject()
                                .put("uuid", generalUUID));

                logger.info(query);
                vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
                    if (reply.succeeded()) {
                        JsonObject obj = (JsonObject) reply.result().body();
                        if (! Objects.isNull(obj)) {
                            RoomMapper roomMapper = obj.getJsonObject("result").mapTo(RoomMapper.class);
                            handler.handle(roomMapper);
                        } else handler.handle(null);
                    }
                });
            });
        } else {
            JsonObject query = new JsonObject()
                    .put("type", DataStoreVerticle.FIND_ROOM_BY_UUID)
                    .put("params", new JsonObject()
                            .put("uuid", uuid));

            logger.info(query);
            vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
                if (reply.succeeded()) {
                    JsonObject obj = (JsonObject) reply.result().body();
                    if (! Objects.isNull(obj)) {
                        RoomMapper roomMapper = obj.getJsonObject("result").mapTo(RoomMapper.class);
                        handler.handle(roomMapper);
                    } else handler.handle(null);
                }
            });
        }
    }

    public static void getGeneralRoomUUID(Vertx vertx, Handler<String> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.GET_GENERAL_ROOM_UUID);

        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject obj = (JsonObject) reply.result().body();
                if (! Objects.isNull(obj)) {
                    String uuid = obj.getJsonObject("result").getString("uuid");
                    handler.handle(uuid);
                } else handler.handle(null);
            }
        });
    }

    /**
     * Records a new message: who said what, when and where.
     *
     * @param userMapper
     * @param timeStamp
     * @param messageText
     * @param handler
     */
    public static void recordMessage(Vertx vertx, UserMapper userMapper, String messageText, Instant timeStamp,
                               RoomMapper roomMapper, Handler<MessageMapper> handler) {

        Objects.requireNonNull(userMapper, "user is null");
        Objects.requireNonNull(messageText, "messageText is null");
        Objects.requireNonNull(timeStamp, "timeStamp is null");
        Objects.requireNonNull(roomMapper, "room is null");

        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.RECORD_MESSAGE)
                .put("params", new JsonObject()
                        .put("user", JsonObject.mapFrom(userMapper))
                        .put("messageText", messageText)
                        .put("timeStamp", timeStamp.toString())
                        .put("room", JsonObject.mapFrom(roomMapper)));

        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                MessageMapper messageMapper = body.getJsonObject("result").mapTo(MessageMapper.class);
                handler.handle(messageMapper); /* done */
            } else {
                final Throwable cause = reply.cause();
                logger.error(cause.toString());
            }
        });
    }

    /**
     * Fetches messages for a given room. User permissions shall be checked (TODO)
     *
     * @param userMapper
     * @param roomMapper
     * @param handler
     */
    public static void fetchMessages(Vertx vertx, UserMapper userMapper, RoomMapper roomMapper, Handler<List<MessageMapper>> handler) {
        Objects.requireNonNull(userMapper, "user is null");
        Objects.requireNonNull(roomMapper, "room is null");

        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FETCH_MESSAGES)
                .put("params", new JsonObject()
                        .put("roomUUID", roomMapper.getUuid()));

        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                final List<JsonObject> jsonObjects =
                        body.getJsonObject("result").getJsonArray("messages").getList();

                List<MessageMapper> messages = new ArrayList<>();
                for (JsonObject obj: jsonObjects) {
                    MessageMapper messageMapper = obj.mapTo(MessageMapper.class);
                    messages.add(messageMapper);
                }
                handler.handle(messages);
            } else {
                final Throwable cause = reply.cause();
                logger.error(cause.toString());
            }
        });
    }

    /**
     * Find all defined rooms.
     *
     * @param handler
     */
    public static void findRooms(Vertx vertx, Handler<List<RoomMapper>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_ROOMS)
                .put("params", new JsonObject());

        logger.info(query);
        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                final List<JsonObject> jsonObjects =
                        body.getJsonObject("result").getJsonArray("rooms").getList();

                handler.handle(jsonObjects.stream().map(obj -> obj.mapTo(RoomMapper.class))
                        .collect(Collectors.toList()));
            } else {
                final Throwable cause = reply.cause();
                logger.error(cause.toString());
            }
        });
    }
}
