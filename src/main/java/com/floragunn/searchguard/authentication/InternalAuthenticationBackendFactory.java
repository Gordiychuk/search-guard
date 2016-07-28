package com.floragunn.searchguard.authentication;


import com.floragunn.searchguard.auth.internal.InternalAuthenticationBackend;
import com.floragunn.searchguard.configuration.ConfigurationRepository;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

public class InternalAuthenticationBackendFactory implements AuthenticationBackendFactory<InternalAuthenticationBackend> {
    private final ConfigurationRepository configurationRepository;

    @Inject
    public InternalAuthenticationBackendFactory(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    @Override
    public InternalAuthenticationBackend create(Settings settings) {
        return new InternalAuthenticationBackend(configurationRepository);
    }
}
