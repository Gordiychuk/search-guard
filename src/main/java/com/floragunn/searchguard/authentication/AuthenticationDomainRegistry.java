package com.floragunn.searchguard.authentication;

import com.floragunn.searchguard.auth.AuthDomain;

import java.util.SortedSet;

public interface AuthenticationDomainRegistry {
    /**
     * @return not null immutable threadsafe sorted set active authentication domains, or empty if authentication not active
     */
    SortedSet<AuthDomain> getActiveDomains();

    /**
     * @return {@code true} if available work with elasticsearch without authentication otherwise return {@code false}
     */
    boolean isAnonymousEnable();
}
