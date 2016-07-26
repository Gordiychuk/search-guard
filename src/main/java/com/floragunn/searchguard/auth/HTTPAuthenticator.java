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

package com.floragunn.searchguard.auth;

import com.floragunn.searchguard.user.AuthCredentials;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;

public interface HTTPAuthenticator {

    /**
     * Authenticator type can be use as soft reference reference to authenticator in configuration
     *
     * @return not null unique name between whole authenticators
     */
    String getType();

    /**
     * @param request not null client request
     * @return The authenticated user, null means another roundtrip
     * @throws ElasticsearchSecurityException
     */
    AuthCredentials extractCredentials(RestRequest request) throws ElasticsearchSecurityException;

    /**
     * @param channel not null client request
     * @param credentials nullable creadentials that was extract previously
     * @return {@code true} if authenticator support repeat authentication, otherwise return {@code false}
     */
    boolean reRequestAuthentication(final RestChannel channel, AuthCredentials credentials);
}
