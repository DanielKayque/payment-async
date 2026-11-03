package com.payment.async.exceptions;

public class ExceptionGlobalHandler extends RuntimeException {
  public ExceptionGlobalHandler(String message) {
    super(message);
  }
}
