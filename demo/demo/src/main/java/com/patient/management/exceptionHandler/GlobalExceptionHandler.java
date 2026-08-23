package com.patient.management.exceptionHandler;

import com.patient.management.Exception.EmailAlreadyExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleValidException(MethodArgumentNotValidException methodArgumentNotValidException){
        Map<String,String> er = new HashMap<>();
        methodArgumentNotValidException.getBindingResult().getFieldErrors().forEach(ex->{
            er.put(ex.getField(),ex.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(er);
    }

    @ExceptionHandler(EmailAlreadyExistException.class)
    public ResponseEntity<Map<String,String>> handleEmailAlreadyExist(EmailAlreadyExistException ex){
        Map<String,String> error = new HashMap<>();
        log.warn("email already exist in DB {} ",ex.getMessage());
        error.put("message","email already exist");
        return ResponseEntity.badRequest().body(error);
    }

}
