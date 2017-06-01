package org.blackcat.chatty.http.responses;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.Headers;

import java.util.Objects;

public final class JsonResponse {

    private JsonResponse()
    {}

    /**
     * Issues a 200 Ok Json response.
     *
     * @param ctx
     * @param dataObject
     */
    public static void ok(RoutingContext ctx, JsonObject dataObject) {

        Objects.requireNonNull(dataObject);
        final String body = makeDataObject(dataObject)
                .encodePrettily();

        ctx.response()
                .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                .end(body);
    }

    /**
     * Issues a 403 Forbidden Json response.
     *
     * @param ctx
     */
    public static void forbidden(RoutingContext ctx) {

        final String body = makeErrorObject(StatusCode.FORBIDDEN)
                .encodePrettily();

        ctx.response()
                .setStatusCode(StatusCode.FORBIDDEN.getStatusCode())
                .setStatusMessage(StatusCode.FORBIDDEN.getStatusMessage())
                .end(body);
    }

    /**
     * Issues a 404 Not Found Json response.
     *
     * @param ctx
     */
    public static void notFound(RoutingContext ctx) {

        final String body = makeErrorObject(StatusCode.NOT_FOUND)
                .encodePrettily();

        ctx.response()
                .setStatusCode(StatusCode.NOT_FOUND.getStatusCode())
                .setStatusMessage(StatusCode.NOT_FOUND.getStatusMessage())
                .end(body);
    }

    /**
     * Issues a 500 Internal Server Error Json response.
     *
     * @param ctx
     */
    public static void internalServerError(RoutingContext ctx) {

        final String body = makeErrorObject(StatusCode.INTERNAL_SERVER_ERROR)
                .encodePrettily();

        ctx.response()
                .setStatusCode(StatusCode.INTERNAL_SERVER_ERROR.getStatusCode())
                .setStatusMessage(StatusCode.INTERNAL_SERVER_ERROR.getStatusMessage())
                .end(body);
    }

    private static JsonObject makeDataObject(JsonObject payload) {
        return new JsonObject().put("data", payload);
    }

    private static JsonObject makeErrorObject(StatusCode statusCode) {
        return new JsonObject().put("error", new JsonObject()
                .put("code", statusCode.getStatusCode())
                .put("message", statusCode.getStatusMessage()));
    }
}
