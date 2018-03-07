package org.blackcat.chatty.http.requests.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.middleware.UserInfoHandler;
import org.blackcat.chatty.http.requests.handlers.impl.LogoutHandlerImpl;

public interface LogoutRequestHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static UserInfoHandler create() {
        return new LogoutHandlerImpl();
    }
}
