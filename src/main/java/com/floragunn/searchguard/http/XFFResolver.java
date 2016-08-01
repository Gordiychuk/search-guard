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

package com.floragunn.searchguard.http;

import java.net.InetSocketAddress;

import com.floragunn.searchguard.configuration.ConfigurationChangeListener;
import com.floragunn.searchguard.configuration.ConfigurationRepository;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.http.netty.NettyHttpRequest;
import org.elasticsearch.rest.RestRequest;

import com.floragunn.searchguard.support.ConfigConstants;

import javax.annotation.Nonnull;

public class XFFResolver implements ConfigurationChangeListener {
    public static final String CONFIGURATION_NAME = "config";

    protected final ESLogger log = Loggers.getLogger(this.getClass());
    private volatile boolean enabled = false;
    private volatile RemoteIpDetector detector;
    
    public XFFResolver() {
        super();
    }

    public TransportAddress resolve(final RestRequest request) throws ElasticsearchSecurityException {
        
        if(log.isTraceEnabled()) {
            log.trace("resolve {}", request.getRemoteAddress());
        }
        
        if(enabled && request.getRemoteAddress() instanceof InetSocketAddress && request instanceof NettyHttpRequest) {
            
            InetSocketAddress isa = new InetSocketAddress(detector.detect((NettyHttpRequest) request), ((InetSocketAddress)request.getRemoteAddress()).getPort());
        
            if(isa.isUnresolved()) {           
                throw new ElasticsearchSecurityException("Cannot resolve address "+isa.getHostString());
            }
                
             
            if(log.isTraceEnabled()) {
                if(request.getFromContext(ConfigConstants.SG_XFF_DONE) == Boolean.TRUE) {
                    log.trace("xff resolved {} to {}", request.getRemoteAddress(), isa);
                } else {
                    log.trace("no xff done for {}",request.getClass());
                }
            }
            return new InetSocketTransportAddress(isa);
        } else if(request.getRemoteAddress() instanceof InetSocketAddress){
            
            if(log.isTraceEnabled()) {
                log.trace("no xff done (not initialized, enabled or no netty request) {},{},{}", enabled, request.getClass());

            }
            return new InetSocketTransportAddress((InetSocketAddress)request.getRemoteAddress());
        } else {
            throw new ElasticsearchSecurityException("Cannot handle this request. Remote address is "+request.getRemoteAddress()+" with request class "+request.getClass());
        }
    }

    @Override
    public void onChange(@Nonnull Settings settings) {
        enabled = settings.getAsBoolean("searchguard.dynamic.http.xff.enabled", true);
        if(enabled) {
            detector = new RemoteIpDetector();
            detector.setInternalProxies(settings.get("searchguard.dynamic.http.xff.internalProxies", detector.getInternalProxies()));
            detector.setProxiesHeader(settings.get("searchguard.dynamic.http.xff.proxiesHeader", detector.getProxiesHeader()));
            detector.setRemoteIpHeader(settings.get("searchguard.dynamic.http.xff.proxiesHeader.remoteIpHeader", detector.getRemoteIpHeader()));
            detector.setTrustedProxies(settings.get("searchguard.dynamic.http.xff.proxiesHeader.trustedProxies", detector.getTrustedProxies()));
            
        } else {
            detector = null;
        }
        
    }
}




