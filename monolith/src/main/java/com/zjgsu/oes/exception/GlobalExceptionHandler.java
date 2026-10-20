package com.zjgsu.oes.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail missing(ResourceNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ProblemDetail conflict(BusinessConflictException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail constraint(DataIntegrityViolationException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Database constraint prevents this operation");
    }
}
