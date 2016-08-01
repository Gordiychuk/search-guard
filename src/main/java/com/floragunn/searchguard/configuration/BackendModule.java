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
import com.floragunn.searchguard.auth.internal.InternalAuthenticationBackend;
import com.floragunn.searchguard.filter.SearchGuardRestFilter;
import com.floragunn.searchguard.http.XFFResolver;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.AbstractModule;

import com.floragunn.searchguard.auth.BackendRegistry;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.inject.Provides;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.threadpool.ThreadPool;

import javax.annotation.Nonnull;

public class BackendModule extends AbstractModule {

    @Override
    protected void configure() {

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

    @Provides
    @Singleton
    public XFFResolver xffResolver(ConfigurationRepository repository) {
        XFFResolver xffResolver = new XFFResolver();
        repository.subscribeOnChange(XFFResolver.CONFIGURATION_NAME, xffResolver);
        return xffResolver;
    }

    @Provides
    @Singleton
    public BackendRegistry backendRegistry(
            Settings settings,
            TransportConfigUpdateAction tcua,
            AdminDNs adminDns,
            XFFResolver xffResolver,
            InternalAuthenticationBackend iab,
            AuditLog auditLog,
            ConfigurationRepository configurationRepository,
            RestController controller
    ) {
        BackendRegistry backendRegistry = new BackendRegistry(settings, tcua, adminDns, xffResolver, iab, auditLog);

        configurationRepository.subscribeOnChange(BackendRegistry.CONFIGURATION_NAME, backendRegistry);
        controller.registerFilter(new SearchGuardRestFilter(backendRegistry, auditLog));
        return backendRegistry;
    }
}
