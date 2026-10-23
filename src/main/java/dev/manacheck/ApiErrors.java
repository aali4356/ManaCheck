package dev.manacheck;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ApiController.class)
public class ApiErrors {
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> domain(ApiException e) {
    return problem(e.status(), e.code(), e.getMessage());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  public ResponseEntity<ProblemDetail> input(Exception e) {
    return problem(400, "BAD_REQUEST", "Request fields or identifiers are invalid");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> constraint(Exception e) {
    return problem(409, "CONSTRAINT", "This change conflicts with a database constraint");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LoggerFactory.getLogger(ApiErrors.class).error("Request failed", e);
    return problem(
        500,
        "INTERNAL_ERROR",
        "The request could not be completed. Inspect the local application log.");
  }

  private ResponseEntity<ProblemDetail> problem(int status, String code, String message) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message);
    problem.setProperty("code", code);
    return ResponseEntity.status(status).body(problem);
  }
}
