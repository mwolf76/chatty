//package org.blackcat.chatty.http.responses;
//
//import io.vertx.ext.web.RoutingContext;
//import org.blackcat.chatty.http.Headers;
//
//public final class TextResponse {
//
//    private TextResponse()
//    {}
//
//    /**
//     * Terminates a request with a text/plain body.
//     *
//     * @param ctx
//     * @param body
//     */
//    public static void done(RoutingContext ctx, String body) {
//        ctx.response()
//                .putHeader(Headers.CONTENT_TYPE_HEADER, "text/plain")
//                .end(body);
//    }
//}
