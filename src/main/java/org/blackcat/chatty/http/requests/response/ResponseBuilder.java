package org.blackcat.chatty.http.requests.response;

import io.vertx.ext.web.RoutingContext;

public interface ResponseBuilder {
    /* 400 */ void badRequest(RoutingContext ctx);
    /* 401 */ void forbidden(RoutingContext ctx);
    /* 404 */ void notFound(RoutingContext ctx);
    /* 405 */ void methodNotAllowed(RoutingContext ctx);
    /* 406 */ void notAcceptable(RoutingContext ctx);
    /* 409 */ void conflict(RoutingContext ctx);
    /* 500 */ void internalServerError(RoutingContext ctx);
}

