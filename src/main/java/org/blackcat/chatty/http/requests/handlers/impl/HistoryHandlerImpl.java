package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.HistoryHandler;
import org.blackcat.chatty.mappers.MessageMapper;
import org.blackcat.chatty.mappers.RoomMapper;
import org.blackcat.chatty.mappers.UserMapper;
import org.blackcat.chatty.queries.Queries;
import org.blackcat.chatty.util.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class HistoryHandlerImpl extends BaseUserRequestHandler implements HistoryHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkJsonRequest(ctx, this::accept);
    }

    private void accept(RoutingContext ctx) {
        String email = ctx.get("email");
        Path prefix = Paths.get("/protected/history");
        String roomUID = prefix.relativize(Paths.get(Utils.urlDecode(ctx.request().path()))).toString();

        Queries.findCreateUserEntityByEmail(vertx, email, userMapperAsyncResult -> {
            if (userMapperAsyncResult.failed()) {
                logger.warn(userMapperAsyncResult.cause());
                jsonResponseBuilder.forbidden(ctx);
            } else {
                UserMapper user = userMapperAsyncResult.result();

                Queries.findRoomByUUID(vertx, roomUID, roomMapperAsyncResult -> {
                    if (roomMapperAsyncResult.failed()) {
                        logger.warn(roomMapperAsyncResult.cause());
                        jsonResponseBuilder.notFound(ctx);
                    } else {
                        RoomMapper room = roomMapperAsyncResult.result();

                        Queries.fetchMessages(vertx, user, room, messagesAsyncResult -> {
                            if (messagesAsyncResult.failed()) {
                                logger.error(messagesAsyncResult.cause());
                            } else {
                                List<MessageMapper> messages =
                                    messagesAsyncResult.result();

                                List<JsonArray> history = messages.stream()
                                                              .map(this::formatJsonMessage)
                                                              .collect(Collectors.toList());

                                jsonResponseBuilder.success(ctx, new JsonObject()
                                                                     .put("history", history));
                            }
                        });
                    }
                });
            }
        });
    }

    private JsonArray formatJsonMessage(MessageMapper messageMapper) {
        return new JsonArray()
                   .add(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(
                       Date.from(Instant.from(ISO_INSTANT.parse(messageMapper.getTimeStamp())))))
                   .add(messageMapper.getAuthor().getEmail())
                   .add(messageMapper.getText());
    }
}
