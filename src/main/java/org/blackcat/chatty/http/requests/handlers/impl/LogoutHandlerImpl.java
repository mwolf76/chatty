package org.blackcat.chatty.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.chatty.http.middleware.UserInfoHandler;
import org.blackcat.chatty.http.requests.response.ResponseUtils;

final public class LogoutHandlerImpl extends BaseUserRequestHandler implements UserInfoHandler {

    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkHtmlRequest(ctx, ok -> {
            accept(ctx);
        });
    }

    private void accept(RoutingContext ctx) {
        String email = ctx.get("email");
        logger.info("Logged out user {}. Redirecting to / ...", email);

        AccessToken user = (AccessToken) ctx.user();
        if (user == null || configuration.getOauth2Provider().equals("google")) {
            cleanupAndRedirect(ctx);
        } else {
            user.logout(ar -> {
                if (ar.failed())
                    ctx.fail(ar.cause());
                else {
                    cleanupAndRedirect(ctx);
                }
            });
        }
    }

    private void cleanupAndRedirect(RoutingContext ctx) {
        ctx.clearUser();
        ctx.session().destroy();
        ResponseUtils.found(ctx, "/");
    }
}
