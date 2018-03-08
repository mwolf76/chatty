package org.blackcat.chatty.http.middleware.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.middleware.UserInfoHandler;

final public class UserInfoHandlerImpl implements UserInfoHandler {

    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(RoutingContext ctx) {
        User user = ctx.user();
        if (user instanceof AccessToken) {
            AccessToken accessToken = (AccessToken) user;
            if (accessToken.expired()) {
                accessToken.refresh(ar -> {
                    if (ar.failed()) {
                        ctx.session().destroy();
                        ctx.fail(ar.cause());
                    }
                    else {
                        logger.info("Access Token refreshed!");
                        userInfo(ctx, accessToken);
                    }
                });
            } else {
                userInfo(ctx, accessToken);
            }
        }
    }

    private void userInfo(RoutingContext ctx, AccessToken accessToken) {
        accessToken.userInfo(ar -> {
            if (ar.failed()) {
                logger.error("Cannot retrieve user data from oauth2 server for this user.");
                ctx.session().destroy();
                ctx.fail(ar.cause());
            } else {
                JsonObject result = ar.result();
                String email = result.getString("email");
                logger.debug("Successfully retrieved user data from oauth2 server for user {}", email);

                ctx.put("email", email);
                ctx.next();
            }
        });
    }
}