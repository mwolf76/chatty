package org.blackcat.chatty.http;

import com.mitchellbosecke.pebble.PebbleEngine;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.util.HtmlEscape;
import org.blackcat.chatty.util.Utils;
import org.blackcat.chatty.verticles.DataStoreVerticle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.blackcat.chatty.util.Utils.urlDecode;

public class RequestHandler implements Handler<HttpServerRequest> {

    private Vertx vertx;
    private Router router;
    private TemplateEngine templateEngine;
    private Logger logger;

    public RequestHandler(final Vertx vertx, final TemplateEngine templateEngine,
                          final Logger logger, final Configuration configuration) {

        this.vertx = vertx;
        this.router = Router.router(vertx);
        this.templateEngine = templateEngine;
        this.logger = logger;

        // We need cookies, sessions and request bodies
        router.route()
                .handler(CookieHandler.create());

        // avoid reading, sniffing, hijacking or tampering your sessions.
        router.route()
                .handler(SessionHandler
                        .create(LocalSessionStore.create(vertx))
                        .setCookieHttpOnlyFlag(true)
                        .setCookieSecureFlag(true));

        // Allow events for the designated addresses in/out of the event bus bridge
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex("webchat.*"))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("webchat.*"));

        // Create the event bus bridge and add it to the router.
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // Router setup
        setupRouter(vertx, router, configuration);

        // Register to listen for messages coming IN to the server
        final EventBus eventBus = vertx.eventBus();
        eventBus.consumer("webchat.server").handler(event -> {
            JsonObject jsonObject = new JsonObject((String) event.body());

            String userID = jsonObject.getString("userID");
            String roomID = jsonObject.getString("roomID");
            String text = jsonObject.getString("text");

            findUserByUUID(vertx, userID, userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed()) {
                    logger.error(userMapperAsyncResult.cause().toString());
                } else {
                    final UserMapper user = userMapperAsyncResult.result();

                    findRoomByUUID(vertx, roomID, roomMapperAsyncResult -> {
                        if (roomMapperAsyncResult.failed()) {
                            logger.error(roomMapperAsyncResult.cause().toString());
                        } else {
                            final RoomMapper room = roomMapperAsyncResult.result();

                            recordMessage(vertx, user, text, Instant.now(), room, messageMapperAsyncResult -> {
                                if (messageMapperAsyncResult.failed()) {
                                    logger.error(messageMapperAsyncResult.cause().toString());
                                } else {
                                    final MessageMapper message = messageMapperAsyncResult.result();

                                    eventBus.publish("webchat.client", new JsonObject()
                                            .put("roomID", roomID)
                                            .put("displayText", formatMessage(message)));
                                }
                            });
                        }
                    });
                }
            });
        });

        /* internal index handler */
        router
                .get("/protected/protectedIndex")
                .handler(this::protectedIndex);

        router
                .getWithRegex("/protected/history/.*")
                .handler(this::history);

        /* extra handlers */
        router
                .getWithRegex("/static/.*")
                .handler(StaticHandler.create());

        /* invalid URL */
        router
                .getWithRegex(".*")
                .handler(this::notFound);

       /* invalid method */
        router
                .routeWithRegex(".*")
                .handler(this::notAllowed);

        /* errors */
        router
                .route()
                .failureHandler(this::internalServerError);
    }

    private String formatPlainMessage(MessageMapper messageMapper) {
        return MessageFormat.format("{0} <{1}>: {2}",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                        Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))),
                messageMapper.getAuthor().getEmail(), messageMapper.getText());
    }

    private String formatMessage(MessageMapper messageMapper) {
        return MessageFormat.format("{0} &lt;{1}&gt;: {2}",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                        Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))),
                messageMapper.getAuthor().getEmail(), HtmlEscape.escapeTextArea(messageMapper.getText()));
    }

    private void setupRouter(final Vertx vertx, final Router router, final Configuration configuration) {

        final String callbackURL = "/oauth2";

        OAuth2Auth authProvider = null;
        final String oauth2ProviderName = configuration.getOauth2Provider();
        if (oauth2ProviderName.equals("google")) {
            authProvider = GoogleAuth.create(vertx,
                    configuration.getOauth2ClientID(), configuration.getOauth2ClientSecret());
        }
        if (authProvider == null) {
            throw new RuntimeException(
                    MessageFormat.format("Unsupported OAuth2 provider: {0}",
                            oauth2ProviderName));
        }

        // create a oauth2 handler on our domain
        OAuth2AuthHandler authHandler = OAuth2AuthHandler.create(authProvider,
                configuration.getOAuth2Domain());

        // these are the scopes
        authHandler.addAuthority("profile");
        authHandler.addAuthority("email");

        // We need a user session handler too to make sure the user
        // is stored in the session between requests
        router
                .route()
                .handler(UserSessionHandler.create(authProvider));

        // setup the callback handler for receiving the Google callback
        authHandler.setupCallback(router.get(callbackURL));

        // public index
        router
                .route("/")
                .handler(this::publicIndex);

        // put protected resources under oauth2
        router
                .route("/protected/*")
                .handler(authHandler);

        // enable message parsing
        router.routeWithRegex("/protected/.*")
                .handler(BodyHandler.create());

        /* internal index handler */
        router
                .get("/protected/")
                .handler(this::protectedIndex);

        router
                .getWithRegex("/protected/rooms/.*")
                .handler(this::main);

        router
                .put("/protected/new-room")
                .handler(this::newRoom);


        router
                .getWithRegex("/protected/download/.*")
                .handler(this::download);

        // logout
        router
                .route("/logout")
                .handler(ctx -> {
                    User User = ctx.user();
                    AccessToken token = (AccessToken) User;

                    if (token == null) {
                        found(ctx, "/");
                    }
                    else {
                        // Revoke only the access token
                        token.revoke("access_token", _1 -> {
                            token.revoke("refresh_token", _2 -> {
                                logger.info("Revoked tokens");

                                ctx.clearUser();
                                found(ctx, "/");
                            });
                        });
                    }
                });

        logger.info("OAUTH2 setup complete");
    }

    @Override
    public void handle(HttpServerRequest request) {
        logger.debug("Accepting HTTP Request: {} {} ...", request.method(), request.uri());
        router.accept(request);
    }

    /*** Route handlers ***********************************************************************************************/
    private void publicIndex(RoutingContext ctx) {
        templateEngine.render(ctx, "templates/index", asyncResult -> {
            if (asyncResult.succeeded()) {
                Buffer result = asyncResult.result();
                ctx.response()
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
                        .end(result);
            }
        });
    }

    private void protectedIndex(RoutingContext ctx) {
        getGeneralRoomUUID(vertx, stringAsyncResult -> {
            if (stringAsyncResult.failed()) {
                logger.warn(stringAsyncResult.cause().toString());
                internalServerError(ctx);
            } else {
                final String roomUUID = stringAsyncResult.result();
                final String redirectTo = "/protected/rooms/" + roomUUID;
                found(ctx, redirectTo);
            }
        });
    }

    /* main page server */
    private void main(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);

        Path prefix = Paths.get("/protected/rooms/");
        Path requestPath = Paths.get(ctx.request().path());
        Path roomUUIDPath = prefix.relativize(requestPath);
        String roomUUID = roomUUIDPath.toString();

        findRoomByUUID(vertx, roomUUID, roomMapperAsyncResult -> {
            if (roomMapperAsyncResult.failed()) {
                logger.warn(roomMapperAsyncResult.cause().toString());
                notFound(ctx);
            } else {
                final RoomMapper room = roomMapperAsyncResult.result();

                findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
                    if (userMapperAsyncResult.failed()) {
                        logger.error(userMapperAsyncResult.cause());
                        internalServerError(ctx);
                    } else {
                        final UserMapper user = userMapperAsyncResult.result();
                        Objects.requireNonNull(user);

                        ctx
                                .put("userEmail", user.getEmail())
                                .put("userID", user.getUuid())
                                .put("roomID", room.getUuid())
                                .put("roomName", room.getName());

                        templateEngine.render(ctx, "templates/main", templateAsyncResult -> {
                            if (templateAsyncResult.failed()) {
                                logger.error(userMapperAsyncResult.cause().toString());
                                internalServerError(ctx);
                            } else {
                                final Buffer buffer = templateAsyncResult.result();

                                ctx.response()
                                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                                        .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(buffer.length()))
                                        .end(buffer);
                            }
                        });
                    }
                });
            }
        });
    }

    private void newRoom(RoutingContext ctx) {
        final MultiMap params = ctx.request().params();
        final String roomName = params.get("roomName");

        findCreateRoomByName(vertx, roomName, room -> {
            logger.info("Created room {}", roomName);
            done(ctx);
        });
    }

    private void download(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);

        Path requestPath = Paths.get(urlDecode(ctx.request().path()));
        Path prefix = Paths.get("/protected/download/");

        String roomUID = prefix.relativize(requestPath).toString();
        if (! Utils.isValidUUID(roomUID)) {
            badRequest(ctx, "Invalid room UUID");
        } else {
            logger.info("Downloading messages for room UUID {}", roomUID);

            findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed()) {
                    logger.error(userMapperAsyncResult.cause().toString());
                    internalServerError(ctx);
                } else {
                    final UserMapper user = userMapperAsyncResult.result();

                    findRoomByUUID(vertx, roomUID, roomMapperAsyncResult -> {
                        if (roomMapperAsyncResult.failed()) {
                            logger.warn(roomMapperAsyncResult.cause());
                            notFound(ctx);
                        } else {
                            final RoomMapper room = roomMapperAsyncResult.result();

                            fetchMessages(vertx, user, room, messagesAsyncResult -> {
                                if (messagesAsyncResult.failed()) {
                                    logger.error(messagesAsyncResult.cause().toString());
                                    notFound(ctx);
                                } else {
                                    final List<MessageMapper> messageMappers = messagesAsyncResult.result();

                                    final String fullText = messageMappers.stream()
                                            .map(this::formatPlainMessage)
                                            .collect(Collectors.joining(""));

                                    ctx.response()
                                            .putHeader(Headers.CONTENT_TYPE_HEADER, "text/plain")
                                            .end(fullText);
                                }
                            });
                        }
                    });
                }
            });
        }
    }

    /**
     * Retrieves messages history for the given room.
     *
     * @param ctx
     */
    private void history(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);

        Path prefix = Paths.get("/protected/history");
        String roomUID = prefix.relativize(Paths.get(urlDecode(ctx.request().path()))).toString();

        findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
            if (userMapperAsyncResult.failed()) {
                logger.warn(userMapperAsyncResult.cause());
                forbidden(ctx);
            } else {
                final UserMapper user = userMapperAsyncResult.result();

                findRoomByUUID(vertx, roomUID, roomMapperAsyncResult -> {
                    if (roomMapperAsyncResult.failed()) {
                        logger.warn(roomMapperAsyncResult.cause());
                        notFound(ctx);
                    } else {
                        final RoomMapper room = roomMapperAsyncResult.result();

                        fetchMessages(vertx, user, room, messagesAsyncResult -> {
                            if (messagesAsyncResult.failed()) {
                                logger.error(messagesAsyncResult.cause());
                            } else {
                                final List<MessageMapper> messages =
                                        messagesAsyncResult.result();

                                final List<String> history = messages.stream()
                                        .map(this::formatMessage)
                                        .collect(Collectors.toList());

                                String body = new JsonObject()
                                        .put("data", new JsonObject()
                                                .put("history", history))
                                        .encodePrettily();

                                ctx.response()
                                        .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                                        .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                                        .end(body);
                            }
                        });
                    }
                });
            }
        });
    }

    /*** Responders ***************************************************************************************************/
    private void badRequest(RoutingContext ctx, String message) {

        HttpServerRequest request = ctx.request();
        logger.debug("Bad Request: {}", request.uri());

        request.response()
                .setStatusCode(StatusCode.BAD_REQUEST.getStatusCode())
                .setStatusMessage(StatusCode.BAD_REQUEST.getStatusMessage())
                .end(message != null ? message : StatusCode.BAD_REQUEST.getStatusMessage());
    }

    private void conflict(RoutingContext ctx, String message) {
        HttpServerRequest request = ctx.request();
        logger.debug("Conflict: {}", message);

        request.response()
                .setStatusCode(StatusCode.CONFLICT.getStatusCode())
                .setStatusMessage(StatusCode.CONFLICT.getStatusMessage())
                .end(message);
    }

    private void done(RoutingContext ctx) {
        ctx.response()
                .end();
    }

    private void notAllowed(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Not allowed: {}", request.uri());

        request.response()
                .setStatusCode(StatusCode.METHOD_NOT_ALLOWED.getStatusCode())
                .setStatusMessage(StatusCode.METHOD_NOT_ALLOWED.getStatusMessage())
                .end(StatusCode.METHOD_NOT_ALLOWED.toString());
    }

    private void notAcceptable(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Not acceptable: {}", request.uri());

        request.response()
                .setStatusCode(StatusCode.NOT_ACCEPTABLE.getStatusCode())
                .setStatusMessage(StatusCode.NOT_ACCEPTABLE.getStatusMessage())
                .end(StatusCode.NOT_ACCEPTABLE.toString());
    }

    private void notFound(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        final MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);
        boolean html = (accept != null && accept.contains("text/html"));
        boolean json = (accept != null && accept.contains("application/json"));

        logger.debug("Resource not found: {}", request.uri());
        HttpServerResponse response = ctx.response();
        response
                .setStatusCode(StatusCode.NOT_FOUND.getStatusCode())
                .setStatusMessage(StatusCode.NOT_FOUND.getStatusMessage());

        if (html) {
            templateEngine.render(ctx, "templates/notfound", asyncResult -> {
                if (asyncResult.succeeded()) {
                    response
                            .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                            .end(asyncResult.result());
                }
            });
        } else if (json) {
            response
                    .end(new JsonObject()
                            .put("status", "error")
                            .put("message", "Not Found")
                            .encodePrettily());
        } else /* assume: text/plain */ {
            response
                    .end(StatusCode.NOT_FOUND.toString());
        }
    }

    private void forbidden(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        final MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);
        boolean html = (accept != null && accept.contains("text/html"));
        boolean json = (accept != null && accept.contains("application/json"));

        logger.debug("Resource not found: {}", request.uri());
        HttpServerResponse response = ctx.response();
        response
                .setStatusCode(StatusCode.FORBIDDEN.getStatusCode())
                .setStatusMessage(StatusCode.FORBIDDEN.getStatusMessage());

        if (html) {
            templateEngine.render(ctx, "templates/forbidden", asyncResult -> {
                if (asyncResult.succeeded()) {
                    response
                            .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                            .end(asyncResult.result());
                }
            });
        } else if (json) {
            response
                    .end(new JsonObject()
                            .put("status", "error")
                            .put("message", "Not Found")
                            .encodePrettily());
        } else /* assume: text/plain */ {
            response
                    .end(StatusCode.NOT_FOUND.toString());
        }
    }

    private void notModified(RoutingContext ctx, String etag) {
        ctx.response()
                .setStatusCode(StatusCode.NOT_MODIFIED.getStatusCode())
                .setStatusMessage(StatusCode.NOT_MODIFIED.getStatusMessage())
                .putHeader(Headers.ETAG_HEADER, etag)
                .putHeader(Headers.CONTENT_LENGTH_HEADER, "0")
                .end();
    }

    private void ok(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Ok: {}", request.uri());

        ctx.response()
                .setStatusCode(StatusCode.OK.getStatusCode())
                .setStatusMessage(StatusCode.OK.getStatusMessage())
                .end(StatusCode.OK.toString());
    }

    private void internalServerError(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        final MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);
        boolean html = (accept != null && accept.contains("text/html"));
        boolean json = (accept != null && accept.contains("application/json"));

        logger.debug("Resource not found: {}", request.uri());
        HttpServerResponse response = ctx.response();
        response
                .setStatusCode(StatusCode.INTERNAL_SERVER_ERROR.getStatusCode())
                .setStatusMessage(StatusCode.INTERNAL_SERVER_ERROR.getStatusMessage());

        if (html) {
            templateEngine.render(ctx, "templates/internal", asyncResult -> {
                if (asyncResult.succeeded()) {
                    response
                            .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                            .end(asyncResult.result());
                }
            });
        } else if (json) {
            response
                    .end(new JsonObject()
                            .put("status", "error")
                            .put("message", "Not Found")
                            .encodePrettily());
        } else /* assume: text/plain */ {
            response
                    .end(StatusCode.INTERNAL_SERVER_ERROR.toString());
        }
    }

    private void found(RoutingContext ctx, String targetURI) {
        logger.debug("Redirecting to {}", targetURI);
        ctx.response()
                .putHeader(Headers.LOCATION_HEADER, targetURI)
                .setStatusCode(StatusCode.FOUND.getStatusCode())
                .setStatusMessage(StatusCode.FOUND.getStatusMessage())
                .end();
    }

    /** Queries *******************************************************************************************************/
    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email - the user's email
     * @param handler
     */
    private void findCreateUserEntityByEmail(Vertx vertx, String email, Handler<AsyncResult<UserMapper>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_CREATE_USER_BY_EMAIL)
                .put("params", new JsonObject()
                        .put("email", email));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                final JsonObject obj = (JsonObject) reply.result().body();
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null result"));
                } else {
                    final JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        UserMapper user;
                        try {
                            user = obj.getJsonObject("result").mapTo(UserMapper.class);
                            handler.handle(Future.succeededFuture(user));
                        } catch (Throwable t) {
                            handler.handle(Future.failedFuture(t));
                        }
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
    private void findCreateRoomByName(Vertx vertx, String name, Handler<AsyncResult<RoomMapper>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_CREATE_ROOM_BY_NAME)
                .put("params", new JsonObject()
                        .put("name", name));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                final JsonObject obj = (JsonObject) reply.result().body();
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null result"));
                } else {
                    final JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        try {
                            RoomMapper room = result.mapTo(RoomMapper.class);
                            handler.handle(Future.succeededFuture(room));
                        } catch (Throwable t) {
                            handler.handle(Future.failedFuture(t));
                        }
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
    private void findUserByUUID(Vertx vertx, String uuid, Handler<AsyncResult<UserMapper>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_USER_BY_UUID)
                .put("params", new JsonObject()
                        .put("uuid", uuid));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null reply message"));
                } else {
                    JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        try {
                            handler.handle(Future.succeededFuture(result.mapTo(UserMapper.class)));
                        } catch (Throwable t) {
                            handler.handle(Future.failedFuture(t));
                        }
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
    private void findRoomByUUID(Vertx vertx, String uuid, Handler<AsyncResult<RoomMapper>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_ROOM_BY_UUID)
                .put("params", new JsonObject()
                        .put("uuid", uuid));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null reply message"));
                } else {
                    JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        try {
                            handler.handle(Future.succeededFuture(result.mapTo(RoomMapper.class)));
                        } catch (Throwable t) {
                            handler.handle(Future.failedFuture(t));
                        }
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
    private void recordMessage(Vertx vertx, UserMapper userMapper, String messageText, Instant timeStamp,
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
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null reply message"));
                } else {
                    JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        try {
                            handler.handle(Future.succeededFuture(result.mapTo(MessageMapper.class)));
                        } catch (Throwable t) {
                            handler.handle(Future.failedFuture(t));
                        }
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
    private void getGeneralRoomUUID(Vertx vertx, Handler<AsyncResult<String>> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.GET_GENERAL_ROOM_UUID);

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.failed()) {
                handler.handle(Future.failedFuture(reply.cause()));
            } else {
                JsonObject obj = (JsonObject) reply.result().body();
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null reply message"));
                } else {
                    JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
                    } else {
                        try {
                            handler.handle(Future.succeededFuture(result.getString("uuid")));
                        } catch (Throwable t) {
                            handler.handle(Future.failedFuture(t));
                        }
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
    private void fetchMessages(Vertx vertx, UserMapper userMapper,
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
                if (Objects.isNull(obj)) {
                    handler.handle(Future.failedFuture("Null reply message"));
                } else {
                    JsonObject result = obj.getJsonObject("result");
                    if (Objects.isNull(result)) {
                        handler.handle(Future.failedFuture("Malformed reply message"));
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
            }
        });
    }

    /*** Helpers ******************************************************************************************************/
    private static String getSessionUserEmail(RoutingContext ctx) {
        User User = ctx.user();
        AccessToken at = (AccessToken) User;

        JsonObject idToken = KeycloakHelper.idToken(at.principal());
        String email = idToken.getString("email");

        return email;
    }

    private static String buildBackLink(int index) {
        StringBuilder sb = new StringBuilder();

        sb.append("./");
        for (int i = 0; i < index; ++ i)
            sb.append("../");

        return sb.toString();
    }

    private static String chopString(String s) {
        return s.substring(0, s.length() -2);
    }

    private static Path protectedPath(RoutingContext ctx) {
        Path prefix = Paths.get("/protected");
        return prefix.relativize(Paths.get(urlDecode(ctx.request().path())));
    }
}
