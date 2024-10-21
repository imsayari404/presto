package com.facebook.presto.jwt.Default;
import com.facebook.presto.jwt.Default.DefaultJWTAuthenticator;
import com.facebook.presto.jwt.Default.DefaultJWTAuthenticatorFactory;
import com.facebook.presto.spi.Plugin;
import com.facebook.presto.spi.security.JWTAuthenticatorFactory;
import com.facebook.presto.spi.security.PasswordAuthenticatorFactory;
import com.google.common.collect.ImmutableList;

public class JWTAuthenticatorPlugin implements Plugin {


    @Override
    public Iterable<JWTAuthenticatorFactory> getJWTAuthenticatorFactories()
    {
        return ImmutableList.<JWTAuthenticatorFactory>builder()
                .add(new DefaultJWTAuthenticatorFactory())
                .build();
    }
}

