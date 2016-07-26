package com.floragunn.searchguard.authentication;


import com.floragunn.searchguard.auth.AuthenticationBackend;
import org.elasticsearch.common.settings.Settings;

public interface AuthenticationBackendFactory<T extends AuthenticationBackend> {

    /**
     * Factory method for instantiate authenticator backend describe in config file.
     *
     * @param settings not null settings specific for particular authenticator backend, it extension point can be ignore
     *                 by not configurable backend
     * @return new instance authenticator backend configured by specified settings
     */
    T create(Settings settings);
}
