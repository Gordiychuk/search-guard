package com.floragunn.searchguard.auth;

import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;


public class AuthSessionTest {

    @Test
    public void sessionCanNotBeEqualWithDifferentToken() throws Exception {
        final String userName = "myUser";
        AuthSession firstSession = new AuthSession(userName, UUID.randomUUID());
        AuthSession secondSession = new AuthSession(userName, UUID.randomUUID());


        assertThat(firstSession, not(equalTo(secondSession)));
    }

    @Test
    public void sessionCanNotBeEqualWithSameTokenAndDifferentUsername() throws Exception {
        UUID token = UUID.randomUUID();
        AuthSession firstSession = new AuthSession("firstUser", token);
        AuthSession secondSession = new AuthSession("secondUser", token);

        assertThat(firstSession, not(equalTo(secondSession)));
    }

    @Test
    public void sessionWithSameUserAndTokenEqual() throws Exception {
        UUID token = UUID.randomUUID();
        String userName = "myUser";

        AuthSession firstSession = new AuthSession(userName, token);
        AuthSession secondSession = new AuthSession(userName, token);

        assertThat(firstSession, equalTo(secondSession));
    }

    @Test
    public void hashCodeEqualForEqualObjects() throws Exception {
        UUID token = UUID.randomUUID();
        String userName = "myUser";

        AuthSession firstSession = new AuthSession(userName, token);
        AuthSession secondSession = new AuthSession(userName, token);

        assertThat(firstSession.hashCode(), equalTo(secondSession.hashCode()));
    }

    @Test
    public void hashcodeForDifferentTokenNotEqual() throws Exception {
        String userName = "myUser";

        AuthSession firstSession = new AuthSession(userName, UUID.randomUUID());
        AuthSession secondSession = new AuthSession(userName, UUID.randomUUID());

        assertThat(firstSession.hashCode(), not(equalTo(secondSession.hashCode())));
    }
}