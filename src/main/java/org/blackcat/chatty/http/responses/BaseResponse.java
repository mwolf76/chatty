package org.blackcat.chatty.http.responses;

import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.Headers;

public final class BaseResponse {

    private BaseResponse()
    {}

    /**
     * Terminates a request.
     *
     * @param ctx
     */
    public static void done(RoutingContext ctx) {
        ctx.response().end();
    }

    /**
     * Issues a 302 redirect.
     *
     * @param ctx
     * @param targetURI
     */
    public static void found(RoutingContext ctx, String targetURI) {
        ctx.response()
                .putHeader(Headers.LOCATION_HEADER, targetURI)
                .setStatusCode(StatusCode.FOUND.getStatusCode())
                .setStatusMessage(StatusCode.FOUND.getStatusMessage())
                .end();
    }

    /**
     * Issues a 400 Bad Request response.
     *
     * @param ctx
     * @param cause
     */
    public static void badRequest(RoutingContext ctx, String cause) {
        ctx.response()
                .setStatusCode(StatusCode.BAD_REQUEST.getStatusCode())
                .setStatusMessage(StatusCode.BAD_REQUEST.getStatusMessage())
                .end(cause != null ? cause : StatusCode.BAD_REQUEST.getStatusMessage());
    }

    /**
     * Issues a 400 Bad Request response.
     *
     * @param ctx
     * @param cause
     */
    public static void badRequest(RoutingContext ctx, Throwable cause) {
        ctx.response()
                .setStatusCode(StatusCode.BAD_REQUEST.getStatusCode())
                .setStatusMessage(StatusCode.BAD_REQUEST.getStatusMessage())
                .end(cause != null ? cause.toString() : StatusCode.BAD_REQUEST.getStatusMessage());
    }

    /**
     * Issues a 500 Internal Server Error response.
     *
     * @param ctx
     * @param cause
     */
    public static void internalServerError(RoutingContext ctx, String cause) {
        ctx.response()
                .setStatusCode(StatusCode.INTERNAL_SERVER_ERROR.getStatusCode())
                .setStatusMessage(StatusCode.INTERNAL_SERVER_ERROR.getStatusMessage())
                .end(StatusCode.INTERNAL_SERVER_ERROR.toString());
    }


    /**
     * Issues a 500 Internal Server Error response.
     *
     * @param ctx
     * @param cause
     */
    public static void internalServerError(RoutingContext ctx, Throwable cause) {
        internalServerError(ctx, cause.toString());
    }

}
