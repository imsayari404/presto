package com.facebook.presto.spi.security;

import java.security.Principal;

public class JWTAuthenticator {
    /**
     * Authenticate the provided token.
     *
     * @return the authenticated principal
     * @throws AccessDeniedException if not allowed
     */
    Principal createAuthenticatedPrincipal(String token);
}
