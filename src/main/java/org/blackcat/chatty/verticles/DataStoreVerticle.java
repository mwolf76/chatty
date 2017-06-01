package org.blackcat.chatty.verticles;

import de.braintags.vertx.jomnigate.dataaccess.query.IQuery;
import de.braintags.vertx.jomnigate.dataaccess.query.ISearchCondition;
import de.braintags.vertx.jomnigate.dataaccess.write.IWrite;
import de.braintags.vertx.jomnigate.init.DataStoreSettings;
import de.braintags.vertx.jomnigate.mongo.MongoDataStore;
import de.braintags.vertx.jomnigate.util.QueryHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataStoreVerticle extends AbstractVerticle {

    final public static String ADDRESS = "webchat.data-store";

    /* canonical getters  */
    final public static String FIND_USER_BY_UUID = "find-user-by-uuid";
    final public static String FIND_ROOM_BY_UUID = "find-room-by-uuid";

    /* specific getters */
    final public static String FIND_CREATE_USER_BY_EMAIL = "find-create-user-by-email";
    final public static String FIND_CREATE_ROOM_BY_NAME = "find-create-room-by-name";

    /* message queries */
    final public static String RECORD_MESSAGE = "record-message";
    final public static String FETCH_MESSAGES = "fetch-messages";

    /* room queries */
    final public static String GET_GENERAL_ROOM_UUID = "get-general-room-uuid";
    final public static String FIND_ROOMS = "find-rooms";

    final public static String GENERAL_ROOM_NAME = "General";

    private Logger logger;
    private MongoDataStore mongoDataStore;

    private String generalRoomUUID;

    @Override
    public void start(Future<Void> startFuture) {

        logger = LoggerFactory.getLogger(DataStoreVerticle.class);
        vertx.executeBlocking(future -> {

            /* retrieve configuration object from vert.x ctx */
            final Configuration configuration = new Configuration(vertx.getOrCreateContext().config());

            /* connect to mongodb data store */
            String connectionString = String.format("%s://%s:%s",
                    configuration.getDatabaseType(),
                    configuration.getDatabaseHost(),
                    configuration.getDatabasePort());

            JsonObject mongodbConfig = new JsonObject()
                    .put("connection_string", connectionString)
                    .put("db_name", configuration.getDatabaseName());

            final DataStoreSettings dataStoreSettings =
                    new DataStoreSettings();

            MongoClient mongoClient = MongoClient.createShared(vertx, mongodbConfig);
            mongoDataStore = new MongoDataStore(vertx, mongoClient, new JsonObject(), dataStoreSettings);

            future.complete();
        }, res -> {
            if (res.succeeded()) {
                initData(_1 -> {
                    setupQueryDispatch(_2 -> {
                        startFuture.complete();
                    });
                });
            } else {
                Throwable cause = res.cause();
                logger.error(cause.toString());
                startFuture.fail(cause);
            }
        });
    }

    private void initData(Handler<AsyncResult<Void>> handler) {
        /* creator is null */
        findCreateRoomByName(new JsonObject().put("name", GENERAL_ROOM_NAME), asyncResult -> {
            if (asyncResult.failed())
                handler.handle(Future.failedFuture(asyncResult.cause()));

            final RoomMapper room = asyncResult.result();

            logger.info("General room is {}", room);
            generalRoomUUID = room.getUuid();

            handler.handle(null); /* done */
        });
    }

    private JsonObject makeFailure(String cause) {
        return new JsonObject()
                .put("failure", new JsonObject()
                        .put("cause", cause));
    }

    private JsonObject makeFailure(Throwable cause) {
        return new JsonObject()
                .put("failure", new JsonObject().put("cause",
                        cause.toString()));
    }

    private void setupQueryDispatch(Handler<AsyncResult<Void>> handler) {
        vertx.eventBus()
                .consumer(ADDRESS, msg -> {
                    JsonObject obj = (JsonObject) msg.body();
                    String queryType = obj.getString("type");
                    JsonObject params = obj.getJsonObject("params");

                    switch(queryType) {
                        case FIND_CREATE_USER_BY_EMAIL:
                            findCreateUserByEmail(params, asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                   reply = makeFailure(asyncResult.cause());
                                } else {
                                    final UserMapper user = asyncResult.result();
                                    reply = new JsonObject().put("result",
                                            Objects.isNull(user) ? null : JsonObject.mapFrom(user));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}({}) := {}", FIND_CREATE_USER_BY_EMAIL, params, reply);
                                msg.reply(reply);
                            });
                            break;

                        case FIND_CREATE_ROOM_BY_NAME:
                            findCreateRoomByName(params, asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                    reply = makeFailure(asyncResult.cause());
                                } else {
                                    final RoomMapper room = asyncResult.result();
                                    reply = new JsonObject().put("result",
                                            Objects.isNull(room) ? null : JsonObject.mapFrom(room));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}({}) := {}", FIND_CREATE_ROOM_BY_NAME, params, reply);
                                msg.reply(reply);
                            });
                            break;

                        case FIND_USER_BY_UUID:
                            findUserByUUID(params, asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                    reply = makeFailure(asyncResult.cause());
                                } else {
                                    final UserMapper user = asyncResult.result();
                                    reply = new JsonObject().put("result",
                                            Objects.isNull(user) ? null : JsonObject.mapFrom(user));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}({}) := {}", FIND_USER_BY_UUID, params, reply);
                                msg.reply(reply);
                            });
                            break;

                        case FIND_ROOM_BY_UUID:
                            findRoomByUUID(params,  asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                    reply = makeFailure(asyncResult.cause());
                                } else {
                                    final RoomMapper room = asyncResult.result();
                                    reply = new JsonObject().put("result",
                                            Objects.isNull(room) ? null : JsonObject.mapFrom(room));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}({}) := {}", FIND_ROOM_BY_UUID, params, reply);
                                msg.reply(reply);
                            });
                            break;

                        case FIND_ROOMS:
                            /* params unused here */
                            findRooms(params, asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                    reply = makeFailure(asyncResult.cause());
                                } else {
                                    /* lists of objects need to be explicitly mapped to an array of of json objects */
                                    final JsonArray rooms = new JsonArray(asyncResult.result()
                                            .stream().map(JsonObject::mapFrom).collect(Collectors.toList()));

                                    reply = new JsonObject().put("result",
                                            new JsonObject().put("rooms", rooms));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}() := {}", FIND_ROOMS, reply);
                                msg.reply(reply);
                            });
                            break;

                        case RECORD_MESSAGE:
                            recordMessage(params, asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                    reply = makeFailure(asyncResult.cause());
                                } else {
                                    final MessageMapper message = asyncResult.result();
                                    reply = new JsonObject().put("result",
                                            Objects.isNull(message) ? null : JsonObject.mapFrom(message));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}({}) := {}", RECORD_MESSAGE, params, reply);
                                msg.reply(reply);
                            });
                            break;

                        case FETCH_MESSAGES:
                            fetchMessages(params, asyncResult -> {
                                JsonObject reply;
                                if (asyncResult.failed()) {
                                    reply = makeFailure(asyncResult.cause());
                                } else {
                                    /* lists of objects need to be explicitly mapped to a list of json objects */
                                    final JsonArray messages = new JsonArray(asyncResult.result()
                                            .stream().map(JsonObject::mapFrom).collect(Collectors.toList()));

                                    reply = new JsonObject().put("result",
                                            new JsonObject().put("messages", messages));
                                }

                                Objects.requireNonNull(reply);
                                logger.debug("{}({}) := {}", FETCH_MESSAGES, params, reply);
                                msg.reply(reply);
                            });
                            break;

                        case GET_GENERAL_ROOM_UUID:
                            JsonObject reply;
                            if (Objects.isNull(generalRoomUUID)) {
                                reply = makeFailure("No room UUID available");
                            } else {
                                reply = new JsonObject()
                                        .put("result", new JsonObject()
                                                .put("uuid", this.generalRoomUUID));
                            }

                            Objects.requireNonNull(reply);
                            logger.debug("{} := {}", GET_GENERAL_ROOM_UUID, reply);
                            msg.reply(reply);
                            break;

                        default:
                            logger.error("Ignoring unsupported query type: {}", queryType);
                    } /* switch() */
                });

        handler.handle(null); /* done */
    } /* setupQueryDispatch() */

    private void findCreateUserByEmail(JsonObject params, Handler<AsyncResult<UserMapper>> handler) {

        /* fetch params */
        String email = params.getString("email");

        IQuery<UserMapper> query = mongoDataStore.createQuery(UserMapper.class);
        query.setSearchCondition(ISearchCondition.isEqual("email", email));

        QueryHelper.executeToFirstRecord(query, false, asyncResult -> {
            if (asyncResult.failed()) {
                handler.handle(Future.failedFuture(asyncResult.cause()));
            } else {
                final UserMapper result = asyncResult.result();
                if (result != null) {
                    handler.handle(Future.succeededFuture(result));
                } else {
                    /* User does not exist. Create a new record. */
                    UserMapper userMapper = new UserMapper();
                    userMapper.setUuid(UUID.randomUUID().toString());
                    userMapper.setEmail(email);

                    IWrite<UserMapper> write = mongoDataStore.createWrite(UserMapper.class);
                    write.add(userMapper);

                    write.save(writeAsyncResult -> {
                        if (writeAsyncResult.failed()) {
                            handler.handle(Future.failedFuture(writeAsyncResult.cause()));
                        } else {
                            handler.handle(Future.succeededFuture(userMapper));
                        }
                    });
                }
            }
        });
    }

    private void findCreateRoomByName(JsonObject params, Handler<AsyncResult<RoomMapper>> handler) {
        /* fetch params */
        String name = params.getString("name");

        IQuery<RoomMapper> query = mongoDataStore.createQuery(RoomMapper.class);
        query.setSearchCondition(ISearchCondition.isEqual("name", name));

        QueryHelper.executeToFirstRecord(query, false, asyncResult -> {
            if (asyncResult.failed()) {
                handler.handle(Future.failedFuture(asyncResult.cause()));
            } else {
                final RoomMapper result = asyncResult.result();
                if (result != null) {
                    handler.handle(Future.succeededFuture(result));
                } else {
                    /* Room does not exist. Create a new record. */
                    RoomMapper roomMapper = new RoomMapper();
                    roomMapper.setUuid(UUID.randomUUID().toString());
                    roomMapper.setName(name);

                    IWrite<RoomMapper> write = mongoDataStore.createWrite(RoomMapper.class);
                    write.add(roomMapper);

                    write.save(writeAsyncResult -> {
                        if (writeAsyncResult.failed()) {
                            handler.handle(Future.failedFuture(writeAsyncResult.cause()));
                        } else {
                            handler.handle(Future.succeededFuture(roomMapper));
                        }
                    });
                }
            }
        });
    }

    private void findUserByUUID(JsonObject params, Handler<AsyncResult<UserMapper>> handler) {
        /* fetch params */
        String uuid = params.getString("uuid");

        IQuery<UserMapper> query = mongoDataStore.createQuery(UserMapper.class);
        query.setSearchCondition(ISearchCondition.isEqual("uuid", uuid));

        QueryHelper.executeToFirstRecord(query, false, handler);
    }

    private void findRoomByUUID(JsonObject params, Handler<AsyncResult<RoomMapper>> handler) {
        /* fetch params */
        String uuid = params.getString("uuid");

        IQuery<RoomMapper> query = mongoDataStore.createQuery(RoomMapper.class);
        query.setSearchCondition(ISearchCondition.isEqual("uuid", uuid));

        QueryHelper.executeToFirstRecord(query, false, handler);
    }

    private void findRooms(JsonObject params, Handler<AsyncResult<List<RoomMapper>>> handler) {
        /* no params */
        IQuery<RoomMapper> query = mongoDataStore.createQuery(RoomMapper.class);
        QueryHelper.executeToList(query, handler);
    }

    private void recordMessage(JsonObject params, Handler<AsyncResult<MessageMapper>> handler) {
        /* fetch params */
        UserMapper user = params.getJsonObject("user").mapTo(UserMapper.class);
        String messageText = params.getString("messageText");
        String timeStamp = params.getString("timeStamp");
        RoomMapper room = params.getJsonObject("room").mapTo(RoomMapper.class);

        MessageMapper messageMapper = new MessageMapper();
        messageMapper.setAuthor(user);
        messageMapper.setText(messageText);
        messageMapper.setTimeStamp(timeStamp);
        messageMapper.setRoom(room);

        IWrite<MessageMapper> write = mongoDataStore.createWrite(MessageMapper.class);
        write.add(messageMapper);

        write.save(result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
            } else {
                logger.info("Recorded new message: {}", messageMapper.toString());
                handler.handle(Future.succeededFuture(messageMapper));
            }
        });
    }

    private void fetchMessages(JsonObject params, Handler<AsyncResult<List<MessageMapper>>> handler) {
        /* fetch params */
        String roomUUID = params.getString("roomUUID");
        findRoomByUUID(new JsonObject().put("uuid", roomUUID), asyncResult -> {

            if (asyncResult.failed()) {
                handler.handle(Future.failedFuture(asyncResult.cause()));
            } else {
                final RoomMapper room = asyncResult.result();
                IQuery<MessageMapper> query = mongoDataStore.createQuery(MessageMapper.class);

                query.setSearchCondition(ISearchCondition.isEqual("room", room.getUuid()));
                QueryHelper.executeToList(query, handler);
            }
        });
    }
}
