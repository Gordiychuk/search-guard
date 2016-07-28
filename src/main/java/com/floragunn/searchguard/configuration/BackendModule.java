/*
 * Copyright 2015 floragunn UG (haftungsbeschränkt)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.floragunn.searchguard.configuration;

import com.floragunn.searchguard.action.configupdate.TransportConfigUpdateAction;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.auth.BackendRegistry;
import com.floragunn.searchguard.auth.internal.InternalAuthenticationBackend;
import com.floragunn.searchguard.auth.internal.NoOpAuthenticationBackend;
import com.floragunn.searchguard.authentication.*;
import com.floragunn.searchguard.authentication.http.HttpAuthenticatorFactory;
import com.floragunn.searchguard.authentication.http.HttpProxyAuthenticatorFactory;
import com.floragunn.searchguard.authentication.http.NoConfigurableAuthenticatorFactory;
import com.floragunn.searchguard.filter.AuthenticationRestFilter;
import com.floragunn.searchguard.http.HTTPBasicAuthenticator;
import com.floragunn.searchguard.http.HTTPClientCertAuthenticator;
import com.floragunn.searchguard.http.HTTPHostAuthenticator;
import com.floragunn.searchguard.http.HTTPProxyAuthenticator;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.inject.Provides;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.inject.multibindings.MapBinder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.threadpool.ThreadPool;

import javax.annotation.Nonnull;
import java.util.Map;

public class BackendModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(BackendRegistry.class).asEagerSingleton();

        configureAuthenticators();
        configureAuthenticationBackends();
    }

    private void configureAuthenticators() {
        MapBinder<String, HttpAuthenticatorFactory> binder
                = MapBinder.newMapBinder(binder(), String.class, HttpAuthenticatorFactory.class);

        binder.addBinding(HTTPBasicAuthenticator.TYPE)
                .toInstance(new NoConfigurableAuthenticatorFactory<>(HTTPBasicAuthenticator.class));

        binder.addBinding(HTTPClientCertAuthenticator.TYPE)
                .toInstance(new NoConfigurableAuthenticatorFactory<>(HTTPClientCertAuthenticator.class));

        binder.addBinding(HTTPHostAuthenticator.TYPE)
                .toInstance(new NoConfigurableAuthenticatorFactory<>(HTTPHostAuthenticator.class));

        binder.addBinding(HTTPProxyAuthenticator.TYPE)
                .to(HttpProxyAuthenticatorFactory.class)
                .asEagerSingleton();
    }

    private void configureAuthenticationBackends() {
        MapBinder<String, AuthenticationBackendFactory> binder =
                MapBinder.newMapBinder(binder(), String.class, AuthenticationBackendFactory.class);

        binder.addBinding(NoOpAuthenticationBackend.TYPE)
                .to(NoOpAuthenticationBackendFactory.class)
                .asEagerSingleton();

        binder.addBinding(InternalAuthenticationBackend.TYPE)
                .to(InternalAuthenticationBackendFactory.class)
                .asEagerSingleton();

        binder.addBinding(InternalAuthenticationBackend.OLD_TYPE)
                .to(InternalAuthenticationBackendFactory.class)
                .asEagerSingleton();
    }

    @Provides
    @Singleton
    public AuthenticationRestFilter authenticationRestFilter(RestController controller,
                                                             BackendRegistry registry,
                                                             AuditLog auditLog) {
        AuthenticationRestFilter filter = new AuthenticationRestFilter(registry, auditLog);
        controller.registerFilter(filter);
        return filter;
    }

    @Provides
    @Singleton
    public AuthenticationDomainRegistry authenticationDomainRegistry(
            Settings esSettings,
            TransportConfigUpdateAction configAction,
            ConfigurationRepository configurationRepository,
            Map<String, HttpAuthenticatorFactory> typeToAuthenticator,
            Map<String, AuthenticationBackendFactory> typeToBackend
    ) {
        ConfigBaseAuthenticationDomainRegistry registry =
                new ConfigBaseAuthenticationDomainRegistry(esSettings, configAction, typeToAuthenticator, typeToBackend);
        configurationRepository.subscribeOnChange(ConfigBaseAuthenticationDomainRegistry.CONFIG_NAME, registry);
        return registry;
    }

    @Provides
    @Singleton
    public ConfigurationRepository configurationRepository(
            @Nonnull ThreadPool threadPool,
            @Nonnull Provider<Client> clientProvider,
            @Nonnull ClusterService clusterService
    ) {
        return IndexBaseConfigurationRepository.create(threadPool, clientProvider, clusterService);
    }
}
