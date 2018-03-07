package org.blackcat.chatty.http.requests.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.impl.ProtectedIndexHandlerImpl;

public interface ProtectedIndexHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static ProtectedRoomsHandler create() {
        return new ProtectedIndexHandlerImpl();
    }
}
