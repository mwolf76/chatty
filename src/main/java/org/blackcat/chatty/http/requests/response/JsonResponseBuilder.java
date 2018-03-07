package org.blackcat.chatty.http.requests.response;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public interface JsonResponseBuilder extends ResponseBuilder {
    void success(RoutingContext ctx, JsonObject result);
}