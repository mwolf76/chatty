package org.blackcat.chatty.http.responses;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.chatty.http.Headers;

import java.util.Objects;

public final class HtmlResponse {

    static Logger logger = LoggerFactory.getLogger(HtmlResponse.class);

    private HtmlResponse()
    {}

    public static void badRequest(RoutingContext ctx) {
        badRequest(ctx, null);
    }

    /**
     * Issue a Bad Request (400) HTML response. An optional message can be specified.
     *
     * @param ctx
     * @param message (optional)
     */
    public static void badRequest(RoutingContext ctx, String message) {

        ctx.response()
                .setStatusCode(StatusCode.BAD_REQUEST.getStatusCode())
                .setStatusMessage(StatusCode.BAD_REQUEST.getStatusMessage())
                .end(message != null ? message : StatusCode.BAD_REQUEST.getStatusMessage());
    }

    /**
     * Issues a Forbidden (403) HTML response.
     *
     * @param ctx
     */
    public static void forbidden(RoutingContext ctx) {

        ctx.response()
                .setStatusCode(StatusCode.FORBIDDEN.getStatusCode())
                .setStatusMessage(StatusCode.FORBIDDEN.getStatusMessage());

        TemplateEngine templateEngine = ctx.get("templateEngine");
        Objects.requireNonNull(templateEngine);

        templateEngine.render(ctx, "templates/forbidden", asyncResult -> {
            if (asyncResult.succeeded()) {
                ctx.response()
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .end(asyncResult.result());
            }
        });
    }

    /**
     * Issues a Not Found (404) HTML response.
     *
     * @param ctx
     */
    public static void notFound(RoutingContext ctx) {

        ctx.response()
                .setStatusCode(StatusCode.NOT_FOUND.getStatusCode())
                .setStatusMessage(StatusCode.NOT_FOUND.getStatusMessage());

        TemplateEngine templateEngine = ctx.get("templateEngine");
        Objects.requireNonNull(templateEngine);

        templateEngine.render(ctx, "templates/notfound", asyncResult -> {
            if (asyncResult.failed()) {
                logger.error(asyncResult.cause());
                BaseResponse.done(ctx);
            } else {
                final Buffer result = asyncResult.result();
                ctx.response()
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .end(result);
            }
        });
    }


    /**
     * Issues an Internal Server Error (500) HTML response.
     *
     * @param ctx
     */
    public static void internalServerError(RoutingContext ctx) {

        ctx.response()
                .setStatusCode(StatusCode.INTERNAL_SERVER_ERROR.getStatusCode())
                .setStatusMessage(StatusCode.INTERNAL_SERVER_ERROR.getStatusMessage());

        TemplateEngine templateEngine = ctx.get("templateEngine");
        Objects.requireNonNull(templateEngine);

        templateEngine.render(ctx, "templates/internal", asyncResult -> {
            if (asyncResult.failed()) {
                logger.error(asyncResult.cause());
                BaseResponse.done(ctx);
            } else {
                final Buffer result = asyncResult.result();
                ctx.response()
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .end(result);
            }
        });
    }
}
