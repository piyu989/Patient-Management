package com.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception ex){
        Map<String,Object> mp = new HashMap<>();
        mp.put("message", ex.getMessage());
        mp.put("status", ex.getClass().getSimpleName());

        ex.printStackTrace();
        return mp;
    }

}
