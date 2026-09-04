package com.auth.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtils {
    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    @Autowired
    public JwtUtils(JwtProperties jwtProperties){
            this.jwtProperties = jwtProperties;
            this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes());
    }

    public String generateToken(UserDetails userDetails){
        List<String> collect = userDetails.getAuthorities().stream().
                map(GrantedAuthority::getAuthority).collect(Collectors.toList());

        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + jwtProperties.expirationTimeMs());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.issuer())
                .claim("roles", collect)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + jwtProperties.refreshTokenMs());

        return Jwts.builder().subject(userDetails.getUsername())
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }
    public String extractUsername(String token){
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails){
        String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public boolean isTokenExpired(String token){
        return parseClaims(token).getExpiration().before(new Date());
    }



    private Claims parseClaims(String token){
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

}
