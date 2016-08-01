package com.floragunn.searchguard.utils;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.PluginAwareNode;


import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EmbeddedServer {
    public static final String DEFAULT_CLUSTER_NAME = "searchguard_ssl_testcluster";
    private static final ESLogger LOGGER = Loggers.getLogger(EmbeddedServer.class);

    private final Settings nodeSettings;
    private List<Node> nodes = Collections.emptyList();

    public EmbeddedServer(Settings nodeSettings) {
        this.nodeSettings = nodeSettings == null ? Settings.EMPTY : nodeSettings;
    }

    public void start(int countNode) {

        if (countNode <= 0) {
            throw new IllegalArgumentException("Available only positive value for count node parameter, but was " + countNode);
        }
        try {
            FileUtils.deleteDirectory(new File("data"));
        } catch (IOException e) {
            Throwables.propagate(e);
        }

        List<Node> nodes = Lists.newArrayList();

        boolean masterInitialized = false;
        for (int index = 0; index < countNode; index++) {
            Settings esSettings;
            if (!masterInitialized) {
                esSettings =
                        getDefaultSettingsBuilder(index, true, true)
                                .put(nodeSettings)
                                .build();
                masterInitialized = true;
            } else {
                esSettings =
                        getDefaultSettingsBuilder(index, true, false)
                                .put(nodeSettings)
                                .build();
            }

            Node node = new PluginAwareNode(esSettings, SearchGuardSSLPlugin.class, SearchGuardPlugin.class);
            node.start();
            nodes.add(node);
        }

        this.nodes = nodes;

        waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(30L));
    }

    public void stopNode(int index) {
        this.nodes.get(index).close();
    }

    public void startNode(int index) {
        Settings esSettings =
                getDefaultSettingsBuilder(index, true, false)
                        .put(nodeSettings)
                        .build();

        Node node = new PluginAwareNode(esSettings, SearchGuardSSLPlugin.class, SearchGuardPlugin.class);
        node.start();

        this.nodes.add(index, node);
    }

    public void stop() {
        for(Node node : nodes) {
            node.close();
        }

        nodes.clear();
    }

    public Client getNodeClient() {
        Node node = Objects.requireNonNull(getFirstNotStopped());
        return node.client();
    }

    private Node getFirstNotStopped() {
        for(Node node : nodes) {
            if(!node.isClosed()) {
                return node;
            }
        }

        return null;
    }

    public void waitForCluster(final ClusterHealthStatus status, final TimeValue timeout) {
        Client client = getNodeClient();

            LOGGER.debug("waiting for cluster state {}", status.name());
            final ClusterHealthResponse healthResponse =
                    client.admin()
                            .cluster()
                            .prepareHealth()
                            .setWaitForStatus(status)
                            .setTimeout(timeout)
                            .setWaitForNodes(String.valueOf(nodes.size()))
                            .get();

            if (healthResponse.isTimedOut()) {
                throw new ElasticsearchTimeoutException("cluster state is " + healthResponse.getStatus().name() + " with " + healthResponse.getNumberOfNodes() + " nodes");
            } else {
                LOGGER.debug("... cluster state ok " + healthResponse.getStatus().name() + " with " + healthResponse.getNumberOfNodes()
                        + " nodes");
            }
    }

    public List<TransportAddress> getNodeAddresses() {
        Client client = getNodeClient();
        List<TransportAddress> result = Lists.newArrayList();

        final NodesInfoResponse res =
                client.admin()
                        .cluster()
                        .nodesInfo(new NodesInfoRequest())
                        .actionGet();

        for (NodeInfo nodeInfo : res.getNodes()) {
            TransportAddress address = nodeInfo.getTransport().getAddress().publishAddress();
            result.add(address);
        }

        return result;
    }

    public Set<InetSocketTransportAddress> getHttpAddresses() {
        Client client = getNodeClient();
        Set<InetSocketTransportAddress> result = Sets.newHashSet();

        final NodesInfoResponse res =
                client.admin()
                        .cluster()
                        .nodesInfo(new NodesInfoRequest())
                        .actionGet();

        for (NodeInfo nodeInfo : res.getNodes()) {
            if (nodeInfo.getHttp() != null && nodeInfo.getHttp().address() != null) {
                final InetSocketTransportAddress is = (InetSocketTransportAddress) nodeInfo.getHttp().address().publishAddress();
                result.add(is);
            }
        }

        return result;
    }

    private Settings.Builder getDefaultSettingsBuilder(final int nodenum, final boolean dataNode, final boolean masterNode) {
        return Settings.settingsBuilder()
                .put("node.name", "searchguard_testnode_" + nodenum)
                .put("node.data", dataNode)
                .put("node.master", masterNode)
                .put("cluster.name", DEFAULT_CLUSTER_NAME)
                .put("path.data", "data/data")
                .put("path.work", "data/work")
                .put("path.logs", "data/logs")
                .put("path.conf", "data/config")
                .put("path.plugins", "data/plugins")
                .put("index.number_of_shards", "1")
                .put("index.number_of_replicas", "0")
                .put("http.enabled", true)
                .put("cluster.routing.allocation.disk.watermark.high", "1mb")
                .put("cluster.routing.allocation.disk.watermark.low", "1mb")
                .put("http.cors.enabled", true)
                .put("node.local", false)
                .put("path.home", ".");
    }


}
