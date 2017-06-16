package org.blackcat.chatty.http;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
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
import org.blackcat.chatty.http.responses.BaseResponse;
import org.blackcat.chatty.http.responses.HtmlResponse;
import org.blackcat.chatty.http.responses.JsonResponse;
import org.blackcat.chatty.http.responses.TextResponse;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.util.Utils;
import org.blackcat.chatty.verticles.DataStoreVerticle;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamEventReceiver;
import org.owasp.html.HtmlStreamRenderer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.blackcat.chatty.util.Utils.urlDecode;

public class RequestHandler implements Handler<HttpServerRequest> {

    private Vertx vertx;
    private Router router;
    private TemplateEngine templateEngine;
    private Logger logger;

    /** A policy definition that matches the minimal HTML that Slashdot allows. */
    private static final Function<HtmlStreamEventReceiver, HtmlSanitizer.Policy>
            POLICY_DEFINITION = new HtmlPolicyBuilder()
            .allowStandardUrlProtocols()
            .toFactory();

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

            // sanitize the message
            StringBuilder sanitizedTextStringBuilder = new StringBuilder();
            HtmlStreamRenderer htmlStreamRenderer = HtmlStreamRenderer.create(sanitizedTextStringBuilder,
                    e -> {
                        Throwables.propagate(e);
            },
                    s -> {
            });
            HtmlSanitizer.sanitize(text, POLICY_DEFINITION.apply(htmlStreamRenderer));

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

                            recordMessage(vertx, user, sanitizedTextStringBuilder.toString(),
                                    Instant.now(), room, messageMapperAsyncResult -> {
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
                .handler(HtmlResponse::notFound);

       /* invalid method */
        router
                .routeWithRegex(".*")
                .handler(HtmlResponse::badRequest);

        /* errors */
        router
                .route()
                .failureHandler(HtmlResponse::internalServerError);
    }

    private String formatPlainMessage(MessageMapper messageMapper) {
        return MessageFormat.format("{0} <{1}>: {2}",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                        Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))),
                messageMapper.getAuthor().getEmail(), messageMapper.getText());
    }

    private JsonArray formatJsonMessage(MessageMapper messageMapper) {
        return new JsonArray()
                .add(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                        Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))))
                .add(messageMapper.getAuthor().getEmail())
                .add(messageMapper.getText());
    }

    private String formatMessage(MessageMapper messageMapper) {
        return MessageFormat.format("{0} &lt;{1}&gt;: {2}",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                        Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))),
                messageMapper.getAuthor().getEmail(),
                messageMapper.getText());
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

        // add template engine to the routing ctx. This is needed by HTML error pages.
        router
                .route()
                .handler(ctx -> {
                    ctx.put("templateEngine", templateEngine);
                    ctx.next();
                });

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
                        BaseResponse.found(ctx, "/");
                    }
                    else {
                        // Revoke only the access token
                        token.revoke("access_token", _1 -> {
                            token.revoke("refresh_token", _2 -> {
                                logger.info("Revoked tokens");

                                ctx.clearUser();
                                BaseResponse.found(ctx, "/");
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

    /**
     * Redirects to Main room page.
     *
     * @param ctx
     */
    private void protectedIndex(RoutingContext ctx) {
        getGeneralRoomUUID(vertx, stringAsyncResult -> {
            if (stringAsyncResult.failed()) {
                logger.warn(stringAsyncResult.cause().toString());
                HtmlResponse.internalServerError(ctx);
            } else {
                final String roomUUID = stringAsyncResult.result();
                final String redirectTo = "/protected/rooms/" + roomUUID;

                BaseResponse.found(ctx, redirectTo);
            }
        });
    }

    /**
     * main page
     *
     * @param ctx
     */
    private void main(RoutingContext ctx) {
        String email = Utils.getSessionUserEmail(ctx);

        Path prefix = Paths.get("/protected/rooms/");
        Path requestPath = Paths.get(ctx.request().path());
        Path roomUUIDPath = prefix.relativize(requestPath);
        String roomUUID = roomUUIDPath.toString();

        findRoomByUUID(vertx, roomUUID, roomMapperAsyncResult -> {
            if (roomMapperAsyncResult.failed()) {
                logger.warn(roomMapperAsyncResult.cause().toString());
                HtmlResponse.notFound(ctx);
            } else {
                final RoomMapper room = roomMapperAsyncResult.result();

                findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
                    if (userMapperAsyncResult.failed()) {
                        logger.error(userMapperAsyncResult.cause());
                        HtmlResponse.internalServerError(ctx);
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
                                HtmlResponse.internalServerError(ctx);
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

    /**
     * create a new room (AJAX, JSON response).
     *
     * @param ctx
     */
    private void newRoom(RoutingContext ctx) {
        final MultiMap params = ctx.request().params();
        final String roomName = params.get("roomName");

        findCreateRoomByName(vertx, roomName, roomMapperAsyncResult -> {
            if (roomMapperAsyncResult.failed()) {
                logger.error(roomMapperAsyncResult.cause().toString());
                JsonResponse.internalServerError(ctx);
            } else {
                logger.info("Created room {}", roomName);
                JsonResponse.ok(ctx, new JsonObject());
            }
        });
    }

    /**
     * Downloads full chat history for the room (plain text response).
     *
     * @param ctx
     */
    private void download(RoutingContext ctx) {
        String email = Utils.getSessionUserEmail(ctx);

        Path requestPath = Paths.get(urlDecode(ctx.request().path()));
        Path prefix = Paths.get("/protected/download");

        String roomUID = prefix.relativize(requestPath).toString();
        if (! Utils.isValidUUID(roomUID)) {
            BaseResponse.badRequest(ctx, "Invalid room UUID");
        } else {
            logger.info("Downloading messages for room UUID {}", roomUID);

            findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed()) {
                    final Throwable cause = userMapperAsyncResult.cause();

                    logger.error(cause.toString());
                    BaseResponse.internalServerError(ctx, cause);
                } else {
                    final UserMapper user = userMapperAsyncResult.result();

                    findRoomByUUID(vertx, roomUID, roomMapperAsyncResult -> {
                        if (roomMapperAsyncResult.failed()) {
                            final Throwable cause = roomMapperAsyncResult.cause();

                            logger.error(cause.toString());
                            BaseResponse.internalServerError(ctx, cause);
                        } else {
                            final RoomMapper room = roomMapperAsyncResult.result();

                            fetchMessages(vertx, user, room, messagesAsyncResult -> {
                                if (messagesAsyncResult.failed()) {
                                    final Throwable cause = messagesAsyncResult.cause();

                                    logger.error(cause.toString());
                                    BaseResponse.internalServerError(ctx, cause);
                                } else {
                                    final List<MessageMapper> messageMappers =
                                            messagesAsyncResult.result();

                                    final String fullText = messageMappers.stream()
                                            .map(this::formatPlainMessage)
                                            .collect(Collectors.joining(""));

                                    TextResponse.done(ctx, fullText);
                                }
                            });
                        }
                    });
                }
            });
        }
    }

    /**
     * Downloads full chat history for the room (JSON response).
     *
     * @param ctx
     */
    private void history(RoutingContext ctx) {
        String email = Utils.getSessionUserEmail(ctx);

        Path prefix = Paths.get("/protected/history");
        String roomUID = prefix.relativize(Paths.get(urlDecode(ctx.request().path()))).toString();

        findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
            if (userMapperAsyncResult.failed()) {
                logger.warn(userMapperAsyncResult.cause());
                JsonResponse.forbidden(ctx);
            } else {
                final UserMapper user = userMapperAsyncResult.result();

                findRoomByUUID(vertx, roomUID, roomMapperAsyncResult -> {
                    if (roomMapperAsyncResult.failed()) {
                        logger.warn(roomMapperAsyncResult.cause());
                        JsonResponse.notFound(ctx);
                    } else {
                        final RoomMapper room = roomMapperAsyncResult.result();

                        fetchMessages(vertx, user, room, messagesAsyncResult -> {
                            if (messagesAsyncResult.failed()) {
                                logger.error(messagesAsyncResult.cause());
                            } else {
                                final List<MessageMapper> messages =
                                        messagesAsyncResult.result();

                                final List<JsonArray> history = messages.stream()
                                        .map(this::formatJsonMessage)
                                        .collect(Collectors.toList());

                                JsonResponse.ok(ctx, new JsonObject()
                                        .put("history", history));
                            }
                        });
                    }
                });
            }
        });
    }

    /** Queries *******************************************************************************************************/
    final private String malformedReplyMessage = "Malformed reply message";

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
    private void getGeneralRoomUUID(Vertx vertx, Handler<AsyncResult<String>> handler) {
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
