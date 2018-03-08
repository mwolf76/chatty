package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.http.Headers;
import org.blackcat.chatty.http.requests.MainHandler;
import org.blackcat.chatty.http.requests.response.impl.HtmlResponseBuilderImpl;
import org.blackcat.chatty.http.requests.response.impl.JsonResponseBuilderImpl;

import java.text.MessageFormat;

abstract public class BaseUserRequestHandler implements Handler<RoutingContext> {

    static final String requestTypeKey = "requestType";

    /* general refs */
    protected Vertx vertx;
    protected Configuration configuration;
    protected JsonResponseBuilderImpl jsonResponseBuilder;
    protected HtmlResponseBuilderImpl htmlResponseBuilder;

    final private Logger logger = LoggerFactory.getLogger(BaseUserRequestHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        logger.debug(MessageFormat.format("Invoking {0} ...", getClass().toString()));
        preprocess(ctx);
    }

    private void preprocess(RoutingContext ctx) {
        vertx = ctx.get(MainHandler.vertxKey);
        if (vertx == null) {
            ctx.fail(new BaseUserRequestException("vertx == null"));
        }

        configuration = ctx.get(MainHandler.configurationKey);
        if (configuration == null) {
            ctx.fail(new BaseUserRequestException("configuration == null"));
        }

        jsonResponseBuilder = ctx.get(MainHandler.jsonResponseBuilderKey);
        if (jsonResponseBuilder == null) {
            ctx.fail(new BaseUserRequestException("jsonResponseBuilder == null"));
        }

        htmlResponseBuilder = ctx.get(MainHandler.htmlResponseBuilderKey);
        if (htmlResponseBuilder == null) {
            ctx.fail(new BaseUserRequestException("htmlResponseBuilder == null"));
        }

        ctx.put(requestTypeKey, doesAcceptJson(ctx) ? RequestType.JSON : RequestType.HTML);
    }

    private boolean doesAcceptJson(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);

        return (accept != null && accept.contains("application/json"));
    }

    public void checkHtmlRequest(RoutingContext ctx, Handler<RoutingContext> handler) {
        if (! ctx.get(requestTypeKey).equals(RequestType.HTML)) {
            logger.warn("Expected HTML request, got JSON. Rejecting...");
            htmlResponseBuilder.badRequest(ctx);
        }

        else handler.handle(ctx); /* ok */
    }

    public void checkJsonRequest(RoutingContext ctx, Handler<RoutingContext> handler) {
        if (! ctx.get(requestTypeKey).equals(RequestType.JSON)) {
            logger.warn("Expected JSON request, got HTML. Rejecting...");
            jsonResponseBuilder.badRequest(ctx);
        }

        else handler.handle(ctx); /* ok */
    }
}
