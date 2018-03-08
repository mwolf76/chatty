package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.DownloadHandler;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.queries.Queries;
import org.blackcat.chatty.util.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DownloadHandlerImpl extends BaseUserRequestHandler implements DownloadHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkHtmlRequest(ctx, this::accept);
    }

    private void accept(RoutingContext ctx) {
        String email = ctx.get("email");

        Path requestPath = Paths.get(Utils.urlDecode(ctx.request().path()));
        Path prefix = Paths.get("/protected/download");

        String roomUID = prefix.relativize(requestPath).toString();
        if (! Utils.isValidUUID(roomUID)) {
            logger.error("Invalid room UUID: {}", roomUID);
            htmlResponseBuilder.badRequest(ctx);
        } else {
            logger.info("Downloading messages for room UUID {}", roomUID);

            Queries.findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed()) {
                    Throwable cause = userMapperAsyncResult.cause();

                    logger.error(cause);
                    ctx.fail(cause);
                } else {
                    UserMapper user = userMapperAsyncResult.result();
                    Queries.findRoomByUUID(vertx, roomUID, roomMapperAsyncResult -> {
                        if (roomMapperAsyncResult.failed()) {
                            Throwable cause = roomMapperAsyncResult.cause();

                            logger.error(cause);
                            ctx.fail(cause);
                        } else {
                            RoomMapper room = roomMapperAsyncResult.result();
                            Queries.fetchMessages(vertx, user, room, messagesAsyncResult -> {
                                if (messagesAsyncResult.failed()) {
                                    Throwable cause = messagesAsyncResult.cause();

                                    logger.error(cause);
                                    ctx.fail(cause);
                                } else {
                                    List<MessageMapper> messageMappers =
                                        messagesAsyncResult.result();

                                    ctx.put("messages", messageMappers);
                                    htmlResponseBuilder.success(ctx, "download");
                                }
                            });
                        }
                    });
                }
            });
        }
    }
}
