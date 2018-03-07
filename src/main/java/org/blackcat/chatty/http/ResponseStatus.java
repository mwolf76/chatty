package org.blackcat.chatty.http;

/**
 * Enum for HTTP status codes
 */

public enum ResponseStatus {
    OK(200, "OK"),

    FOUND(302, "Found"),

    BAD_REQUEST(400, "Bad Request"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    CONFLICT(409, "Conflict"),

    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int statusCode;
    private final String statusMessage;

    ResponseStatus(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    @Override
    public String toString() {
        return statusCode + " " + statusMessage;
    }
}