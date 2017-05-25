package org.blackcat.chatty.http;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
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
import org.blackcat.chatty.verticles.DataStoreVerticle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
                .addInboundPermitted(new PermittedOptions().setAddress("webchat.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("webchat.client"));

        // Create the event bus bridge and add it to the router.
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // oauth2 setup
        setupOAuth2(vertx, router, configuration);

        EventBus eventBus = vertx.eventBus();

        // Register to listen for messages coming IN to the server
        eventBus.consumer("webchat.server").handler(message -> {

            JsonObject jsonObject = new JsonObject((String) message.body());
            String userID = jsonObject.getString("userID");
            String roomID = jsonObject.getString("roomID");
            String text = jsonObject.getString("text");

            findUserByUUID(userID, user -> {
                findRoomByUUID(roomID, room -> {
                    recordMessage(user, text, Instant.now(), room, messageMapper -> {

//                        String displayText = MessageFormat.format("{0} &lt;{1}&gt;: {2}",
//                                DateFormat.getDateTimeInstance(DateFormat.SHORT,
//                                        DateFormat.MEDIUM).format(Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))),
//                                user.getEmail(), HtmlEscape.escapeTextArea(text));

                        eventBus.publish("webchat.client", new JsonObject()
                                .put("roomID", roomID)
                                .put("displayText", formatMessage(messageMapper)));
                    });
                });
            });
        });

        /* internal index handler */
        router
                .get("/protected/main")
                .handler(this::main);

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

    private String formatMessage(MessageMapper messageMapper) {
        return MessageFormat.format("{0} &lt;{1}&gt;: {2}",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now())),
                messageMapper.getAuthor().getEmail(), HtmlEscape.escapeTextArea(messageMapper.getText()));
    }

    private void setupOAuth2(final Vertx vertx, final Router router, final Configuration configuration) {

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

        router
                .route("/")
                .handler(this::publicIndexGetRequest);

        // put sharing mgmt under oauth2
        router
                .route("/share/*")
                .handler(authHandler);

        // put protected resource under oauth2
        router
                .route("/protected/*")
                .handler(authHandler);

        /* internal index handler */
        router
                .get("/protected/main")
                .handler(this::main);

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
    private void publicIndexGetRequest(RoutingContext ctx) {
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

    private void main(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);

        findCreateUserEntityByEmail(email, userMapper -> {

            getGeneralRoomUUID(roomUUID -> {
                ctx
                        .put("userEmail", userMapper.getEmail())
                        .put("userID", userMapper.getUuid())
                        .put("roomID", roomUUID)
                        .put("historyURL", "/protected/history/" + roomUUID);

                templateEngine.render(ctx, "templates/main", asyncResult -> {
                    if (asyncResult.succeeded()) {
                        Buffer result = asyncResult.result();
                        ctx.response()
                                .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                                .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
                                .end(result);
                    } else {
                        final Throwable cause = asyncResult.cause();
                        logger.error(cause.toString());
                        internalServerError(ctx);
                    }
                });
            });
        });
    }

    private void history(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);

        Path prefix = Paths.get("/protected/history");
        String roomUID = prefix.relativize(Paths.get(urlDecode(ctx.request().path()))).toString();

        findCreateUserEntityByEmail(email, userMapper -> {
            findRoomByUUID(roomUID, roomMapper -> {
                /* TODO: check user authorization for this room, for now we assume we're good */
                fetchMessages(userMapper, roomMapper, messages -> {

                    final List<String> history = messages.stream()
                            .map(message -> formatMessage(message))
                            .collect(Collectors.toList());

                    String body = new JsonObject()
                            .put("data", new JsonObject()
                                    .put("history", history))
                            .encodePrettily();

                    ctx.response()
                            .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                            .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                            .end(body);
                });
            });
        });
    }


    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email - the user's email
     * @param handler
     */
    private void findCreateUserEntityByEmail(String email, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_CREATE_USER_BY_EMAIL)
                .put("params", new JsonObject()
                        .put("email", email));

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
    private void findCreateRoomByName(String name, Handler<RoomMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_CREATE_ROOM_BY_NAME)
                .put("params", new JsonObject()
                        .put("name", name));

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
    private void findUserByUUID(String uuid, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_USER_BY_UUID)
                .put("params", new JsonObject()
                        .put("uuid", uuid));

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
    private void findRoomByUUID(String uuid, Handler<RoomMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_ROOM_BY_UUID)
                .put("params", new JsonObject()
                        .put("uuid", uuid));

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

    private void getGeneralRoomUUID(Handler<String> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.GET_GENERAL_ROOM_UUID);

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
    private void recordMessage(UserMapper userMapper, String messageText, Instant timeStamp,
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
                        .put("timeStamp", timeStamp) /* XXX */
                        .put("room", JsonObject.mapFrom(roomMapper)));

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
    private void fetchMessages(UserMapper userMapper, RoomMapper roomMapper, Handler<List<MessageMapper>> handler) {
        Objects.requireNonNull(userMapper, "user is null");
        Objects.requireNonNull(roomMapper, "room is null");

        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FETCH_MESSAGES)
                .put("params", new JsonObject()
                        .put("userUUID", userMapper.getUuid())
                        .put("roomUUID", roomMapper.getUuid()));

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
