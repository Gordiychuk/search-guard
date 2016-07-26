package com.floragunn.searchguard.authentication.http;

import com.floragunn.searchguard.auth.HTTPAuthenticator;
import org.elasticsearch.common.settings.Settings;

public class NoConfigurableAuthenticatorFactory<T extends HTTPAuthenticator> implements HttpAuthenticatorFactory<T> {
    private final Class<T> clazz;

    public NoConfigurableAuthenticatorFactory(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public T create(Settings settings) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Not available create authentificator instance of class " + clazz);
        }
    }
}
