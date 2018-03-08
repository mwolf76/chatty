package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.ProtectedRoomsHandler;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.queries.Queries;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ProtectedRoomsHandlerImpl extends BaseUserRequestHandler implements ProtectedRoomsHandler {

    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkHtmlRequest(ctx, this::accept);
    }

    private void accept(RoutingContext ctx) {
        String email = ctx.get("email");

        Path prefix = Paths.get("/protected/rooms/");
        Path requestPath = Paths.get(ctx.request().path());
        Path roomUUIDPath = prefix.relativize(requestPath);
        String roomUUID = roomUUIDPath.toString();

        Queries.findRoomByUUID(vertx, roomUUID, roomMapperAsyncResult -> {
            if (roomMapperAsyncResult.failed()) {
                logger.warn(roomMapperAsyncResult.cause().toString());
                htmlResponseBuilder.notFound(ctx);
            } else {
                RoomMapper room = roomMapperAsyncResult.result();
                Queries.findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
                    if (userMapperAsyncResult.failed()) {
                        Throwable cause = userMapperAsyncResult.cause();
                        logger.error(cause);
                        ctx.fail(cause);
                    } else {
                        UserMapper user = userMapperAsyncResult.result();
                        Objects.requireNonNull(user);

                        ctx
                            .put("userEmail", user.getEmail())
                            .put("userID", user.getUuid())
                            .put("roomID", room.getUuid())
                            .put("roomName", room.getName());

                        htmlResponseBuilder.success(ctx, "main");
                    }
                });
            }
        });
    }
}
