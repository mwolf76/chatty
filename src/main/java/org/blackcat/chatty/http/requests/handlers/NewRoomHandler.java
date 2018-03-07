package org.blackcat.chatty.http.requests.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.impl.NewRoomHandlerImpl;

public interface NewRoomHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static NewRoomHandler create() {
        return new NewRoomHandlerImpl();
    }
}
