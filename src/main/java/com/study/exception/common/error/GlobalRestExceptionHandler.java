package com.study.exception.common.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.FieldError;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

/**
 * REST(@RestController) 전용 전역 예외처리기.
 * - JSON 표준 에러 바디(ErrorResponse)를 반환합니다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RestController.class)
public class GlobalRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);

    private ResponseEntity<ErrorResponse> buildJson(
            HttpServletRequest req, ErrorCode ec, String message,
            List<ValidationError> errors, boolean stack) {

        String rid = MDC.get("traceId");
        String head = "[API][rid=" + rid + "][" + req.getMethod() + " " + req.getRequestURI() + "] ";

        if (ec.status().is4xxClientError()) {
            if (stack) log.warn("[{}] {}{}", ec.code(), head, message);
            else       log.warn("[{}] {}{}", ec.code(), head, message);
        } else {
            if (stack) log.error("[{}] {}{}", ec.code(), head, message, null);
            else       log.error("[{}] {}{}", ec.code(), head, message);
        }

        ErrorResponse body = ErrorResponse.of(rid, ec, message, req.getRequestURI(), req.getMethod(), errors);
        return ResponseEntity.status(ec.status()).body(body);
    }

    // ---------- 예외 핸들러들 (JSON 반환) ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(HttpServletRequest req, BusinessException ex) {
        return buildJson(req, ex.getErrorCode(), ex.getMessage(), null, false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleManve(HttpServletRequest req, MethodArgumentNotValidException ex) {
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fe: ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()));
        }
        return buildJson(req, ErrorCode.VALIDATION_FAILED, "요청 값 검증 실패", errors, false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleCve(HttpServletRequest req, ConstraintViolationException ex) {
        List<ValidationError> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(v ->
                errors.add(new ValidationError(v.getPropertyPath().toString(), v.getInvalidValue(), v.getMessage()))
        );
        return buildJson(req, ErrorCode.VALIDATION_FAILED, "메서드 파라미터 검증 실패", errors, false);
    }

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(HttpServletRequest req, org.springframework.validation.BindException ex) {
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fe: ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()));
        }
        return buildJson(req, ErrorCode.VALIDATION_FAILED, "폼 바인딩 실패", errors, false);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(HttpServletRequest req, MethodArgumentTypeMismatchException ex) {
        return buildJson(req, ErrorCode.TYPE_MISMATCH, ex.getMessage(), null, false);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ResponseEntity<ErrorResponse> handleMissing(HttpServletRequest req, Exception ex) {
        ErrorCode ec = (ex instanceof MissingServletRequestParameterException) ? ErrorCode.MISSING_PARAM : ErrorCode.BAD_REQUEST;
        return buildJson(req, ec, ex.getMessage(), null, false);
    }

    @ExceptionHandler(ConversionFailedException.class)
    public ResponseEntity<ErrorResponse> handleConversion(HttpServletRequest req, ConversionFailedException ex) {
        return buildJson(req, ErrorCode.BAD_REQUEST, "값 변환 실패", null, false);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpServletRequest req, HttpMessageNotReadableException ex) {
        return buildJson(req, ErrorCode.MESSAGE_NOT_READABLE, "JSON 파싱 실패", null, false);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpServletRequest req, HttpRequestMethodNotSupportedException ex) {
        return buildJson(req, ErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 메서드", null, false);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpServletRequest req, HttpMediaTypeNotSupportedException ex) {
        return buildJson(req, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type", null, false);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpServletRequest req, HttpMediaTypeNotAcceptableException ex) {
        return buildJson(req, ErrorCode.NOT_ACCEPTABLE, "응답 타입 협상 불가", null, false);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handle404(HttpServletRequest req, NoHandlerFoundException ex) {
        return buildJson(req, ErrorCode.NOT_FOUND, "요청 경로를 찾을 수 없습니다.", null, false);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDiv(HttpServletRequest req, DataIntegrityViolationException ex) {
        return buildJson(req, ErrorCode.CONFLICT, "무결성 제약 위반", null, false);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDup(HttpServletRequest req, DuplicateKeyException ex) {
        return buildJson(req, ErrorCode.CONFLICT, "중복 키", null, false);
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleSql(HttpServletRequest req, SQLIntegrityConstraintViolationException ex) {
        return buildJson(req, ErrorCode.CONFLICT, "SQL 무결성 위반", null, false);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEnf(HttpServletRequest req, EntityNotFoundException ex) {
        return buildJson(req, ErrorCode.NOT_FOUND, ex.getMessage(), null, false);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRest(HttpServletRequest req, RestClientException ex) {
        return buildJson(req, ErrorCode.INTERNAL_ERROR, "외부 연동 오류", null, true);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleIllegal(HttpServletRequest req, RuntimeException ex) {
        return buildJson(req, ErrorCode.BAD_REQUEST, ex.getMessage(), null, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(HttpServletRequest req, Exception ex) {
        return buildJson(req, ErrorCode.INTERNAL_ERROR, "예상치 못한 오류", null, true);
    }
}
