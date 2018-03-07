package org.blackcat.chatty.http.requests.response.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.Headers;
import org.blackcat.chatty.http.ResponseStatus;
import org.blackcat.chatty.http.requests.response.JsonResponseBuilder;

final public class JsonResponseBuilderImpl implements JsonResponseBuilder {

    @Override
    public void success(RoutingContext ctx, JsonObject result) {
        String body = result.encode();
        ctx.response()
            .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
            .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
            .end(body);
    }

//    @Override
//    public void ok(RoutingContext ctx) {
//        ctx.response()
//            .setStatusCode(ResponseStatus.OK.getStatusCode())
//            .setStatusMessage(ResponseStatus.OK.getStatusMessage())
//            .end(ResponseStatus.OK.toString());
//    }

    @Override
    public void badRequest(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.BAD_REQUEST);
    }

    @Override
    public void forbidden(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.FORBIDDEN);
    }

    @Override
    public void notFound(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.NOT_FOUND);
    }

    @Override
    public void methodNotAllowed(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.METHOD_NOT_ALLOWED);
    }

    @Override
    public void conflict(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.CONFLICT);
    }

    @Override
    public void notAcceptable(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void internalServerError(RoutingContext ctx) {
        errorResponse(ctx, ResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private void errorResponse(RoutingContext ctx, ResponseStatus status) {
        ctx.response()
            .setStatusCode(status.getStatusCode())
            .setStatusMessage(status.getStatusMessage())
            .end(new JsonObject()
                     .put("status", "error")
                     .put("message", status.getStatusMessage())
                     .encodePrettily());
    }



}
