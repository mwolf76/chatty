package org.blackcat.chatty.verticles;

import de.braintags.io.vertx.pojomapper.dataaccess.query.IQuery;
import de.braintags.io.vertx.pojomapper.dataaccess.query.IQueryResult;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWrite;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWriteEntry;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWriteResult;
import de.braintags.io.vertx.pojomapper.mongo.MongoDataStore;
import io.vertx.core.AbstractVerticle;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DataStoreVerticle extends AbstractVerticle {

    final public static String ADDRESS = "data-store";

    /* queries */
    final public static String FIND_CREATE_USER_BY_EMAIL = "find-create-user-by-email";
    final public static String FIND_CREATE_ROOM_BY_NAME = "find-create-room-by-name";
    final public static String FIND_USER_BY_UUID = "find-user-by-uuid";
    final public static String FIND_ROOM_BY_UUID = "find-room-by-uuid";

    final public static String RECORD_MESSAGE = "record-message";
    final public static String FETCH_MESSAGES = "fetch-messages";

    final public static String GET_GENERAL_ROOM_UUID = "get-general-room-uuid";

    private Logger logger;
    private MongoDataStore mongoDataStore;

    private String generalRoomUUID;

    @Override
    public void start(Future<Void> startFuture) {

        logger = LoggerFactory.getLogger(DataStoreVerticle.class);
        vertx.executeBlocking(future -> {

            /* retrieve configuration object from vert.x ctx */
            final Configuration configuration = new Configuration(vertx.getOrCreateContext().config());

            /* connect to mongo data store */
            String connectionString = String.format("%s://%s:%s",
                    configuration.getDatabaseType(),
                    configuration.getDatabaseHost(),
                    configuration.getDatabasePort());

            JsonObject mongoConfig = new JsonObject()
                    .put("connection_string", connectionString)
                    .put("db_name", configuration.getDatabaseName());

            MongoClient mongoClient = MongoClient.createShared(vertx, mongoConfig);
            mongoDataStore = new MongoDataStore(vertx, mongoClient, mongoConfig);

            vertx.eventBus()
                    .consumer(ADDRESS, msg -> {
                        JsonObject obj = (JsonObject) msg.body();
                        String queryType = obj.getString("type");
                        JsonObject params = obj.getJsonObject("params");

                        /* msg dispatch */
                        if (queryType.equals(FIND_CREATE_USER_BY_EMAIL)) {
                            findCreateUserByEmail(params, user -> {
                                msg.reply(new JsonObject().put("result",
                                        Objects.isNull(user) ? null : JsonObject.mapFrom(user)));
                            });
                        } else if (queryType.equals(FIND_USER_BY_UUID)) {
                            findUserByUUID(params, user -> {
                                msg.reply(new JsonObject().put("result",
                                        Objects.isNull(user) ? null : JsonObject.mapFrom(user)));
                            });
                        } else if (queryType.equals(FIND_ROOM_BY_UUID)) {
                            findRoomByUUID(params, room -> {
                                msg.reply(new JsonObject().put("result",
                                        Objects.isNull(room) ? null : JsonObject.mapFrom(room)));
                            });
                        } else if (queryType.equals(RECORD_MESSAGE)) {
                            recordMessage(params, message -> {
                                msg.reply(new JsonObject().put("result",
                                        Objects.isNull(message) ? null : JsonObject.mapFrom(message)));
                            });
                        } else if (queryType.equals(FETCH_MESSAGES)) {
                            fetchMessages(params, messages -> {
                                JsonArray jsonArray = new JsonArray();
                                for (MessageMapper message: messages) {
                                    jsonArray.add(JsonObject.mapFrom(message));
                                }

                                msg.reply(new JsonObject().put("result",
                                        new JsonObject().put("messages", jsonArray)));
                            });
                        } else if (queryType.equals(GET_GENERAL_ROOM_UUID)) {
                            msg.reply(new JsonObject()
                                    .put("result", new JsonObject()
                                            .put("uuid", this.generalRoomUUID)));
                        } else {
                            logger.error("Unsupported query type: {}", queryType);
                        }
                    });

            future.complete();
        }, res -> {
            if (res.succeeded()) {
                initData(done -> {
                    startFuture.complete();
                });

            } else {
                Throwable cause = res.cause();
                startFuture.fail(cause);
            }
        });
    }

    private void initData(Handler<Void> handler) {
        findCreateRoomByName(new JsonObject().put("name", "general"), room -> {
            logger.info("General room is {}", room);
            generalRoomUUID = room.getUuid();
            handler.handle(null);
        });
    }

    private void findCreateUserByEmail(JsonObject params, Handler<UserMapper> handler) {

        /* fetch params */
        String email = params.getString("email");

        IQuery<UserMapper> query = mongoDataStore.createQuery(UserMapper.class);
        query.field("email").is(email);
        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<UserMapper> queryResult = queryAsyncResult.result();
                if (!queryResult.isEmpty()) {
                    queryResult.iterator().next(nextAsyncResult -> {
                        if (nextAsyncResult.failed()) {
                            Throwable cause = nextAsyncResult.cause();

                            logger.error(cause);
                            throw new RuntimeException(cause);
                        } else {
                            UserMapper userMapper = nextAsyncResult.result();

                            logger.debug("Found matching user for {}: {}", email, userMapper);
                            handler.handle(userMapper);
                        }
                    });
                } else {
                    /* User does not exist. create it */
                    UserMapper userMapper = new UserMapper();
                    userMapper.setUuid(UUID.randomUUID().toString());
                    userMapper.setEmail(email);

                    IWrite<UserMapper> write = mongoDataStore.createWrite(UserMapper.class);
                    write.add(userMapper);

                    write.save(result -> {
                        if (result.failed()) {
                            Throwable cause = result.cause();

                            logger.error(cause.toString());
                            throw new RuntimeException(cause);
                        } else {
                            IWriteResult writeResult = result.result();
                            IWriteEntry entry = writeResult.iterator().next();

                            logger.debug("Created new userMapper for {}: {}", email, entry.getStoreObject());
                            handler.handle(userMapper);
                        }
                    });
                }
            }
        });
    }

    private void findCreateRoomByName(JsonObject params, Handler<RoomMapper> handler) {

        /* fetch params */
        String name = params.getString("name");

        IQuery<RoomMapper> query = mongoDataStore.createQuery(RoomMapper.class);
        query.field("name").is(name);
        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<RoomMapper> queryResult = queryAsyncResult.result();
                if (!queryResult.isEmpty()) {
                    queryResult.iterator().next(nextAsyncResult -> {
                        if (nextAsyncResult.failed()) {
                            Throwable cause = nextAsyncResult.cause();

                            logger.error(cause);
                            throw new RuntimeException(cause);
                        } else {
                            RoomMapper roomMapper = nextAsyncResult.result();

                            logger.debug("Found matching room for {}: {}", name, roomMapper);
                            handler.handle(roomMapper);
                        }
                    });
                } else {
                    /* Room does not exist. create it */
                    RoomMapper roomMapper = new RoomMapper();
                    roomMapper.setUuid(UUID.randomUUID().toString());
                    roomMapper.setName(name);

                    IWrite<RoomMapper> write = mongoDataStore.createWrite(RoomMapper.class);
                    write.add(roomMapper);

                    write.save(result -> {
                        if (result.failed()) {
                            Throwable cause = result.cause();

                            logger.error(cause.toString());
                            throw new RuntimeException(cause);
                        } else {
                            IWriteResult writeResult = result.result();
                            IWriteEntry entry = writeResult.iterator().next();

                            logger.debug("Created new room for {}: {}", name, roomMapper);
                            handler.handle(roomMapper);
                        }
                    });
                }
            }
        });
    }

    private void findUserByUUID(JsonObject params, Handler<UserMapper> handler) {

        /* fetch params */
        String uuid = params.getString("uuid");

        IQuery<UserMapper> query = mongoDataStore.createQuery(UserMapper.class);
        query.field("uuid").is(uuid);
        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<UserMapper> queryResult = queryAsyncResult.result();
                if (!queryResult.isEmpty()) {
                    queryResult.iterator().next(nextAsyncResult -> {
                        if (nextAsyncResult.failed()) {
                            Throwable cause = nextAsyncResult.cause();

                            logger.error(cause);
                            throw new RuntimeException(cause);
                        } else {
                            UserMapper userMapper = nextAsyncResult.result();

                            logger.debug("Found matching user for {}: {}", uuid, userMapper);
                            handler.handle(userMapper);
                        }
                    });
                } else {
                    /* User does not exist. */
                    logger.warn("No user found for {}", uuid);
                    handler.handle(null);
                }
            }
        });
    }

    private void findRoomByUUID(JsonObject params, Handler<RoomMapper> handler) {

        /* fetch params */
        String uuid = params.getString("uuid");

        IQuery<RoomMapper> query = mongoDataStore.createQuery(RoomMapper.class);
        query.field("uuid").is(uuid);
        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<RoomMapper> queryResult = queryAsyncResult.result();
                if (!queryResult.isEmpty()) {
                    queryResult.iterator().next(nextAsyncResult -> {
                        if (nextAsyncResult.failed()) {
                            Throwable cause = nextAsyncResult.cause();

                            logger.error(cause);
                            throw new RuntimeException(cause);
                        } else {
                            RoomMapper roomMapper = nextAsyncResult.result();

                            logger.debug("Found matching room for {}: {}", uuid, roomMapper);
                            handler.handle(roomMapper);
                        }
                    });
                } else {
                    /* Room does not exist. */
                    logger.warn("No room found for {}", uuid);
                    handler.handle(null);
                }
            }
        });
    }
    
    private void recordMessage(JsonObject params, Handler<MessageMapper> handler) {

        /* fetch params */
        UserMapper user = params.getJsonObject("user").mapTo(UserMapper.class);
        String messageText = params.getString("messageText");
        Instant timeStamp = params.getInstant("timeStamp");
        RoomMapper room = params.getJsonObject("room").mapTo(RoomMapper.class);

        MessageMapper messageMapper = new MessageMapper();
        messageMapper.setAuthor(user);
        messageMapper.setText(messageText);
        messageMapper.setTimeStamp(timeStamp.toString());
        messageMapper.setRoom(room);

        IWrite<MessageMapper> write = mongoDataStore.createWrite(MessageMapper.class);
        write.add(messageMapper);

        write.save(result -> {
            if (result.failed()) {
                Throwable cause = result.cause();

                logger.error(cause.toString());
                throw new RuntimeException(cause);
            } else {
                IWriteResult writeResult = result.result();
                IWriteEntry entry = writeResult.iterator().next();

                logger.info("Recorded new message: {}", messageMapper.toString());
                handler.handle(messageMapper);
            }
        });
    }

    private void fetchMessages(JsonObject params, Handler<List<MessageMapper>> handler) {

        /* fetch params */
        String roomUUID = params.getString("roomUUID");

        IQuery<MessageMapper> query = mongoDataStore.createQuery(MessageMapper.class);
        // query.field("roomUUID").is(roomUUID);

        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<MessageMapper> queryResult = queryAsyncResult.result();
                queryResult.toArray(arrayAsyncResult -> {
                    if (arrayAsyncResult.failed()) {
                        Throwable cause = arrayAsyncResult.cause();

                        logger.error(cause);
                        throw new RuntimeException(cause);
                    } else {
                        Object[] objects = arrayAsyncResult.result();
                        MessageMapper[] messages = Arrays.copyOf(objects, objects.length, MessageMapper[].class);
                        handler.handle(Arrays.asList(messages));
                    }
                });
            }
        });
    }
}
