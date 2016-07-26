package com.floragunn.searchguard.authentication.http;


import com.floragunn.searchguard.http.HTTPProxyAuthenticator;
import org.elasticsearch.common.settings.Settings;

public class HttpProxyAuthenticatorFactory implements HttpAuthenticatorFactory<HTTPProxyAuthenticator> {
    @Override
    public HTTPProxyAuthenticator create(Settings settings) {
        return new HTTPProxyAuthenticator(settings);
    }
}
