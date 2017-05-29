package org.blackcat.chatty.conf;

import io.vertx.core.json.JsonObject;
import org.blackcat.chatty.conf.exceptions.ConfigurationException;

import java.text.MessageFormat;

import static org.blackcat.chatty.conf.Keys.*;

/**
 * Configuration parser
 */
final public class Configuration {

    /* Configuration data */
    private int httpPort;
    private boolean useSSL;
    private String keystoreFilename;
    private String keystorePassword;
    private int timeout;

    /* database section */
    private String dbType;
    private String dbHost;
    private int dbPort;
    private String dbName;

    /* redis section */
    private String redisHost;
    private int redisPort;
    private int redisDatabaseIndex;

    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public int getRedisDatabaseIndex() {
        return redisDatabaseIndex;
    }

    /* oauth2 section */
    private String oauth2Provider;
    private String oauth2ClientID;
    private String oauth2ClientSecret;
    private String oauth2Domain;

    public int getHttpPort() {
        return httpPort;
    }

    public boolean sslEnabled() {
        return useSSL;
    }

    public String getKeystoreFilename() {
        return keystoreFilename;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public int getTimeout() {
        return timeout;
    }

    void parseServerSection(JsonObject jsonObject) {
        final JsonObject serverSection = jsonObject.getJsonObject(SERVER_SECTION);

        this.httpPort = serverSection.getInteger(SERVER_HTTP_PORT, DEFAULT_SERVER_HTTP_PORT);
        this.useSSL = serverSection.getBoolean(SERVER_USE_SSL, DEFAULT_SERVER_USE_SSL);
        if (useSSL) {
            this.keystoreFilename = serverSection.getString(SERVER_KEYSTORE_FILENAME, DEFAULT_SERVER_KEYSTORE_FILENAME);
            this.keystorePassword = serverSection.getString(SERVER_KEYSTORE_PASSWORD, DEFAULT_SERVER_KEYSTORE_PASSWORD);
        }
        this.timeout = serverSection.getInteger(SERVER_TIMEOUT, DEFAULT_SERVER_TIMEOUT);
    }

    public String getOauth2Provider() {
        return oauth2Provider;
    }

    public String getOauth2ClientID() {
        return oauth2ClientID;
    }

    public String getOauth2ClientSecret() {
        return oauth2ClientSecret;
    }

    public String getOAuth2Domain() {
        return oauth2Domain + ':' + getHttpPort();
    }

    public String getDatabaseType() {
        return dbType;
    }

    public String getDatabaseHost() {
        return dbHost;
    }

    public int getDatabasePort() {
        return dbPort;
    }

    public String getDatabaseName() {
        return dbName;
    }

    void parseDatabaseSection(JsonObject jsonObject) {
        final JsonObject databaseSection = jsonObject.getJsonObject(DATABASE_SECTION);

        this.dbType = databaseSection.getString(DATABASE_TYPE);
        if (! dbType.equals(DATABASE_TYPE_MONGODB)) {
            throw new ConfigurationException( MessageFormat.format(
                    "Unsupported oauth2 provider: {0}", oauth2Provider));
        }
        this.dbHost = databaseSection.getString(DATABASE_HOST, DEFAULT_DATABASE_HOST);
        this.dbPort = databaseSection.getInteger(DATABASE_PORT, DEFAULT_DATABASE_PORT);
        this.dbName = databaseSection.getString(DATABASE_NAME, DEFAULT_DATABASE_NAME);
    }

    void parseRedisSection(JsonObject jsonObject) {
        final JsonObject redisSection = jsonObject.getJsonObject(REDIS_SECTION);

        this.redisHost = redisSection.getString(REDIS_HOST, DEFAULT_REDIS_HOST);
        this.redisPort = redisSection.getInteger(REDIS_PORT, DEFAULT_REDIS_PORT);
        this.redisDatabaseIndex = redisSection.getInteger(REDIS_DATABASE_INDEX, DEFAULT_REDIS_DATABASE_INDEX);
    }

    void parseOAuth2Section(JsonObject jsonObject) {
        final JsonObject oauth2Section = jsonObject.getJsonObject(OAUTH2_SECTION);

        this.oauth2Provider = oauth2Section.getString(OAUTH2_PROVIDER);
        // TODO: add more supported providers
        if (! oauth2Provider.equals(OAUTH2_PROVIDER_GOOGLE)) {
            throw new ConfigurationException( MessageFormat.format(
                    "Unsupported oauth2 provider: {0}", oauth2Provider));
        }
        this.oauth2ClientID = oauth2Section.getString(OAUTH2_CLIENT_ID);
        this.oauth2ClientSecret = oauth2Section.getString(OAUTH2_CLIENT_SECRET);
        this.oauth2Domain = oauth2Section.getString(OAUTH2_DOMAIN);
    }

    public Configuration(JsonObject jsonObject) {
        parseServerSection(jsonObject);
        parseDatabaseSection(jsonObject);
        parseRedisSection(jsonObject);
        parseOAuth2Section(jsonObject);
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "httpPort=" + httpPort +
                ", useSSL=" + useSSL +
                ", keystoreFilename='" + keystoreFilename + '\'' +
                ", keystorePassword='" + keystorePassword + '\'' +
                ", oauth2Provider='" + oauth2Provider + '\'' +
                ", oauth2ClientID='" + oauth2ClientID + '\'' +
                ", oauth2ClientSecret='" + oauth2ClientSecret + '\'' +
                ", OAuth2Domain='" + getOAuth2Domain() + '\'' +
                ", timeout=" + timeout +
                ", sslEnabled=" + sslEnabled() +
                ", databaseType='" + getDatabaseType() + '\'' +
                ", databaseHost='" + getDatabaseHost() + '\'' +
                ", databasePort=" + getDatabasePort() +
                ", databaseName='" + getDatabaseName() + '\'' +
                ", redisHost='" + getRedisHost() + '\'' +
                ", redisPort=" + getRedisPort() +
                ", redisDatabaseIndex=" + getRedisDatabaseIndex() +
                '}';
    }
}