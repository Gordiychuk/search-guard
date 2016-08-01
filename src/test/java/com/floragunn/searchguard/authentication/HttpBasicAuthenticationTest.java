package com.floragunn.searchguard.authentication;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;
import com.floragunn.searchguard.support.Base64Helper;
import com.floragunn.searchguard.utils.EmbeddedServer;
import com.floragunn.searchguard.utils.ResourceUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assume.assumeThat;

public class HttpBasicAuthenticationTest {
    private static final ESLogger LOGGER = Loggers.getLogger(HttpBasicAuthenticationTest.class);

    private static final int COUNT_NODE = 1;
    private final static ResourceUtils RESOURCES = new ResourceUtils(HttpBasicAuthenticationTest.class);
    private static EmbeddedServer server;
    private static String httpDomain;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final Settings settings =
                Settings.settingsBuilder()
                        .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, true)
                        .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, RESOURCES.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
                        .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, RESOURCES.getAbsoluteFilePathFromClassPath("truststore.jks"))
                        .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION, false)
                        .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION_RESOLVE_HOST_NAME, false)
                        .putArray("searchguard.authcz.admin_dn", "CN=kirk,OU=client,O=client,l=tEst, C=De")
                        .build();

        server = new EmbeddedServer(settings);
        server.start(COUNT_NODE);

        Set<InetSocketTransportAddress> nodes = server.getHttpAddresses();

        TransportAddress address = Iterables.getFirst(nodes, null);
        assert address != null;
        httpDomain = "http://" + address.getHost() + ":" + address.getPort();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void unauthorizedStatusForRestWithoutBasic() throws Exception {
        loadConfiguration("authentication/basic/minimal");

        int statusCode =
                Request.Get(httpDomain + "/_search")
                        .execute()
                        .returnResponse()
                        .getStatusLine()
                        .getStatusCode();

        assertThat("When anonymous authentication disabled and not specified http basic user info, " +
                        "search guard should reject request with unauthorized status(401) ",
                statusCode, equalTo(HttpStatus.SC_UNAUTHORIZED)
        );
    }

    @Test
    public void unauthorizedStatusForRestWithNotValidPassword() throws Exception {
        loadConfiguration("authentication/basic/minimal");


        int statusCode =
                Request.Get(httpDomain + "/_search")
                        .addHeader(new BasicHeader("Authorization", "Basic " + Base64Helper.encodeBasicHeader("test_user", "notValidPassword")))
                        .execute()
                        .returnResponse()
                        .getStatusLine()
                        .getStatusCode();

        assertThat("When user name not exists or password not valid, rest api shoul return unauthorized status(401). " +
                        "401 response indicates that authorization has been refused for those credentials",
                statusCode, equalTo(HttpStatus.SC_UNAUTHORIZED)
        );
    }

    @Test
    public void unauthorizedStatusForRestWithNotExistUser() throws Exception {
        loadConfiguration("authentication/basic/minimal");


        int statusCode =
                Request.Get(httpDomain + "/_search")
                        .addHeader(new BasicHeader("Authorization", "Basic " + Base64Helper.encodeBasicHeader("notExistsUser", "notValidPassword")))
                        .execute()
                        .returnResponse()
                        .getStatusLine()
                        .getStatusCode();

        assertThat("When user name not exists or password not valid, rest api shoul return unauthorized status(401). " +
                        "401 response indicates that authorization has been refused for those credentials",
                statusCode, equalTo(HttpStatus.SC_UNAUTHORIZED)
        );
    }

    @Test
    public void authorizedStatusForRestWithValidUserAndPassword() throws Exception {
        loadConfiguration("authentication/basic/minimal");

        int statusCode =
                Request.Get(httpDomain + "/_search")
                        .addHeader(new BasicHeader("Authorization", "Basic " + Base64Helper.encodeBasicHeader("test_user", "spock")))
                        .execute()
                        .returnResponse()
                        .getStatusLine()
                        .getStatusCode();

        assertThat("In case successfully authentication rest status code should be ok(200)",
                statusCode, equalTo(HttpStatus.SC_OK)
        );
    }

    @Test
    public void passwordHashCodeVulnerability() throws Exception {
        loadConfiguration("authentication/basic/minimal");

        String correctBasic = Base64Helper.encodeBasicHeader("example_user_with_pass_hashcode_vulnerability", "Wikohy8b");
        String hackerBasic = Base64Helper.encodeBasicHeader("example_user_with_pass_hashcode_vulnerability", "aaaqscnch");

        int statusCode =
                Request.Get(httpDomain + "/_search")
                        .addHeader(new BasicHeader("Authorization", "Basic " + correctBasic))
                        .execute()
                        .returnResponse()
                        .getStatusLine()
                        .getStatusCode();

        assumeThat(statusCode, equalTo(HttpStatus.SC_OK));

        int hackerStatus =
                Request.Get(httpDomain + "/_search")
                        .addHeader(new BasicHeader("Authorization", "Basic " + hackerBasic))
                        .execute()
                        .returnResponse()
                        .getStatusLine()
                        .getStatusCode();

        assertThat("Authentication can be cached on backend logic. If as part of cache key use password hashCode, hacker can easy " +
                "brute force password because java hashcode cryptographic strength 2^32. " +
                "It means that even if user password hashed with 64-bit(2^64) key " +
                "in configuration hacker still can hack it by use password hashcode vulnerability",
                hackerStatus, equalTo(HttpStatus.SC_UNAUTHORIZED)
        );
    }

    public void loadConfiguration(String configFolder) throws Exception {
        File dir = RESOURCES.getAbsoluteFilePathFromClassPath(configFolder);

        if (dir == null || !dir.isDirectory()) {
            throw new IllegalArgumentException("Configuration for search guard can't be load from directory: " + configFolder);
        }

        Client client = getAdminClient();

        IndicesExistsResponse existsResponse =
                client.admin()
                        .indices()
                        .prepareExists(IndexBaseConfigurationRepository.CONFIGURATION_INDEX)
                        .get();

        if (!existsResponse.isExists()) {
            client.admin()
                    .indices()
                    .prepareCreate(IndexBaseConfigurationRepository.CONFIGURATION_INDEX)
                    .get();
        }

        List<String> updatedConfigs = Lists.newArrayList();

        File[] childFiles = dir.listFiles();

        if (childFiles == null || childFiles.length == 0) {
            return;
        }

        for (File cfgFile : childFiles) {
            String fileName = cfgFile.getName();
            String cfgName = fileName.substring(0, fileName.lastIndexOf("."));

            client.prepareIndex()
                    .setIndex(IndexBaseConfigurationRepository.CONFIGURATION_INDEX)
                    .setType(cfgName)
                    .setId("0")
                    .setRefresh(true)
                    .setSource(RESOURCES.readXContent(new FileReader(cfgFile), XContentType.YAML))
                    .get();

            updatedConfigs.add(cfgName);
        }

        ConfigUpdateResponse response =
                client.execute(
                        ConfigUpdateAction.INSTANCE,
                        new ConfigUpdateRequest(updatedConfigs.toArray(new String[updatedConfigs.size()])))
                        .get();
    }

    public Client getAdminClient() {
        Settings tcSettings = Settings.builder()
                .put("cluster.name", EmbeddedServer.DEFAULT_CLUSTER_NAME)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, true)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, RESOURCES.getAbsoluteFilePathFromClassPath("truststore.jks"))
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION_RESOLVE_HOST_NAME, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, RESOURCES.getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk")
                .put("path.home", ".")
                .build();

        TransportClient client =
                TransportClient.builder()
                        .settings(tcSettings)
                        .addPlugin(SearchGuardSSLPlugin.class)
                        .addPlugin(SearchGuardPlugin.class)
                        .build();

        List<TransportAddress> addresses = server.getNodeAddresses();

        client.addTransportAddresses(addresses.toArray(new TransportAddress[0]));

        return client;
    }
}
