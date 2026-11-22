package com.edgarlens.api;

import com.edgarlens.sec.SecUnavailableException;
import com.edgarlens.sec.UnknownTickerException;
import com.edgarlens.valuation.InsufficientDataException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownTickerException.class)
    ProblemDetail unknownTicker(UnknownTickerException e) {
        return problem(HttpStatus.NOT_FOUND, "Unknown ticker", e.getMessage());
    }

    @ExceptionHandler(InsufficientDataException.class)
    ProblemDetail insufficientData(InsufficientDataException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough data for a valuation", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, HandlerMethodValidationException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(SecUnavailableException.class)
    ProblemDetail secUnavailable(SecUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SEC EDGAR unavailable", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
