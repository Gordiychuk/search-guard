package com.floragunn.searchguard.authentication;

import com.floragunn.searchguard.auth.internal.NoOpAuthenticationBackend;
import org.elasticsearch.common.settings.Settings;

public class NoOpAuthenticationBackendFactory implements AuthenticationBackendFactory<NoOpAuthenticationBackend> {
    @Override
    public NoOpAuthenticationBackend create(Settings settings) {
        return new NoOpAuthenticationBackend();
    }
}
