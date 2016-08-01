package com.floragunn.searchguard.rest;


import com.google.common.collect.Maps;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestResponse;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Extend default rest channel with additional abilities
 */
public class ExtendedRestChannel extends RestChannelDelegator {

    private Map<String, String> responseHeaders;

    public ExtendedRestChannel(RestChannel original) {
        super(original);
    }

    public void addHeaderToResponse(@Nonnull String header, @Nonnull String value) {
        if (responseHeaders == null) {
            responseHeaders = Maps.newHashMap();
        }

        responseHeaders.put(header, value);
    }

    @Override
    public void sendResponse(RestResponse response) {
        injectHeaders(response);

        super.sendResponse(response);
    }

    private void injectHeaders(RestResponse response) {
        if (responseHeaders != null) {
            for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
                response.addHeader(entry.getKey(), entry.getValue());
            }
        }
    }
}
