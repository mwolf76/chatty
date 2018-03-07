package org.blackcat.chatty.conf;

import io.vertx.core.json.JsonObject;
import org.blackcat.chatty.conf.exceptions.ConfigurationException;
import org.blackcat.chatty.util.Utils;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.blackcat.chatty.conf.Keys.*;

/**
 * Configuration parser
 */
final public class Configuration {

    /* http section */
    private String domain;
    private int startTimeout;

    /* HTTP(S) server conf */
    private String httpHost;
    private int httpPort;
    private boolean useSSL;
    private String keystoreFilename;
    private String keystorePassword;

    /* database section */
    private String dbType;
    private String dbHost;
    private int dbPort;
    private String dbName;

    /* redis section */
    private String redisHost;
    private int redisPort;
    private int redisDatabaseIndex;

    /* oauth2 section */
    private String oauth2Provider;

    private String oauth2ClientID;
    private String oauth2ClientSecret;
    /* keycloak only */

    private String oauth2AuthServerURL;
    private String oauth2AuthServerRealm;
    private String oauth2AuthServerPublicKey;
    /* storage section */

    private String storageRoot;
    public String getDomain() {
        return domain;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public boolean isSSLEnabled() {
        return useSSL;
    }

    public String getKeystoreFilename() {
        return keystoreFilename;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public int getStartTimeout() {
        return startTimeout;
    }

    void parseServerSection(JsonObject jsonObject) {
        JsonObject serverSection = jsonObject.getJsonObject(SERVER_SECTION, new JsonObject());

        this.startTimeout = serverSection.getInteger(SERVER_START_TIMEOUT, DEFAULT_SERVER_START_TIMEOUT);
        this.httpHost = serverSection.getString(SERVER_HTTP_HOST, DEFAULT_SERVER_HTTP_HOST);
        this.httpPort = serverSection.getInteger(SERVER_HTTP_PORT, DEFAULT_SERVER_HTTP_PORT);
        this.useSSL = serverSection.getBoolean(SERVER_USE_SSL, DEFAULT_SERVER_USE_SSL);
        if (useSSL) {
            this.keystoreFilename = serverSection.getString(SERVER_KEYSTORE_FILENAME, DEFAULT_SERVER_KEYSTORE_FILENAME);
            this.keystorePassword = serverSection.getString(SERVER_KEYSTORE_PASSWORD, DEFAULT_SERVER_KEYSTORE_PASSWORD);
        }

        this.domain = serverSection.getString(SERVER_DOMAIN);
        if (domain == null) {
            domain = String.format("%s://%s:%d", useSSL ? "https" : "http", httpHost, httpPort);
        }
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

    public String getOauth2AuthServerURL() {
        return oauth2AuthServerURL;
    }

    public String getOauth2AuthServerRealm() {
        return oauth2AuthServerRealm;
    }

    public String getOauth2AuthServerPublicKey() {
        return oauth2AuthServerPublicKey;
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

    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public int getRedisDatabaseIndex() {
        return redisDatabaseIndex;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    void parseDatabaseSection(JsonObject jsonObject) {
        JsonObject databaseSection = jsonObject.getJsonObject(DATABASE_SECTION, new JsonObject());

        // TODO: 1/25/18 Support more databases
        this.dbType = databaseSection.getString(DATABASE_TYPE, DATABASE_TYPE_MONGODB);
        if (! dbType.equals(DATABASE_TYPE_MONGODB)) {
            throw new ConfigurationException( MessageFormat.format(
                    "Unsupported database: {0}", dbType));
        }

        // db-independent configuration
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
        JsonObject oauth2Section = jsonObject.getJsonObject(OAUTH2_SECTION, new JsonObject());

        // TODO: 1/25/18 Support more oauth2 providers
        this.oauth2Provider = oauth2Section.getString(OAUTH2_PROVIDER);
        if (oauth2Provider == null) {
            throw new ConfigurationException("No oauth2 provider specified");
        } else if (
            !oauth2Provider.equals(OAUTH2_PROVIDER_GOOGLE) &&
                !oauth2Provider.equals(OAUTH2_PROVIDER_KEYCLOAK)) {
            throw new ConfigurationException(MessageFormat.format(
                "Unsupported oauth2 provider: {0}", oauth2Provider));
        }

        // provider-independent configuration
        this.oauth2ClientID = oauth2Section.getString(OAUTH2_CLIENT_ID);
        if (oauth2ClientID == null) {
            throw new ConfigurationException("No oauth2 client ID specified");
        }

        this.oauth2ClientSecret = oauth2Section.getString(OAUTH2_CLIENT_SECRET);
        if (oauth2ClientSecret == null) {
            throw new ConfigurationException("No oauth2 client secret specified");
        }

        if (oauth2Provider.equals(OAUTH2_PROVIDER_GOOGLE)) {
            validateGoogleOauth2Settings(oauth2Section);
        } else if (oauth2Provider.equals(OAUTH2_PROVIDER_KEYCLOAK)) {
            validateKeyCloakOauth2Settings(oauth2Section);
        }
    }

    private void validateGoogleOauth2Settings(JsonObject oauth2Section) {
        validateGoogleOAuth2ClientID(oauth2Section);
        validateGoogleOAuth2ClientSecret(oauth2Section);
    }

    final static String googleClientSuffix = ".apps.googleusercontent.com";
    private void validateGoogleOAuth2ClientID(JsonObject oauth2Section) {
        String clientID = oauth2Section.getString(OAUTH2_CLIENT_ID);
        if (! clientID.endsWith(googleClientSuffix))
            throw new ConfigurationException(MessageFormat.format("{0} is not a valid Google client ID", clientID));
    }

    final static String googleSecretRegExp = "^[0-9a-zA-Z]{24}$";
    private void validateGoogleOAuth2ClientSecret(JsonObject oauth2Section) {
        String secret = oauth2Section.getString(OAUTH2_CLIENT_SECRET);
        Pattern uuidPattern = Pattern.compile(googleSecretRegExp);
        Matcher matcher = uuidPattern.matcher(secret);
        if (! matcher.matches())
            throw new ConfigurationException( MessageFormat.format("{0} is not a valid UUID", secret));
    }

    private void validateKeyCloakOauth2Settings(JsonObject jsonObject) {
        validateKeycloakOAuth2ClientID(jsonObject);
        validateKeycloakOAuth2ClientSecret(jsonObject);

        JsonObject oauth2KeycloakSection = jsonObject.getJsonObject(OAUTH2_KEYCLOAK_SECTION, new JsonObject());

        this.oauth2AuthServerURL = oauth2KeycloakSection.getString(OAUTH2_KEYCLOAK_AUTH_SERVER_URL,
            DEFAULT_OAUTH2_KEYCLOAK_AUTH_SERVER_URL);
        validateKeycloakAuthServerURL(oauth2AuthServerURL);

        this.oauth2AuthServerRealm = oauth2KeycloakSection.getString(OAUTH2_KEYCLOAK_AUTH_SERVER_REALM,
            DEFAULT_KEYCLOAK_OAUTH2_AUTH_SERVER_REALM);
        validateKeycloakAuthRealm(oauth2AuthServerRealm);

        this.oauth2AuthServerPublicKey = oauth2KeycloakSection.getString(OAUTH2_KEYCLOAK_AUTH_SERVER_PUBLIC_KEY,
            DEFAULT_KEYCLOAK_OAUTH2_AUTH_SERVER_PUBLIC_KEY);
        /* mmmhhh... validating public would entail cracking RSA! we can not perform validation here */
    }

    private void validateKeycloakOAuth2ClientID(JsonObject jsonObject) {
        String id = jsonObject.getString(OAUTH2_CLIENT_ID);
        if (id.isEmpty())
            throw new ConfigurationException("Client ID must be a non-empty alphanumerical string");
    }

    final static String keyCloakSecretRegExp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private void validateKeycloakOAuth2ClientSecret(JsonObject jsonObject) {
        String secret = jsonObject.getString(OAUTH2_CLIENT_SECRET);
        if (secret.isEmpty())
            throw new ConfigurationException("Client secret must be a non-empty alphanumerical string");

        Pattern secretPattern = Pattern.compile(keyCloakSecretRegExp);
        Matcher matcher = secretPattern.matcher(secret);
        if (! matcher.matches())
            throw new ConfigurationException( MessageFormat.format("{0} is not a valid client secret", secret));
    }

    private void validateKeycloakAuthRealm(String oauth2AuthServerRealm) {
        if (oauth2AuthServerRealm.isEmpty())
            throw new ConfigurationException("Realm must me a non empty string");
    }

    private void validateKeycloakAuthServerURL(String oauth2AuthServerURL) {
        if (!Utils.isValidURL(oauth2AuthServerURL))
            throw new ConfigurationException(MessageFormat.format("{0} is not a valid URL", oauth2AuthServerURL));
    }

    void parseStorageSection(JsonObject jsonObject) {
        JsonObject storageSection = jsonObject.getJsonObject(STORAGE_SECTION, new JsonObject());
        this.storageRoot = storageSection.getString(STORAGE_ROOT, DEFAULT_STORAGE_ROOT);
    }

    public Configuration(JsonObject jsonObject) {
        parseServerSection(jsonObject);
        parseDatabaseSection(jsonObject);
        parseRedisSection(jsonObject);
        parseOAuth2Section(jsonObject);
        parseStorageSection(jsonObject);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Configuration{");
        sb.append(String.format("domain='%s'", domain));
        sb.append(String.format(",startTimeout=%d", startTimeout));
        sb.append(String.format(",httpHost='%s'", httpHost));
        sb.append(String.format(",httpPort=%s", httpPort));
        if (useSSL) {
            sb.append(String.format(",keystoreFilename='%s'", keystoreFilename));
            sb.append(String.format(",keystorePassword=<hidden>"));
        }

        sb.append(String.format(",redisHost='%s'", redisHost));
        sb.append(String.format(",redisPort=%d", redisPort));
        sb.append(String.format(",redisDatabaseIndex='%s'", redisDatabaseIndex));

        sb.append(String.format(",dbType='%s'", dbType));
        sb.append(String.format(",dbHost='%s'", dbHost));
        sb.append(String.format(",dbPort=%d", dbPort));
        sb.append(String.format(",dbName='%s'", dbName));

        sb.append(String.format(",oauth2Provider='%s'", oauth2Provider));
        sb.append(String.format(",oauth2ClientID='%s'", oauth2ClientID));
        sb.append(String.format(",oauth2ClientSecret='%s'", oauth2ClientSecret));

        if (oauth2Provider.equals("keycloak")) {
            sb.append(String.format(",keyCloakServerURL='%s'", oauth2AuthServerRealm));
            sb.append(String.format(",realm='%s'", oauth2AuthServerRealm));
            sb.append(String.format(",realPublicKey='%s'", oauth2AuthServerPublicKey));
        }
        sb.append("}");

        return sb.toString();
    }

    public static Configuration create(JsonObject config) {
        return new Configuration(config);
    }

    /* keycloak helper method */
    public JsonObject buildKeyCloakConfiguration() {
        return new JsonObject()
                   .put("realm", getOauth2AuthServerRealm())
                   .put("realm-public-key", getOauth2AuthServerPublicKey())
                   .put("auth-server-url", getOauth2AuthServerURL())
                   .put("ssl-required", "external")
                   .put("resource", getOauth2ClientID())
                   .put("credentials",
                       new JsonObject()
                           .put("secret", getOauth2ClientSecret()));
    }
}