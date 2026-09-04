package com.auth.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secretKey,long expirationTimeMs,long refreshTokenMs,String issuer) {

}
