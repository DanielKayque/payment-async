package com.payment.async.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionGlobalHandler {

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleIdempotencyKeyConflictException(IdempotencyKeyConflictException e){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDto(HttpStatus.CONFLICT.value(), e.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDto(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }
}
