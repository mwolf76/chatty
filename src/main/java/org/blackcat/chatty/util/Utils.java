package org.blackcat.chatty.util;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final public class Utils {

    static final Pattern emailPattern =
        Pattern.compile("^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$");

    static final int DISK_STORAGE_UNIT = 1024;

    private Utils()
    {}

    @NotNull
    public static String urlDecode(String path) {
        Objects.requireNonNull(path);
        try {
            return URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UrlDecodingException(e);
        }
    }

    @NotNull
    public static String urlEncode(String path) {
        Objects.requireNonNull(path);
        try {
            return URLEncoder.encode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UrlEncodingException(e);
        }
    }

    @NotNull
    public static String makeTempFileName(String nameStub) {
        Objects.requireNonNull(nameStub);
        return nameStub + "." + UUID.randomUUID().toString();
    }

    public static boolean isValidEmail(String email) {
        Objects.requireNonNull(email);
        return emailPattern.matcher(email).matches();
    }

    public static boolean isValidURL(String url) {
        Objects.requireNonNull(url);
        try {
            new URI(url).parseServerAuthority();
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @NotNull
    public static String chopString(String s) {
        return s.substring(0, s.length() - 2);
    }

    public static Path protectedPath(RoutingContext ctx) {
        Path prefix = Paths.get("/protected");
        return prefix.relativize(Paths.get(urlDecode(ctx.request().path())));
    }

    @NotNull
    public static <T> JsonArray listToJsonArray(List<T> list) {
        JsonArray jsonArray = new JsonArray();
        list.stream().forEach(jsonArray::add);
        return jsonArray;
    }

    static class UrlEncodingException extends RuntimeException {
        public UrlEncodingException(Throwable throwable) {
            super(throwable);
        }
    }

    static class UrlDecodingException extends RuntimeException {
        public UrlDecodingException(Throwable throwable) {
            super(throwable);
        }
    }

    final static String uuidRegExp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    public static boolean isValidUUID(String uuid) {
        Pattern secretPattern = Pattern.compile(uuidRegExp);
        Matcher matcher = secretPattern.matcher(uuid);
        return matcher.matches();
    }
}
