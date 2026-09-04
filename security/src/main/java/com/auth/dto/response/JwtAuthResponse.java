package com.auth.dto.response;

public record JwtAuthResponse (String accesToken,String refreshToken,String tokenType){
}
