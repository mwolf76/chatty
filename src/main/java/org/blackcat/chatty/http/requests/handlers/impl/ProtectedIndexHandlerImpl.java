package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.ProtectedRoomsHandler;
import org.blackcat.chatty.http.requests.response.ResponseUtils;
import org.blackcat.chatty.queries.Queries;

public class ProtectedIndexHandlerImpl extends BaseUserRequestHandler implements ProtectedRoomsHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkHtmlRequest(ctx, ok -> {
            accept(ctx);
        });
    }

    private void accept(RoutingContext ctx) {
        Queries.getGeneralRoomUUID(vertx, stringAsyncResult -> {
            if (stringAsyncResult.failed()) {
                Throwable cause = stringAsyncResult.cause();
                logger.error(cause);
                ctx.fail(cause);
            } else {
                String roomUUID = stringAsyncResult.result();
                String redirectTo = "/protected/rooms/" + roomUUID;
                logger.info("Redirecting to {}", redirectTo);
                ResponseUtils.found(ctx, redirectTo);
            }
        });
    }
}
