package com.luislipinski.trucklife.shared.error;

import com.luislipinski.trucklife.shared.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                exception.code(),
                "Resource not found",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(
            RateLimitExceededException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problem(
                exception.status(),
                exception.code(),
                exception.title(),
                exception.getMessage(),
                request
        );
        return ResponseEntity.status(exception.status())
                .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(ApiProblemException.class)
    ProblemDetail handleApiProblem(ApiProblemException exception, HttpServletRequest request) {
        return problem(
                exception.status(),
                exception.code(),
                exception.title(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidArgument(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Validation failed",
                "One or more request fields are invalid",
                request
        );
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()
                ))
                .toList();
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "Constraint violation",
                "One or more request constraints were not satisfied",
                request
        );
        List<Map<String, String>> violations = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()
                ))
                .toList();
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Malformed request",
                "The request body could not be read",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request error", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                "The request could not be completed",
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(
                "https://truck-life-simulator.dev/problems/" + code.toLowerCase(Locale.ROOT)
        ));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE));
        return problem;
    }
}
