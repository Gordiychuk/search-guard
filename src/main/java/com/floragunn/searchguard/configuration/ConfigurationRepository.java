package com.floragunn.searchguard.configuration;


import org.elasticsearch.common.settings.Settings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * Abstraction layer over search guard configuration repository
 */
public interface ConfigurationRepository {

    /**
     * Load configuration from persistence layer
     *
     * @param configurationType not null configuration identifier
     * @return configuration found by specified type in persistence layer or {@code null} if persistence layer
     * doesn't have configuration by requested type, or persistence layer not ready yet
     * @throws NullPointerException if specified configuration type is null or empty
     */
    @Nullable
    Settings getConfiguration(@Nonnull String configurationType);

    /**
     * Bulk load configuration from persistence layer
     *
     * @param configTypes not null collection with not null configuration identifiers by that need load configurations
     * @return not null map where key it configuration type for found configuration and value it not null {@link Settings}
     * that represent configuration for correspond type. If by requested type configuration absent in persistence layer,
     * they will be absent in result map
     * @throws NullPointerException if specified collection with type null or contain null or empty types
     */
    @Nonnull
    Map<String, Settings> getConfiguration(@Nonnull Collection<String> configTypes);

    /**
     * Bulk reload configuration from persistence layer. If configuration was modify manually bypassing business logic define
     * in {@link ConfigurationRepository}, this method should catch up it logic. This method can be very slow, because it skip
     * all caching logic and should be use only as a last resort.
     *
     * @param configTypes not null collection with not null configuration identifiers by that need load configurations
     * @return not null map where key it configuration type for found configuration and value it not null {@link Settings}
     * that represent configuration for correspond type. If by requested type configuration absent in persistence layer,
     * they will be absent in result map
     * @throws NullPointerException if specified collection with type null or contain null or empty types
     */
    @Nonnull
    Map<String, Settings> reloadConfiguration(@Nonnull Collection<String> configTypes);

    /**
     * Save changed configuration in persistence layer. After save, changes will be available for
     * read via {@link ConfigurationRepository#getConfiguration(String)}
     *
     * @param configurationType not null configuration identifier
     * @param settings          not null configuration that need persist
     * @throws NullPointerException if specified configuration is null or configuration type is null or empty
     */
    void persistConfiguration(@Nonnull String configurationType, @Nonnull Settings settings);

    /**
     * Subscribe on configuration change
     *
     * @param configurationType not null and not empty configuration type of which changes need notify listener
     * @param listener          not null callback function that will be execute when specified type will modify
     * @throws NullPointerException if specified configuration type is null or empty, or callback function is null
     */
    void subscribeOnChange(@Nonnull String configurationType, @Nonnull ConfigurationChangeListener listener);
}
