package org.blackcat.chatty.conf;

final public class Keys {
    /* SERVER */
    public static final String SERVER_SECTION = "web";

    public static final String SERVER_DOMAIN = "domain";
    public static final String SERVER_HTTP_HOST = "host";
    public static final String DEFAULT_SERVER_HTTP_HOST = "localhost";

    public static final String SERVER_HTTP_PORT = "port";
    public static final int DEFAULT_SERVER_HTTP_PORT = 8080;

    public static final String SERVER_USE_SSL = "useSSL";
    public static final boolean DEFAULT_SERVER_USE_SSL = false;

    public static final String SERVER_KEYSTORE_FILENAME = "keystoreFilename";
    public static final String DEFAULT_SERVER_KEYSTORE_FILENAME = "server-keystore.jks";

    public static final String SERVER_KEYSTORE_PASSWORD = "keystorePassword";
    public static final String DEFAULT_SERVER_KEYSTORE_PASSWORD = "password";

    public static final String SERVER_START_TIMEOUT = "timeout";
    public static final int DEFAULT_SERVER_START_TIMEOUT = 30;

    /* STORAGE */
    public static final String STORAGE_SECTION = "storage";
    public static final String STORAGE_ROOT = "root";
    public static final String DEFAULT_STORAGE_ROOT = "/var/lib/trunk";

    /* DATABASE */
    public static final String DATABASE_SECTION = "database";

    public static final String DATABASE_TYPE = "type";
    public static final String DATABASE_TYPE_MONGODB = "mongodb";

    public static final String DATABASE_HOST = "host";
    public static final String DEFAULT_DATABASE_HOST = "localhost";

    public static final String DATABASE_PORT = "port";
    public static final int DEFAULT_DATABASE_PORT = 27017;

    public static final String DATABASE_NAME = "name";
    public static final String DEFAULT_DATABASE_NAME = "data";

    /* REDIS */
    static String REDIS_SECTION = "redis";

    static String REDIS_HOST = "host";
    static String DEFAULT_REDIS_HOST = "locahost";

    static String REDIS_PORT = "port";
    static int DEFAULT_REDIS_PORT = 6379;

    static String REDIS_DATABASE_INDEX = "index";
    static int DEFAULT_REDIS_DATABASE_INDEX = 0;

    /* OAUTH2 */
    public static final String OAUTH2_SECTION = "oauth2";

    public static final String OAUTH2_PROVIDER = "provider";
    public static final String OAUTH2_PROVIDER_GOOGLE = "google";
    public static final String OAUTH2_PROVIDER_KEYCLOAK = "keycloak";
    public static final String OAUTH2_CLIENT_ID = "clientID";
    public static final String OAUTH2_CLIENT_SECRET = "clientSecret";

    /* reserved for keycloak oauth2 */
    public static final String OAUTH2_KEYCLOAK_SECTION = "keycloak";
    public static final String OAUTH2_KEYCLOAK_AUTH_SERVER_URL = "URL";
    public static final String DEFAULT_OAUTH2_KEYCLOAK_AUTH_SERVER_URL = "http://localhost:9000/auth";

    public static final String OAUTH2_KEYCLOAK_AUTH_SERVER_REALM = "realm";
    public static final String DEFAULT_KEYCLOAK_OAUTH2_AUTH_SERVER_REALM = "master";

    public static final String OAUTH2_KEYCLOAK_AUTH_SERVER_PUBLIC_KEY = "realmPublicKey";
    public static final String DEFAULT_KEYCLOAK_OAUTH2_AUTH_SERVER_PUBLIC_KEY = null;

    private Keys()
    {}
}
