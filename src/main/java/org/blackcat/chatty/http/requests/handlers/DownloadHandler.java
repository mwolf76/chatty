package org.blackcat.chatty.http.requests.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.requests.handlers.impl.DownloadHandlerImpl;

public interface DownloadHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static DownloadHandler create() {
        return new DownloadHandlerImpl();
    }
}
