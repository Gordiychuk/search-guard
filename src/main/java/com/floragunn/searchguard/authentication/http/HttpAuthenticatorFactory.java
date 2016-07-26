package com.floragunn.searchguard.authentication.http;

import com.floragunn.searchguard.auth.HTTPAuthenticator;
import org.elasticsearch.common.settings.Settings;

public interface HttpAuthenticatorFactory<T extends HTTPAuthenticator> {
    /**
     * Factory method for instantiate authenticator describe in config file.
     *
     * @param settings not null settings specific for particular authenticator, it extension point can be ignore
     *                 by not configurable authenticator
     * @return new instance authenticator configured by specified settings
     */
    T create(Settings settings);
}
