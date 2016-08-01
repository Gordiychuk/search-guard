package com.floragunn.searchguard.configuration;


import org.elasticsearch.common.settings.Settings;

import javax.annotation.Nonnull;

/**
 * Callback function on change particular configuration
 */
public interface ConfigurationChangeListener {

    /**
     * @param configuration not null updated configuration on that was subscribe current listener
     */
    void onChange(@Nonnull Settings configuration);
}
