package org.blackcat.chatty.http.requests.response.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.chatty.http.Headers;
import org.blackcat.chatty.http.ResponseStatus;
import org.blackcat.chatty.http.requests.response.HtmlResponseBuilder;

public final class HtmlResponseBuilderImpl implements HtmlResponseBuilder {

    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final TemplateEngine engine;
    private final String templateDir = "templates/";

    public HtmlResponseBuilderImpl(TemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void success(RoutingContext ctx, String templateName) {
        engine.render(ctx, templateDir, templateName, asyncResult -> {
            if (asyncResult.failed()) {
                ctx.fail(asyncResult.cause());
            } else {
                Buffer result = asyncResult.result();
                ctx.response()
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                    .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
                    .end(result);
            }
        });
    }

    @Override
    public void badRequest(RoutingContext ctx) {
        makeUserErrorResponse(ctx, ResponseStatus.BAD_REQUEST, "bad-request");
    }

    @Override
    public void forbidden(RoutingContext ctx) {
        makeUserErrorResponse(ctx, ResponseStatus.FORBIDDEN, "forbidden");
    }

    @Override
    public void notFound(RoutingContext ctx) {
        makeUserErrorResponse(ctx, ResponseStatus.NOT_FOUND, "not-found");
    }

    @Override
    public void methodNotAllowed(RoutingContext ctx) {
        makeUserErrorResponse(ctx, ResponseStatus.METHOD_NOT_ALLOWED, "method-not-allowed.peb");
    }

    @Override
    public void conflict(RoutingContext ctx) {
        makeUserErrorResponse(ctx, ResponseStatus.INTERNAL_SERVER_ERROR, "conflict");
    }

    @Override
    public void notAcceptable(RoutingContext ctx) {
        makeUserErrorResponse(ctx, ResponseStatus.NOT_ACCEPTABLE, "not-acceptable");
    }

    @Override
    public void internalServerError(RoutingContext ctx) {
        makeInternalErrorResponse(ctx);
    }

    private void makeUserErrorResponse(RoutingContext ctx, ResponseStatus status, String templateName) {
        HttpServerResponse response = ctx.response();
        response
            .setStatusCode(status.getStatusCode())
            .setStatusMessage(status.getStatusMessage());

        engine.render(ctx, templateDir, templateName, asyncResult -> {
            if (asyncResult.failed()) {
                Throwable cause = asyncResult.cause();
                logger.error(cause);
                ctx.fail(cause);
            } else {
                response
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                    .end(asyncResult.result());
            }
        });
    }

    private void makeInternalErrorResponse(RoutingContext ctx) {
        ResponseStatus status = ResponseStatus.INTERNAL_SERVER_ERROR;
        String templateName = "internal-error";

        Throwable failure = ctx.failure();
        if (failure != null)
            failure.printStackTrace();

        HttpServerResponse response = ctx.response();
        response
            .setStatusCode(status.getStatusCode())
            .setStatusMessage(status.getStatusMessage());

        engine.render(ctx, templateDir, templateName, asyncResult -> {
            if (asyncResult.failed()) {
                /* here we cannot throw the error via ctx.fail() or we'll enter an infinite recursion loop :-/ */
                Throwable cause = asyncResult.cause();
                logger.error("Sever internal server error: {}", cause);
                response.end("Internal Server Error");
            } else {
                response
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                    .end(asyncResult.result());
            }
        });
    }


}
