package com.floragunn.searchguard.authentication;


import com.floragunn.searchguard.action.configupdate.TransportConfigUpdateAction;
import com.floragunn.searchguard.auth.AuthDomain;
import com.floragunn.searchguard.auth.AuthenticationBackend;
import com.floragunn.searchguard.auth.HTTPAuthenticator;
import com.floragunn.searchguard.auth.internal.InternalAuthenticationBackend;
import com.floragunn.searchguard.authentication.http.HttpAuthenticatorFactory;
import com.floragunn.searchguard.configuration.ConfigChangeListener;
import com.floragunn.searchguard.http.HTTPBasicAuthenticator;
import com.google.common.collect.ImmutableSortedSet;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class ConfigBaseAuthenticationDomainRegistry implements AuthenticationDomainRegistry, ConfigChangeListener {
    public static final String CONFIG_NAME = "config";
    private static final ESLogger LOGGER = Loggers.getLogger(ConfigBaseAuthenticationDomainRegistry.class);

    private final Settings esSettings;
    private final TransportConfigUpdateAction configAction;
    private final Map<String, HttpAuthenticatorFactory> typeToAuthenticator;
    private final Map<String, AuthenticationBackendFactory> typeToBackend;

    private volatile boolean initialized;
    private volatile boolean anonymousAuthEnabled = false;
    private volatile SortedSet<AuthDomain> activeDomains = ImmutableSortedSet.of();

    /**
     * @param esSettings          not null global elasticsearch settings
     * @param configAction        temp dependency for backward compatibility for subscribe on config changes
     * @param typeToAuthenticator not null available authenticators
     * @param typeToBackend       not null available authenticator backends
     */
    @Inject
    public ConfigBaseAuthenticationDomainRegistry(
            Settings esSettings,
            TransportConfigUpdateAction configAction,
            Map<String, HttpAuthenticatorFactory> typeToAuthenticator,
            Map<String, AuthenticationBackendFactory> typeToBackend
    ) {
        this.esSettings = esSettings;
        this.configAction = configAction;
        this.typeToAuthenticator = typeToAuthenticator;
        //todo subscribe
        this.typeToBackend = typeToBackend;
    }

    @Override
    public SortedSet<AuthDomain> getActiveDomains() {
        return activeDomains;
    }

    @Override
    public boolean isAnonymousEnable() {
        return anonymousAuthEnabled;
    }

    @Override
    public void onChange(final String event, final Settings settings) {
        if(LOGGER.isDebugEnabled()) {
            //todo print node name or use MDC
            LOGGER.debug("Was changed authentication domain, new configuration:\n{}", settings.toDelimitedString('\n'));
        }
        anonymousAuthEnabled = settings.getAsBoolean("searchguard.dynamic.http.anonymous_auth_enabled", false);

        SortedSet<AuthDomain> domains = new TreeSet<>();

        final Map<String, Settings> dyn = settings.getGroups("searchguard.dynamic.authc");

        for (final String ad : dyn.keySet()) {
            final Settings domainSetting = dyn.get(ad);

            if (!domainSetting.getAsBoolean("enabled", true)) {
                continue;
            }

            try {
                AuthenticationBackend backend = backendInstance(domainSetting);
                HTTPAuthenticator authenticator = authenticatorInstance(domainSetting);

                boolean challenge = domainSetting.getAsBoolean("http_authenticator.challenge", true);
                int order = domainSetting.getAsInt("order", 0);

                AuthDomain domain = new AuthDomain(backend, authenticator, challenge, order);
                domains.add(domain);
            } catch (Exception e) {
                //todo maybe more convenient wait it fail server than work with basic authentication?
                LOGGER.error("Unable to initialize auth domain {} due to {}", e, ad, e.toString());
            }
        }

        if (domains.isEmpty()) {
            AuthDomain defaultDomain = defaultDomain();
            if (defaultDomain == null) {
                throw new IllegalStateException("Not configure default authentication domain");
            }

            domains.add(defaultDomain);
        }

        activeDomains = ImmutableSortedSet.copyOfSorted(domains);
        initialized = true;
    }

    private AuthDomain defaultDomain() {
        HTTPAuthenticator authenticator = defaultAuthenticatorInstance();
        AuthenticationBackend backend = defaultBackendInstance();

        if (authenticator == null || backend == null) {
            return null;
        }

        return new AuthDomain(backend, authenticator, true, 0);
    }

    private AuthenticationBackend defaultBackendInstance() {
        AuthenticationBackendFactory backendFactory = typeToBackend.get(InternalAuthenticationBackend.TYPE);

        if (backendFactory != null) {
            return backendFactory.create(Settings.EMPTY);
        }

        return null;
    }

    private HTTPAuthenticator defaultAuthenticatorInstance() {
        HttpAuthenticatorFactory authenticatorFactory = typeToAuthenticator.get(HTTPBasicAuthenticator.TYPE);

        if (authenticatorFactory != null) {
            return authenticatorFactory.create(Settings.EMPTY);
        }

        return null;
    }

    private AuthenticationBackend backendInstance(Settings domainSetting) throws ReflectiveOperationException {
        String backendType = domainSetting.get("authentication_backend.type", "internal");

        Settings backendSettings =
                Settings.builder()
                        .put(esSettings) //global elasticsearch settings
                        .put(domainSetting.getAsSettings("authentication_backend.config"))
                        .build();

        AuthenticationBackendFactory backendFactory = typeToBackend.get(backendType);

        if (backendFactory != null) {
            return backendFactory.create(backendSettings);
        }

        return newInstance(backendType, backendSettings);
    }

    private HTTPAuthenticator authenticatorInstance(Settings domainSetting) throws ReflectiveOperationException {
        String authenticatorType = domainSetting.get("http_authenticator.type", "basic");

        Settings authenticatorSettings =
                Settings.builder()
                        .put(esSettings) //global elasticsearch settings
                        .put(domainSetting.getAsSettings("http_authenticator.config"))
                        .build();

        HttpAuthenticatorFactory authenticatorFactory = typeToAuthenticator.get(authenticatorType);

        if (authenticatorFactory != null) {
            return authenticatorFactory.create(authenticatorSettings);
        }

        return newInstance(authenticatorType, domainSetting);
    }

    private <T> T newInstance(final String clazz, final Settings settings)
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final Class<T> t = (Class<T>) Class.forName(clazz);

        try {
            final Constructor<T> tctor = t.getConstructor(Settings.class);
            return tctor.newInstance(settings);
        } catch (final Exception e) {
            LOGGER.warn("Unable to create instance of class {} with (Settings.class) constructor due to {}", e, t, e.toString());
            //todo replace on create via factory
            final Constructor<T> tctor = t.getConstructor(Settings.class, TransportConfigUpdateAction.class);
            return tctor.newInstance(settings, configAction);
        }
    }

    @Override
    public void validate(String event, Settings settings) throws ElasticsearchSecurityException {
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
