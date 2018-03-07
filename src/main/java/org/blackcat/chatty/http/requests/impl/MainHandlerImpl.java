package org.blackcat.chatty.http.requests.impl;

import com.google.common.base.Throwables;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.PebbleTemplateEngine;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.http.middleware.UserInfoHandler;
import org.blackcat.chatty.http.requests.MainHandler;
import org.blackcat.chatty.http.requests.handlers.*;
import org.blackcat.chatty.http.requests.response.impl.HtmlResponseBuilderImpl;
import org.blackcat.chatty.http.requests.response.impl.JsonResponseBuilderImpl;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.queries.Queries;
import org.blackcat.chatty.verticles.DataStoreVerticle;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamRenderer;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.blackcat.chatty.conf.Keys.OAUTH2_PROVIDER_GOOGLE;
import static org.blackcat.chatty.conf.Keys.OAUTH2_PROVIDER_KEYCLOAK;
import static org.owasp.html.examples.SlashdotPolicyExample.POLICY_DEFINITION;

public final class MainHandlerImpl implements MainHandler {

    private final String OAUTH2_CALLBACK_LOCATION = "/callback";
    private final Logger logger = LoggerFactory.getLogger(MainHandlerImpl.class);

    private final Configuration configuration;
    private final Vertx vertx;
    private final Router router;

    private final HtmlResponseBuilderImpl htmlResponseBuilder;
    private final JsonResponseBuilderImpl jsonResponseBuilder;

    public MainHandlerImpl(final Vertx vertx,
                           final Configuration configuration) {

        this.vertx = vertx;
        this.router = Router.router(vertx);
        this.configuration = configuration;

        this.htmlResponseBuilder = new HtmlResponseBuilderImpl(PebbleTemplateEngine.create(vertx));
        this.jsonResponseBuilder = new JsonResponseBuilderImpl();

        // Initial routing ctx setup
        router.route().handler(this::injectContextVars);

        setupMiddlewareHandlers();
        setupOAuth2Handlers();
        setupProtectedHandlers();
        setupWebSockets();
        setupPublicHandlers();
        setupErrorHandlers();
    }

    private void setupMiddlewareHandlers() {
        // Allow events for the designated addresses in/out of the event bus bridge
        PermittedOptions options = new PermittedOptions().setAddressRegex("webchat.*");

        // Create the event bus bridge and add it to the router.
        BridgeOptions opts = new BridgeOptions()
                                 .addInboundPermitted(options)
                                 .addOutboundPermitted(options);
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        /* required */
        router.putWithRegex("/protected/.*").handler(BodyHandler.create());

        // We need cookies, sessions and request bodies
        router.route().handler(CookieHandler.create());

        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx))
                                            .setCookieHttpOnlyFlag(true);

        if (configuration.isSSLEnabled()) {
            // avoid reading, sniffing hijacking or tampering your sessions (requires SSL)
            sessionHandler.setCookieSecureFlag(true);
        } else sessionHandler.setNagHttps(false); /* avoid nagging about not using https */

        router.route().handler(sessionHandler);
    }

    private void injectContextVars(RoutingContext ctx) {
        ctx.put( vertxKey, vertx);
        ctx.put( configurationKey, configuration);

        // it's up to the request handler to decider whether to use one or the other
        ctx.put(jsonResponseBuilderKey, jsonResponseBuilder);
        ctx.put(htmlResponseBuilderKey, htmlResponseBuilder);

        ctx.next();
    }

    private void setupErrorHandlers() {
        /* invalid URL */
        router.getWithRegex(".*")
            .handler(htmlResponseBuilder::notFound);

        /* invalid method */
        router.routeWithRegex(".*")
            .handler(htmlResponseBuilder::methodNotAllowed);

        /* errors */
        router.route()
            .failureHandler(htmlResponseBuilder::internalServerError);
    }

    private void setupWebSockets() {
        // Register to listen for messages coming IN to the server
        vertx.eventBus().consumer("webchat.server").handler(event -> {
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

            Queries.findUserByUUID(vertx, userID, userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed()) {
                    logger.error(userMapperAsyncResult.cause().toString());
                } else {
                    UserMapper user = userMapperAsyncResult.result();
                    Queries.findRoomByUUID(vertx, roomID, roomMapperAsyncResult -> {
                        if (roomMapperAsyncResult.failed()) {
                            logger.error(roomMapperAsyncResult.cause().toString());
                        } else {
                            RoomMapper room = roomMapperAsyncResult.result();
                            recordMessage(vertx, user, sanitizedTextStringBuilder.toString(),
                                    Instant.now(), room, messageMapperAsyncResult -> {
                                if (messageMapperAsyncResult.failed()) {
                                    logger.error(messageMapperAsyncResult.cause().toString());
                                } else {
                                    final MessageMapper message = messageMapperAsyncResult.result();

                                    vertx.eventBus()
                                        .publish("webchat.client",
                                            new JsonObject()
                                                .put("roomID", roomID)
                                                .put("displayText", formatMessage(message)));
                                }
                            });
                        }
                    });
                }
            });
        });
    }

    private String formatMessage(MessageMapper messageMapper) {
        return MessageFormat.format("{0} &lt;{1}&gt;: {2}",
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                        Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))),
                messageMapper.getAuthor().getEmail(),
                messageMapper.getText());
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

                JsonObject result = obj.getJsonObject("result");
                if (Objects.isNull(result)) {
                    JsonObject failure = obj.getJsonObject("failure");
                    if (! Objects.isNull(failure)) {
                        String cause = failure.getString("cause");
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        handler.handle(Future.failedFuture(new RuntimeException("Malformed message")));
                    }
                } else {
                    try {
                        MessageMapper message = result.mapTo(MessageMapper.class);
                        handler.handle(Future.succeededFuture(message));
                    } catch (Throwable t) {
                        handler.handle(Future.failedFuture(t));
                    }
                }
            }
        });
    }

    private void setupProtectedHandlers() {
        /* An extra handler to fetch user info into context */
        UserInfoHandler userInfoHandler = UserInfoHandler.create();

        /* protected */
        router.routeWithRegex("/protected/.*")
            .handler(userInfoHandler);

        router.get("/protected/")
            .handler(ProtectedIndexHandler.create());

        router.get("/protected/download/*")
            .handler(DownloadHandler.create());

        router.get("/protected/rooms/*")
            .handler(ProtectedRoomsHandler.create());

        router.put("/protected/new-room/*")
            .handler(NewRoomHandler.create());

        router.get("/protected/history/*")
            .handler(HistoryHandler.create());

        router.get("/protected/logout")
            .handler(LogoutRequestHandler.create());
    }

    private void setupPublicHandlers() {
        /* public index (unrestricted) */
        router.get("/")
            .handler(PublicIndexHandler.create());

        /* static files (unrestricted) */
        router.getWithRegex("/static/.*")
            .handler(StaticHandler.create());
    }

    private void setupOAuth2Handlers() {
        OAuth2Auth authProvider;
        final String oauth2ProviderName = configuration.getOauth2Provider();

        if (oauth2ProviderName.equals(OAUTH2_PROVIDER_GOOGLE)) {
            logger.info("Configuring Google oauth2 provider");
            authProvider = GoogleAuth.create(vertx,
                configuration.getOauth2ClientID(),
                configuration.getOauth2ClientSecret());
        } else if (oauth2ProviderName.equals(OAUTH2_PROVIDER_KEYCLOAK)) {
            logger.info("Configuring Keycloak oauth2 provider");
            authProvider = KeycloakAuth.create(vertx,
                OAuth2FlowType.AUTH_CODE,
                configuration.buildKeyCloakConfiguration());
        } else {
            throw new RuntimeException(
                MessageFormat.format("Unsupported OAuth2 provider: {0}", oauth2ProviderName));
        }

        String callbackURL = configuration.getDomain() + OAUTH2_CALLBACK_LOCATION;
        logger.debug("Setting up oauth2 callback at {}", callbackURL);
        OAuth2AuthHandler authHandler = OAuth2AuthHandler.create(authProvider, callbackURL);

        /* required by google,  keycloak doesn't care */
        authHandler.addAuthority("profile");
        authHandler.addAuthority("email");

        /* We need a user session handler too to make sure the user is stored in the session between requests */
        router.route().handler(UserSessionHandler.create(authProvider));

        /* Keep protected contents under oauth2 */
        authHandler.setupCallback(router.get(OAUTH2_CALLBACK_LOCATION));
        router.routeWithRegex("/protected/.*").handler(authHandler);
    }

    @Override
    public void handle(HttpServerRequest request) {
        logger.trace("Routing {} {}", request.method(), request.uri());
        router.accept(request);
    }

    public static MainHandlerImpl create(Vertx vertx, Configuration configuration) {
        return new MainHandlerImpl(vertx, configuration);
    }
}
