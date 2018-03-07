package org.blackcat.chatty.http.middleware;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.middleware.impl.UserInfoHandlerImpl;

public interface UserInfoHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static UserInfoHandler create() {
        return new UserInfoHandlerImpl();
    }
}