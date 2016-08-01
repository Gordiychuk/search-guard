package com.floragunn.searchguard.configuration;

import com.floragunn.searchguard.support.ConfigConstants;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.component.LifecycleListener;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.loader.JsonSettingsLoader;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.threadpool.ThreadPool;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class IndexBaseConfigurationRepository implements ConfigurationRepository {
    public static final String CONFIGURATION_INDEX = "searchguard";
    private static final ESLogger LOGGER = Loggers.getLogger(IndexBaseConfigurationRepository.class);

    @Nonnull
    private final Provider<Client> clientProvider;

    @Nonnull
    private final ClusterService clusterService;

    @Nonnull
    private final ConcurrentMap<String, Settings> typeToConfig;

    //todo unsubscribe?
    @GuardedBy("synchronized methods")
    private final Multimap<String, ConfigurationChangeListener> configTypeToChancheListener;

    private volatile boolean indexReady = false;

    private IndexBaseConfigurationRepository(@Nonnull Provider<Client> clientProvider, @Nonnull ClusterService clusterService) {
        this.clientProvider = clientProvider;
        this.typeToConfig = Maps.newConcurrentMap();
        this.configTypeToChancheListener = ArrayListMultimap.create();
        this.clusterService = clusterService;
    }

    @Nonnull
    public static ConfigurationRepository create(@Nonnull final ThreadPool threadPool, @Nonnull Provider<Client> clientProvider, @Nonnull ClusterService clusterService) {
        final IndexBaseConfigurationRepository repository = new IndexBaseConfigurationRepository(clientProvider, clusterService);
        clusterService.addLifecycleListener(new LifecycleListener() {
            @Override
            public void afterStart() {
                threadPool.executor(ThreadPool.Names.GET).execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                Set<String> topics = repository.getSubscribeTypes();
                                LOGGER.debug("Try reload configuration by topics {} after start server", topics);
                                Map<String, Settings> reloaded = repository.reloadConfiguration(topics);
                                LOGGER.debug("Loaded configuration at server start:\n{}",
                                        Joiner.on("\n").withKeyValueSeparator(":\n\n").join(reloaded)
                                );
                            }
                        }
                );
            }
        });

        return repository;
    }

    @Nullable
    @Override
    public Settings getConfiguration(@Nonnull String configurationType) {
        if (!ensureIndexReady()) {
            return null;
        }

        Settings result = typeToConfig.get(configurationType);

        if (result != null) {
            return result;
        }

        Map<String, Settings> loaded = loadConfigurations(Collections.singleton(configurationType));

        result = loaded.get(configurationType);

        return putSettingsToCache(configurationType, result);
    }

    private Settings putSettingsToCache(@Nonnull String configurationType, @Nullable Settings result) {
        if (result != null) {
            typeToConfig.putIfAbsent(configurationType, result);
        }

        return typeToConfig.get(configurationType);
    }

    @Nonnull
    @Override
    public Map<String, Settings> getConfiguration(@Nonnull Collection<String> configTypes) {
        if (!ensureIndexReady() && !configTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> typesToLoad = Lists.newArrayList();
        Map<String, Settings> result = Maps.newHashMap();

        for (String type : configTypes) {
            Settings conf = typeToConfig.get(type);
            if (conf != null) {
                result.put(type, conf);
            } else {
                typesToLoad.add(type);
            }
        }

        if (typesToLoad.isEmpty()) {
            return result;
        }

        Map<String, Settings> loaded = loadConfigurations(typesToLoad);

        for (Map.Entry<String, Settings> entry : loaded.entrySet()) {
            Settings conf = putSettingsToCache(entry.getKey(), entry.getValue());

            if (conf != null) {
                result.put(entry.getKey(), conf);
            }
        }

        return result;
    }

    @Nonnull
    @Override
    public Map<String, Settings> reloadConfiguration(@Nonnull Collection<String> configTypes) {
        if (!ensureIndexReady()) {
            return Collections.emptyMap();
        }

        Map<String, Settings> loaded = loadConfigurations(configTypes);

        typeToConfig.putAll(loaded);
        notifyAboutChanges(loaded);

        return loaded;
    }

    @Override
    public void persistConfiguration(@Nonnull String configurationType, @Nonnull Settings settings) {
        //todo should be use from com.floragunn.searchguard.tools.SearchGuardAdmin
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public synchronized void subscribeOnChange(@Nonnull String configurationType, @Nonnull ConfigurationChangeListener listener) {
        LOGGER.debug("Subscribe on configuration changes by type {} with listener {}", configurationType, listener);
        configTypeToChancheListener.put(configurationType, listener);
    }

    @Nonnull
    private synchronized Set<String> getSubscribeTypes() {
        return Sets.newHashSet(configTypeToChancheListener.keySet());
    }

    public synchronized void notifyAboutChanges(Map<String, Settings> typeToConfig) {
        for (Map.Entry<String, ConfigurationChangeListener> entry : configTypeToChancheListener.entries()) {
            String type = entry.getKey();
            ConfigurationChangeListener listener = entry.getValue();

            Settings settings = typeToConfig.get(type);

            if (settings == null) {
                continue;
            }

            LOGGER.debug("Notify {} listener about change configuration with type {}", listener, type);
            listener.onChange(settings);
        }
    }

    private boolean ensureIndexReady() {
        if (indexReady) {
            return true;
        }

        if (clusterService.lifecycleState() != Lifecycle.State.STARTED) {
            LOGGER.debug("SearchGuard configuration index {} can't be load because server not started yet", CONFIGURATION_INDEX);
            return false;
        }

        if (!ensureIndexExists()) {
            LOGGER.debug("SearchGuard configuration index {} not exists", CONFIGURATION_INDEX);
            return false;
        }

        boolean stateOk = ensureIndexStateYellow();

        if (stateOk) {
            indexReady = true;
        }

        return stateOk;
    }

    private boolean ensureIndexExists() {
        Client client = clientProvider.get();

        IndicesExistsResponse existsResponse =
                client.admin()
                        .indices()
                        .prepareExists(CONFIGURATION_INDEX)
                        .get();

        return existsResponse.isExists();
    }

    private boolean ensureIndexStateYellow() {
        Client client = clientProvider.get();

        ClusterHealthResponse response =
                client.admin()
                        .cluster()
                        .health(new ClusterHealthRequest(CONFIGURATION_INDEX)
                                .waitForYellowStatus())
                        .actionGet();

        if (response.isTimedOut() || response.getStatus() == ClusterHealthStatus.RED) {
            LOGGER.debug("SearchGuard configuration index {} not ready yet for query, status {}",
                    CONFIGURATION_INDEX, response.getStatus()
            );
            return false;
        }

        return true;
    }

    @Nonnull
    private Map<String, Settings> loadConfigurations(@Nonnull Collection<String> configTypes) {
        Client client = clientProvider.get();

        MultiGetRequestBuilder multiGet = client.prepareMultiGet();

        for (String type : configTypes) {
            multiGet.add(CONFIGURATION_INDEX, type, "0");
        }

        multiGet.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true"); //header needed here
        multiGet.setRefresh(true);
        multiGet.setRealtime(true);

        MultiGetResponse multiGetResponse = multiGet.get();

        Map<String, Settings> result = Maps.newHashMap();
        for (MultiGetItemResponse mResponse : multiGetResponse.getResponses()) {
            if (mResponse.isFailed()) {
                LOGGER.error("Fail get SearchGuard configuration with type {} because fail occurs {}",
                        mResponse.getType(),
                        mResponse.getFailure().getMessage(),
                        mResponse.getFailure().getFailure()
                );

                continue;
            }

            GetResponse getResponse = mResponse.getResponse();

            if (!getResponse.isExists()) {
                LOGGER.debug("SearchGuard configuration with type {} not exists", getResponse.getType());
            }

            if(getResponse.isSourceEmpty()) {

            }


            Settings settings;

            if (getResponse.isSourceEmpty() || getResponse.getSourceAsBytesRef().length() == 0) {
                settings = Settings.EMPTY;
            } else {
                try {
                    settings =
                            Settings.builder()
                                    .put(new JsonSettingsLoader().load(XContentHelper.createParser(getResponse.getSourceAsBytesRef()))).build();
                } catch (IOException e) {
                    LOGGER.error("Fail parse search guard configuration with type {}", getResponse.getType(), e);
                    throw ExceptionsHelper.convertToElastic(e);
                }

            }

            result.put(getResponse.getType(), settings);
        }

        return result;
    }
}
