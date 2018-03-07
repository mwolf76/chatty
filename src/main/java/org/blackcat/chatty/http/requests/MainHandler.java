package org.blackcat.chatty.http.requests;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.blackcat.chatty.conf.Configuration;
import org.blackcat.chatty.http.requests.impl.MainHandlerImpl;

public interface MainHandler extends Handler<HttpServerRequest> {

    String vertxKey = "vertx";
    String configurationKey = "configuration";
    String jsonResponseBuilderKey = "jsonResponseBuilder";
    String htmlResponseBuilderKey = "htmlResponseBuilder";

    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static MainHandler create(Vertx vertx, Configuration configuration) {
        return new MainHandlerImpl(vertx, configuration);
    }
}
