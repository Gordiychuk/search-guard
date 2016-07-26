package com.floragunn.searchguard.authentication;


import com.floragunn.searchguard.action.configupdate.TransportConfigUpdateAction;
import com.floragunn.searchguard.auth.internal.InternalAuthenticationBackend;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

public class InternalAuthenticationBackendFactory implements AuthenticationBackendFactory<InternalAuthenticationBackend> {
    private final TransportConfigUpdateAction transportConfig;

    @Inject
    public InternalAuthenticationBackendFactory(TransportConfigUpdateAction transportConfig) {
        this.transportConfig = transportConfig;
    }

    @Override
    public InternalAuthenticationBackend create(Settings settings) {
        InternalAuthenticationBackend backend = new InternalAuthenticationBackend();
        //todo get actual config instead?
        transportConfig.addConfigChangeListener(InternalAuthenticationBackend.CONFIG_NAME, backend);
        return backend;
    }
}
