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
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.util.HtmlEscape;
import org.blackcat.chatty.verticles.DataStoreVerticle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

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

            // Create a timestamp string
            String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.MEDIUM).format(Date.from(Instant.now()));

            JsonObject jsonObject = new JsonObject((String) message.body());
            String userID = jsonObject.getString("userID");
            String text = jsonObject.getString("text");

            findUserEntityByUUID(userID, user -> {

                String msg = MessageFormat.format("{0} &lt;{1}&gt;: {2}",
                        timestamp, user.getEmail(), HtmlEscape.escapeTextArea(text));

                // Send the message back out to all clients with the timestamp prepended.
                eventBus.publish("webchat.client", msg);
            });

        });

        /* internal index handler */
        router
                .get("/protected/main")
                .handler(this::main);

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
            ctx
                    .put("userEmail", userMapper.getEmail())
                    .put("userID", userMapper.getUuid());

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
    }

    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email
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

                UserMapper userMapper = obj.mapTo(UserMapper.class);
                handler.handle(userMapper);
            }
        });
    }

    /**
     * Retrieves a User entity by uuid.
     *
     * @param uuid
     * @param handler
     */
    private void findUserEntityByUUID(String uuid, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", DataStoreVerticle.FIND_USER_BY_UUID)
                .put("params", new JsonObject()
                        .put("uuid", uuid));

        vertx.eventBus().send(DataStoreVerticle.ADDRESS, query, reply -> {
            if (reply.succeeded()) {
                JsonObject obj = (JsonObject) reply.result().body();
                if (! Objects.isNull(obj)) {
                    UserMapper userMapper = obj.mapTo(UserMapper.class);
                    handler.handle(userMapper);
                } else handler.handle(null);
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
