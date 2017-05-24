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
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.mappers.UserMapper;

import java.util.UUID;

public class DataStoreVerticle extends AbstractVerticle {

    final public static String ADDRESS = "data-store";
    final public static String FIND_CREATE_USER_BY_EMAIL = "find-create-user-by-email";
    final public static String FIND_USER_BY_UUID = "find-user-by-uuid";

    private Logger logger;
    private MongoDataStore mongoDataStore;

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
                                msg.reply(user != null ? JsonObject.mapFrom(user) : null);
                            });
                        } else if (queryType.equals(FIND_USER_BY_UUID)) {
                            findUserByUUID(params, user -> {
                                msg.reply(user != null ? JsonObject.mapFrom(user) : null);
                            });
                        } else {
                            logger.error("Unsupported query type: {}", queryType);
                        }
                    });

            future.complete();
        }, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                Throwable cause = res.cause();
                startFuture.fail(cause);
            }
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

                            logger.trace("Found matching user for {}: {}", email, userMapper);
                            handler.handle(userMapper);
                        }
                    });
                } else {
                    /* User does not exist. create it */
                    UserMapper userMapper = new UserMapper();
                    userMapper.setEmail(email);
                    userMapper.setUuid(UUID.randomUUID().toString());

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

                            logger.trace("Created new userMapper for {}: {}", email, entry.getStoreObject());
                            handler.handle(userMapper);
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

                            logger.trace("Found matching user for {}: {}", uuid, userMapper);
                            handler.handle(userMapper);
                        }
                    });
                } else {
                    /* User does not exist. */
                    logger.trace("No user found for {}", uuid);
                    handler.handle(null);
                }
            }
        });
    }
}
