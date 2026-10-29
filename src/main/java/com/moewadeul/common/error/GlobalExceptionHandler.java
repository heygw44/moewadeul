package com.moewadeul.common.error;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String LOCK_RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (e instanceof InvalidFieldException invalidField) {
            log.debug("invalid field field={} message={}", invalidField.getField(), e.getMessage());
            return ResponseEntity.status(errorCode.httpStatus())
                    .body(ErrorResponse.ofInvalidInput(
                            List.of(new ErrorResponse.FieldError(invalidField.getField(), e.getMessage()))));
        }
        if (errorCode.httpStatus().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("business exception code={} cause={}", errorCode.name(), describeCause(e));
        } else if (errorCode.httpStatus().is5xxServerError()) {
            log.error("business exception code={}", errorCode.name(), e);
        } else {
            log.debug("business exception code={} message={}", errorCode.name(), e.getMessage());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.httpStatus());
        if (e instanceof RateLimitedException rateLimited) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(rateLimited.retryAfter())));
        }
        return response.body(ErrorResponse.of(errorCode, e.getMessage(), e.extraFields()));
    }

    private static String describeCause(BusinessException e) {
        Throwable cause = e.getCause();
        if (cause == null) {
            return "none";
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    // Retry-After는 정수 초만 받는다. 그래서 소수 초는 올리고, 0초 이하는 1초로 맞춰 클라이언트가 바로 재시도하지 않게 한다.
    private static long retryAfterSeconds(Duration retryAfter) {
        long seconds = retryAfter.getSeconds() + (retryAfter.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLockAcquisitionFailure(PessimisticLockingFailureException e) {
        log.warn(
                "lock acquisition failed cause={}: {}",
                e.getClass().getSimpleName(),
                e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(CommonErrorCode.RESOURCE_BUSY.httpStatus())
                .header(HttpHeaders.RETRY_AFTER, LOCK_RETRY_AFTER_SECONDS)
                .body(ErrorResponse.of(CommonErrorCode.RESOURCE_BUSY));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("unexpected exception", e);
        return internalError();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.isBindingFailure()
                    ? typeMismatchMessage(requiredTypeOf(fieldError))
                    : fieldError.getDefaultMessage();
            fieldErrors.add(new ErrorResponse.FieldError(fieldError.getField(), message));
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.add(new ErrorResponse.FieldError(globalError.getObjectName(), globalError.getDefaultMessage()));
        }
        return invalidInput(headers, status, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            addFieldErrors(fieldErrors, result);
        }
        return invalidInput(headers, status, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return invalidInput(
                headers, status, List.of(new ErrorResponse.FieldError(ex.getParameterName(), ex.getMessage())));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return invalidInput(
                    headers,
                    status,
                    List.of(new ErrorResponse.FieldError(
                            mismatch.getName(), typeMismatchMessage(mismatch.getRequiredType()))));
        }
        return invalidInput(headers, status, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() != null
                && invalidFormat.getTargetType().isEnum()) {
            return invalidInput(
                    headers,
                    status,
                    List.of(new ErrorResponse.FieldError(
                            fieldPath(invalidFormat), typeMismatchMessage(invalidFormat.getTargetType()))));
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    private static Class<?> requiredTypeOf(FieldError fieldError) {
        return fieldError.contains(TypeMismatchException.class)
                ? fieldError.unwrap(TypeMismatchException.class).getRequiredType()
                : null;
    }

    private static String typeMismatchMessage(Class<?> requiredType) {
        if (requiredType == null || !requiredType.isEnum()) {
            return "값의 형식이 맞지 않아요.";
        }
        String allowed = Arrays.stream(requiredType.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .collect(Collectors.joining(", "));
        return allowed + " 중 하나여야 해요.";
    }

    private static String fieldPath(JacksonException e) {
        return e.getPath().stream()
                .map(reference -> reference.getPropertyName() != null
                        ? reference.getPropertyName()
                        : String.valueOf(reference.getIndex()))
                .collect(Collectors.joining("."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("framework exception status={}", statusCode.value(), ex);
            return ResponseEntity.status(statusCode)
                    .headers(headers)
                    .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(clientErrorCode(statusCode)));
    }

    private static ErrorCode clientErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> CommonErrorCode.INVALID_INPUT;
        };
    }

    private static void addFieldErrors(List<ErrorResponse.FieldError> fieldErrors, ParameterValidationResult result) {
        String parameterName = parameterName(result.getMethodParameter());
        if (result instanceof ParameterErrors parameterErrors) {
            for (FieldError fieldError : parameterErrors.getFieldErrors()) {
                fieldErrors.add(new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()));
            }
            for (ObjectError globalError : parameterErrors.getGlobalErrors()) {
                fieldErrors.add(new ErrorResponse.FieldError(parameterName, globalError.getDefaultMessage()));
            }
            return;
        }
        for (MessageSourceResolvable error : result.getResolvableErrors()) {
            fieldErrors.add(new ErrorResponse.FieldError(parameterName, error.getDefaultMessage()));
        }
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        if (name != null) {
            return name;
        }
        return "arg" + parameter.getParameterIndex();
    }

    private ResponseEntity<Object> invalidInput(
            HttpHeaders headers, HttpStatusCode status, List<ErrorResponse.FieldError> fieldErrors) {
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.ofInvalidInput(fieldErrors));
    }

    private static ResponseEntity<ErrorResponse> internalError() {
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
    }
}
