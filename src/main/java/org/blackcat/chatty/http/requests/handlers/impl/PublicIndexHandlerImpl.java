package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.PublicIndexHandler;

final public class PublicIndexHandlerImpl extends BaseUserRequestHandler implements PublicIndexHandler {

    final private Logger logger = LoggerFactory.getLogger(PublicIndexHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkHtmlRequest(ctx, ok -> {
            logger.debug("Serving public index page");
            htmlResponseBuilder.success(ctx, "index");
        });
    }
}
