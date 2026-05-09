package com.pm.patientservice.exception;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(
      GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationException(
      MethodArgumentNotValidException ex) {

    Map<String, String> errors = new HashMap<>();

    ex.getBindingResult().getFieldErrors().forEach(
        error -> errors.put(error.getField(), error.getDefaultMessage()));

    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ResponseEntity<Map<String, String>> handleEmailAlreadyExistsException(
      EmailAlreadyExistsException ex) {

    log.warn("Email address already exist {}", ex.getMessage());
    Map<String, String> errors = new HashMap<>();
    errors.put("message", "Email address already exists");
    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(PatientNotFoundException.class)
  public ResponseEntity<Map<String, String>> handlePatientNotFoundException(
      PatientNotFoundException ex) {
    log.warn("Patient not found {}", ex.getMessage());

    Map<String, String> errors = new HashMap<>();
    errors.put("message", "Patient not found");
    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(BillingServiceException.class)
  public ResponseEntity<Map<String, String>> handleBillingServiceException(
      BillingServiceException ex) {
    log.error("Billing service error: {}", ex.getMessage(), ex);

    Map<String, String> errors = new HashMap<>();
    errors.put("message", "Patient creation failed: billing service unavailable");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errors);
  }
}
