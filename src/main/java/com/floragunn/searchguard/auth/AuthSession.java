package com.floragunn.searchguard.auth;


import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

public final class AuthSession {
    @Nonnull
    private final String username;
    @Nonnull
    private final UUID sessionToken;

    public AuthSession(String username, UUID sessionToken) {
        this.sessionToken = sessionToken;
        this.username = username;
    }

    @Nonnull
    public UUID getSessionToken() {
        return sessionToken;
    }

    @Nonnull
    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthSession that = (AuthSession) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(sessionToken, that.sessionToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, sessionToken);
    }
}