package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.NewRoomHandler;
import org.blackcat.chatty.queries.Queries;

public class NewRoomHandlerImpl extends BaseUserRequestHandler implements NewRoomHandler {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkJsonRequest(ctx, this::accept);
    }

    private void accept(RoutingContext ctx) {
        MultiMap params = ctx.request().params();
        String roomName = params.get("roomName");

        Queries.findCreateRoomByName(vertx, roomName, roomMapperAsyncResult -> {
            if (roomMapperAsyncResult.failed()) {
                Throwable cause = roomMapperAsyncResult.cause();
                logger.error(cause);
                ctx.fail(cause);
            } else {
                logger.info("Created room {}", roomName);
                jsonResponseBuilder.success(ctx, new JsonObject());
            }
        });
    }
}
