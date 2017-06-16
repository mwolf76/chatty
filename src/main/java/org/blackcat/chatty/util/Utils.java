package org.blackcat.chatty.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.web.RoutingContext;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.regex.Pattern;

final public class Utils {

    final static Pattern emailPattern =
            Pattern.compile("^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$");

    private Utils()
    {}

    public static String getSessionUserEmail(RoutingContext ctx) {
        User User = ctx.user();
        AccessToken at = (AccessToken) User;

        JsonObject idToken = KeycloakHelper.idToken(at.principal());
        String email = idToken.getString("email");

        return email;
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static String urlDecode(String path) {
        String res;

        try {
            res = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    public static String urlEncode(String path) {
        String res;

        try {
            res = URLEncoder.encode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    public static String makeTempFileName(String nameStub) {
        StringBuilder sb = new StringBuilder();

        sb.append(nameStub);
        sb.append(".");
        sb.append(UUID.randomUUID().toString());

        return sb.toString();
    }

    public static boolean isValidEmail(String email) {
        return emailPattern.matcher(email).matches();
    }

    public static boolean isValidUUID(String uuid) {
        boolean res = true;

        try {
            final UUID uuid1 = UUID.fromString(uuid);
        } catch (IllegalArgumentException iae) {
            res = false;
        }

        return res;
    }
}
