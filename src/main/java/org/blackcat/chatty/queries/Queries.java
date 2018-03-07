package org.blackcat.chatty.queries;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.verticles.DataStoreVerticle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Queries {

    private static final String malformedReplyMessage = "Malformed reply message";

    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email - the user's email
     * @param handler
     */
    public static void findCreateUserEntityByEmail(Vertx vertx, String email, Handler<AsyncResult<UserMapper>> handler) {
        JsonObject query = new JsonObject()
                               .put("type", DataStoreVerticle.FIND_CREATE_USER_BY_EMAIL)
                               .put("params", new JsonObject()
                                                  .put("email", email));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                final JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        final UserMapper user = obj.getJsonObject("result").mapTo(UserMapper.class);
                        handler.handle(Future.succeededFuture(user));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
            }
        });
    }

    /**
     * Retrieves a Room entity by name, or creates a new one if no such entity exists.
     *
     * @param name - the room name
     * @param handler
     */
    public static void findCreateRoomByName(Vertx vertx, String name, Handler<AsyncResult<RoomMapper>> handler) {
        JsonObject query = new JsonObject()
                               .put("type", DataStoreVerticle.FIND_CREATE_ROOM_BY_NAME)
                               .put("params", new JsonObject()
                                                  .put("name", name));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                final JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        final RoomMapper room = result.mapTo(RoomMapper.class);
                        handler.handle(Future.succeededFuture(room));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
            }
        });
    }

    /**
     * Retrieves a User entity by uuid.
     *
     * @param uuid
     * @param handler
     */
    public static void findUserByUUID(Vertx vertx, String uuid, Handler<AsyncResult<UserMapper>> handler) {
        JsonObject query = new JsonObject()
                               .put("type", DataStoreVerticle.FIND_USER_BY_UUID)
                               .put("params", new JsonObject()
                                                  .put("uuid", uuid));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        final UserMapper user = result.mapTo(UserMapper.class);
                        handler.handle(Future.succeededFuture(user));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
            }
        });
    }


    /**
     * Retrieves a Room entity by uuid.
     *
     * @param uuid
     * @param handler
     */
    public static void findRoomByUUID(Vertx vertx, String uuid, Handler<AsyncResult<RoomMapper>> handler) {
        JsonObject query = new JsonObject()
                               .put("type", DataStoreVerticle.FIND_ROOM_BY_UUID)
                               .put("params", new JsonObject()
                                                  .put("uuid", uuid));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        RoomMapper room = result.mapTo(RoomMapper.class);
                        handler.handle(Future.succeededFuture(room));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
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
                                     RoomMapper roomMapper, Handler<AsyncResult<MessageMapper>> handler) {

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

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        final MessageMapper message = result.mapTo(MessageMapper.class);
                        handler.handle(Future.succeededFuture(message));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
            }
        });
    }

    /**
     * Retrieve general room UUID
     *
     * @param vertx
     * @param handler
     */
    public static void getGeneralRoomUUID(Vertx vertx, Handler<AsyncResult<String>> handler) {
        JsonObject query = new JsonObject()
                               .put("type", DataStoreVerticle.GET_GENERAL_ROOM_UUID);

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        final String uuid = result.getString("uuid");
                        handler.handle(Future.succeededFuture(uuid));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
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
    public static void fetchMessages(Vertx vertx, UserMapper userMapper,
                                     RoomMapper roomMapper, Handler<AsyncResult<List<MessageMapper>>> handler) {

        Objects.requireNonNull(userMapper, "user is null");
        Objects.requireNonNull(roomMapper, "room is null");

        JsonObject query = new JsonObject()
                               .put("type", DataStoreVerticle.FETCH_MESSAGES)
                               .put("params", new JsonObject()
                                                  .put("roomUUID", roomMapper.getUuid()));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                Objects.requireNonNull(obj);

                final JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    final JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        final String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(malformedReplyMessage));
                    }
                } else {
                    try {
                        final List<JsonObject> jsonObjects =
                            result.getJsonArray("messages").getList();

                        final List<MessageMapper> messages =
                            jsonObjects
                                .stream()
                                .map(x -> x.mapTo(MessageMapper.class))
                                .collect(Collectors.toList());

                        handler.handle(Future.succeededFuture(messages));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
            }
        });
    }
}
