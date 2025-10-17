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
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Web(Thymeleaf) 컨트롤러 전용 전역 예외처리기.
 * - @Controller 로 선언된 MVC 컨트롤러에만 적용됩니다.
 * - HTML 에러 페이지를 반환합니다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class GlobalWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalWebExceptionHandler.class);

    /** 상태별 에러 페이지 선택 + 공통 모델 주입 */
    private ModelAndView buildHtml(HttpServletRequest req, ErrorCode ec, String message) {
        String rid = MDC.get("traceId");
        logByStatus(ec, "[WEB][rid=" + rid + "][" + req.getMethod() + " " + req.getRequestURI() + "] " + message);

        ModelMap model = new ModelMap();
        model.addAttribute("status", ec.status().value());
        model.addAttribute("error", ec.code());
        model.addAttribute("message", (message == null || message.isBlank()) ? ec.defaultMessage() : message);
        model.addAttribute("path", req.getRequestURI());
        model.addAttribute("timestamp", OffsetDateTime.now());
        model.addAttribute("requestId", rid);

        String view = switch (ec.status().value()) {
            case 404 -> "error/404";
            case 500 -> "error/500";
            default -> ec.status().is4xxClientError() ? "error/400" : "error/500";
        };
        ModelAndView mv = new ModelAndView(view, model);
        mv.setStatus(ec.status());
        return mv;
    }

    private void logByStatus(ErrorCode ec, String msg) {
        if (ec.status().is4xxClientError()) log.warn("[{}] {}", ec.code(), msg);
        else log.error("[{}] {}", ec.code(), msg);
    }

    // ---------- 예외 핸들러들 (HTML 반환) ----------

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusiness(HttpServletRequest req, BusinessException ex) {
        return buildHtml(req, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ModelAndView handleBind(HttpServletRequest req, BindException ex) {
        // 폼 바인딩 실패는 400 페이지로
        StringBuilder sb = new StringBuilder("폼 바인딩 실패: ");
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            sb.append(fe.getField()).append("=").append(fe.getDefaultMessage()).append("; ");
        }
        return buildHtml(req, ErrorCode.VALIDATION_FAILED, sb.toString());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ModelAndView handleCve(HttpServletRequest req, ConstraintViolationException ex) {
        List<String> details = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            details.add(v.getPropertyPath() + ": " + v.getMessage());
        }
        return buildHtml(req, ErrorCode.VALIDATION_FAILED, "메서드 파라미터 검증 실패: " + String.join(", ", details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(HttpServletRequest req, MethodArgumentTypeMismatchException ex) {
        return buildHtml(req, ErrorCode.TYPE_MISMATCH, ex.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ModelAndView handleMissing(HttpServletRequest req, Exception ex) {
        ErrorCode ec = (ex instanceof MissingServletRequestParameterException) ? ErrorCode.MISSING_PARAM : ErrorCode.BAD_REQUEST;
        return buildHtml(req, ec, ex.getMessage());
    }

    @ExceptionHandler(ConversionFailedException.class)
    public ModelAndView handleConversion(HttpServletRequest req, ConversionFailedException ex) {
        return buildHtml(req, ErrorCode.BAD_REQUEST, "값 변환 실패");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ModelAndView handleNotReadable(HttpServletRequest req, HttpMessageNotReadableException ex) {
        return buildHtml(req, ErrorCode.MESSAGE_NOT_READABLE, "요청 본문 파싱 실패");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView handleMethodNotAllowed(HttpServletRequest req, HttpRequestMethodNotSupportedException ex) {
        return buildHtml(req, ErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 메서드");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ModelAndView handleMediaType(HttpServletRequest req, HttpMediaTypeNotSupportedException ex) {
        return buildHtml(req, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ModelAndView handleNotAcceptable(HttpServletRequest req, HttpMediaTypeNotAcceptableException ex) {
        return buildHtml(req, ErrorCode.NOT_ACCEPTABLE, "응답 타입 협상 불가");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ModelAndView handle404(HttpServletRequest req, NoHandlerFoundException ex) {
        return buildHtml(req, ErrorCode.NOT_FOUND, "요청 경로를 찾을 수 없습니다.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDiv(HttpServletRequest req, DataIntegrityViolationException ex) {
        return buildHtml(req, ErrorCode.CONFLICT, "무결성 제약 위반");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ModelAndView handleDup(HttpServletRequest req, DuplicateKeyException ex) {
        return buildHtml(req, ErrorCode.CONFLICT, "중복 키");
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ModelAndView handleSql(HttpServletRequest req, SQLIntegrityConstraintViolationException ex) {
        return buildHtml(req, ErrorCode.CONFLICT, "SQL 무결성 위반");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ModelAndView handleEnf(HttpServletRequest req, EntityNotFoundException ex) {
        return buildHtml(req, ErrorCode.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    public ModelAndView handleRest(HttpServletRequest req, RestClientException ex) {
        return buildHtml(req, ErrorCode.INTERNAL_ERROR, "외부 연동 오류");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ModelAndView handleIllegal(HttpServletRequest req, RuntimeException ex) {
        return buildHtml(req, ErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnknown(HttpServletRequest req, Exception ex) {
        return buildHtml(req, ErrorCode.INTERNAL_ERROR, "예상치 못한 오류");
    }
}
