package org.blackcat.chatty.conf;

public class Keys {
    /* SERVER */
    static String SERVER_SECTION = "server";

    static String SERVER_HTTP_PORT = "port";
    static int DEFAULT_SERVER_HTTP_PORT = 8080;

    static String SERVER_USE_SSL = "useSSL";
    static boolean DEFAULT_SERVER_USE_SSL = false;

    static String SERVER_KEYSTORE_FILENAME = "keystoreFilename";
    static String DEFAULT_SERVER_KEYSTORE_FILENAME = "server-keystore.jks";

    static String SERVER_KEYSTORE_PASSWORD = "keystorePassword";
    static String DEFAULT_SERVER_KEYSTORE_PASSWORD = "password";

    static String SERVER_TIMEOUT = "timeout";
    static int DEFAULT_SERVER_TIMEOUT = 30;

    /* DATABASE */
    static String DATABASE_SECTION = "database";

    static String DATABASE_TYPE = "type";
    static String DATABASE_TYPE_MONGODB = "mongodb";

    static String DATABASE_HOST = "host";
    static String DEFAULT_DATABASE_HOST = "locahost";

    static String DATABASE_PORT = "port";
    static int DEFAULT_DATABASE_PORT = 27027;

    static String DATABASE_NAME = "name";
    static String DEFAULT_DATABASE_NAME = "data";

    /* REDIS */
    static String REDIS_SECTION = "redis";

    static String REDIS_HOST = "host";
    static String DEFAULT_REDIS_HOST = "locahost";

    static String REDIS_PORT = "port";
    static int DEFAULT_REDIS_PORT = 6379;

    static String REDIS_DATABASE_INDEX = "index";
    static int DEFAULT_REDIS_DATABASE_INDEX = 0;

    /* OAUTH2 */
    static String OAUTH2_SECTION = "oauth2";

    static String OAUTH2_PROVIDER = "provider";
    static String OAUTH2_PROVIDER_GOOGLE = "google";
    // TODO: add more providers

    static String OAUTH2_CLIENT_ID = "clientID";
    static String OAUTH2_CLIENT_SECRET = "clientSecret";

    static String OAUTH2_DOMAIN = "domain";

    private Keys()
    {}
}
