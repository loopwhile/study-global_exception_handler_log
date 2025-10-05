package com.study.exception.common.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 전역 예외 처리기(Global Exception Handler)
 *
 * 역할:
 * - 컨트롤러에서 발생하는 다양한 예외를 한곳에서 처리하여 JSON 또는 HTML(에러 페이지)로 응답합니다.
 * - JSON 응답은 ErrorResponse 객체로 표준화되어 반환됩니다.
 * - HTML 응답은 "error" 타임리프 템플릿을 사용합니다.
 *
 * 동작 원리 요약:
 * - 클라이언트의 Accept 헤더에 text/html 이 포함되어 있으면 HTML 에러 페이지를 반환합니다.
 * - 그 외엔 표준화된 JSON(ErrorResponse)을 반환합니다.
 *
 * @Order(Ordered.HIGHEST_PRECEDENCE) : 이 핸들러의 우선순위를 가장 높게 하여 다른 Advice 보다 먼저 예외를 잡도록 합니다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * JSON 응답을 구성하는 헬퍼.
	 *
	 * @param req     요청 객체 (URI, method 조회용)
	 * @param ec      ErrorCode 열거형 (HTTP 상태, 코드, 기본메시지 포함)
	 * @param message 응답에 포함할 메시지(없으면 ErrorCode의 defaultMessage 사용)
	 * @param errors  검증 에러 목록 (필요시)
	 * @param stack   true면 내부 로깅 시 스택 추적이 필요한 경우 (보통 5xx)
	 * @return ResponseEntity&lt;ErrorResponse&gt; 표준화된 JSON 바디와 상태코드
	 *
	 * 중요 설명:
	 * - 이 메서드에서 로그를 남기는데, 4xx/5xx에 따라 warn/error를 구분합니다.
	 * - stack 파라미터는 로그 레벨/스택 출력 여부를 결정하는 용도로 사용 (여기선 동일하게 메시지 로깅).
	 */
	private ResponseEntity<ErrorResponse> buildJson(HttpServletRequest req, ErrorCode ec, String message, List<ValidationError> errors, boolean stack) {
		// 로그 기록: 실제 환경에서는 에러의 민감도나 스택 출력 여부를 더 정교하게 제어할 수 있음
		if (ec.status().is4xxClientError()) {
			// 클라이언트 오류는 경고 수준으로 기록
			if (stack) log.warn("[{}] {}", ec.code(), message);
			else log.warn("[{}] {}", ec.code(), message);
		} else {
			// 서버 오류(5xx)는 에러 수준으로 기록
			if (stack) log.error("[{}] {}", ec.code(), message);
			else log.error("[{}] {}", ec.code(), message);
		}
		ErrorResponse body = ErrorResponse.of(null, ec, message, req.getRequestURI(), req.getMethod(), errors);
		return ResponseEntity.status(ec.status()).body(body);
	}

	/**
	 * HTML 에러 페이지(ModelAndView)를 구성하는 헬퍼.
	 *
	 * - 뷰 이름은 "error"로 하고, 모델에 공통 항목(status, error(code), message, path, timestamp)을 넣습니다.
	 * - 편의상 타임리프 템플릿에서 이 모델을 읽어 에러 페이지를 렌더링하도록 구성되어야 합니다.
	 */
	private ModelAndView buildHtml(HttpServletRequest req, ErrorCode ec, String message) {
		ModelMap model = new ModelMap();
		model.addAttribute("status", ec.status().value());
		model.addAttribute("error", ec.code());
		model.addAttribute("message", (message == null || message.isBlank()) ? ec.defaultMessage() : message);
		model.addAttribute("path", req.getRequestURI());
		model.addAttribute("timestamp", OffsetDateTime.now());
		ModelAndView mv = new ModelAndView("error", model);
		mv.setStatus(ec.status());
		return mv;
	}

	/**
	 * 클라이언트가 HTML을 원하면(true) HTML 에러 페이지, 아니면 JSON 반환.
	 *
	 * 작동 방식:
	 * - Accept 헤더에 "text/html"이 포함되어 있으면 HTML 반환을 선호.
	 * - 참고: 더 정교하게 하려면 Accept 헤더를 파싱해서 우선순위를 고려하거나
	 *   요청 URL 패턴(예: /api/** -> 항상 JSON)으로 강제할 수 있습니다.
	 */
	private boolean wantsHtml(HttpServletRequest req) {
		String accept = req.getHeader("Accept");
		return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
	}

	/* ============================
	 *  예외 처리 핸들러들
	 *  각 핸들러는 발생 가능한 상황(언제 발생하는지)과 반환 형태를 주석으로 적었습니다.
	 * ============================ */

	/**
	 * 비즈니스 예외 처리
	 *
	 * 예: 도메인 검증 실패, 명시적으로 throw new BusinessException(...) 한 경우
	 *
	 * 중요도: ★★★★★ (5/5)
	 */
	@ExceptionHandler(BusinessException.class)
	public Object handleBusiness(HttpServletRequest req, BusinessException ex) {
		ErrorCode ec = ex.getErrorCode();
		return wantsHtml(req) ? buildHtml(req, ec, ex.getMessage())
				: buildJson(req, ec, ex.getMessage(), null, false);
	}

	/**
	 * @ExceptionHandler for @Valid 으로 검증 실패 시 발생 (요청 바디의 DTO 검증 실패)
	 *
	 * 예: 컨트롤러 메서드의 @RequestBody DTO 에서 @NotBlank, @Email 등 어노테이션 검증 실패
	 *
	 * 중요도: ★★★★★ (5/5)
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public Object handleManve(HttpServletRequest req, MethodArgumentNotValidException ex) {
		List<ValidationError> errors = new ArrayList<>();
		for (FieldError fe: ex.getBindingResult().getFieldErrors()) {
			errors.add(new ValidationError(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()));
		}
		ErrorCode ec = ErrorCode.VALIDATION_FAILED;
		return wantsHtml(req) ? buildHtml(req, ec, "요청 값 검증 실패")
				: buildJson(req, ec, "요청 값 검증 실패", errors, false);
	}

	/**
	 * 폼 바인딩 실패 처리 (주로 form-urlencoded로 들어오는 폼 검증 실패)
	 *
	 * 예: @ModelAttribute 바인딩 시 타입/검증 문제
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(BindException.class)
	public Object handleBind(HttpServletRequest req, BindException ex) {
		List<ValidationError> errors = new ArrayList<>();
		for (FieldError fe: ex.getBindingResult().getFieldErrors()) {
			errors.add(new ValidationError(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()));
		}
		ErrorCode ec = ErrorCode.VALIDATION_FAILED;
		return wantsHtml(req) ? buildHtml(req, ec, "폼 바인딩 실패")
				: buildJson(req, ec, "폼 바인딩 실패", errors, false);
	}

	/**
	 * 메서드 파라미터 제약(Constraint) 위반 처리
	 *
	 * 예: 컨트롤러 메서드 파라미터에 @Validated 로 검증 시 ConstraintViolationException 발생
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public Object handleCve(HttpServletRequest req, ConstraintViolationException ex) {
		List<ValidationError> errors = new ArrayList<>();
		for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
			errors.add(new ValidationError(v.getPropertyPath().toString(), v.getInvalidValue(), v.getMessage()));
		}
		ErrorCode ec = ErrorCode.VALIDATION_FAILED;
		return wantsHtml(req) ? buildHtml(req, ec, "메서드 파라미터 검증 실패")
				: buildJson(req, ec, "메서드 파라미터 검증 실패", errors, false);
	}

	/**
	 * 파라미터 타입 불일치 처리
	 *
	 * 예: @PathVariable Long id 인데 "abc" 같은 문자열이 들어올 때
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public Object handleTypeMismatch(HttpServletRequest req, MethodArgumentTypeMismatchException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.TYPE_MISMATCH, ex.getMessage())
				: buildJson(req, ErrorCode.TYPE_MISMATCH, ex.getMessage(), null, false);
	}

	/**
	 * 필수 파라미터 누락 / PathVariable 누락 처리
	 *
	 * 예:
	 *  - MissingServletRequestParameterException: required query/form parameter 가 없는 경우
	 *  - MissingPathVariableException: URL 경로 변수 누락
	 *
	 * 중요도: ★★★☆☆ (3/5)
	 */
	@ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
	public Object handleMissing(HttpServletRequest req, Exception ex) {
		ErrorCode ec = (ex instanceof MissingServletRequestParameterException) ? ErrorCode.MISSING_PARAM : ErrorCode.BAD_REQUEST;
		return wantsHtml(req) ? buildHtml(req, ec, ex.getMessage())
				: buildJson(req, ec, ex.getMessage(), null, false);
	}

	/**
	 * 값 변환 실패 처리 (스프링 내부 컨버전 실패)
	 *
	 * 예: Converter에서 변환 불가(문자열 -> enum 등)
	 *
	 * 중요도: ★★★☆☆ (3/5)
	 */
	@ExceptionHandler(ConversionFailedException.class)
	public Object handleConversion(HttpServletRequest req, ConversionFailedException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.BAD_REQUEST, "값 변환 실패")
				: buildJson(req, ErrorCode.BAD_REQUEST, "값 변환 실패", null, false);
	}

	/* ============================
	 *  HTTP / 메시지 관련 예외
	 * ============================ */

	/**
	 * JSON 바디 파싱 실패 처리
	 *
	 * 예: 잘못된 JSON 형식(문법 오류), 누락된 중괄호 등으로 파싱 실패 시
	 *
	 * 중요도: ★★★★★ (5/5)
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public Object handleNotReadable(HttpServletRequest req, HttpMessageNotReadableException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.MESSAGE_NOT_READABLE, "JSON 파싱 실패")
				: buildJson(req, ErrorCode.MESSAGE_NOT_READABLE, "JSON 파싱 실패", null, false);
	}

	/**
	 * 허용되지 않은 HTTP 메서드 처리
	 *
	 * 예: GET 전용 엔드포인트에 POST로 요청한 경우
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public Object handleMethodNotAllowed(HttpServletRequest req, HttpRequestMethodNotSupportedException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 메서드")
				: buildJson(req, ErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 메서드", null, false);
	}

	/**
	 * 지원하지 않는 Content-Type 처리
	 *
	 * 예: 컨트롤러가 application/json만 받는데 text/plain으로 보낼 때
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public Object handleMediaType(HttpServletRequest req, HttpMediaTypeNotSupportedException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type")
				: buildJson(req, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type", null, false);
	}

	/**
	 * 클라이언트가 받아들일 수 있는 미디어 타입을 협상할 수 없는 경우
	 *
	 * 예: Accept 헤더가 서버가 제공하지 않는 타입만 포함할 때
	 *
	 * 중요도: ★★★☆☆ (3/5)
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public Object handleNotAcceptable(HttpServletRequest req, HttpMediaTypeNotAcceptableException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.NOT_ACCEPTABLE, "응답 타입 협상 불가")
				: buildJson(req, ErrorCode.NOT_ACCEPTABLE, "응답 타입 협상 불가", null, false);
	}

	/**
	 * 404 처리: 매핑된 핸들러가 없을 때(NoHandlerFoundException)
	 *
	 * - 주의: application.properties에 spring.mvc.throw-exception-if-no-handler-found=true 설정이 필요함.
	 *
	 * 중요도: ★★★★★ (5/5)
	 */
	@ExceptionHandler(NoHandlerFoundException.class)
	public Object handle404(HttpServletRequest req, NoHandlerFoundException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.NOT_FOUND, "요청 경로를 찾을 수 없습니다.")
				: buildJson(req, ErrorCode.NOT_FOUND, "요청 경로를 찾을 수 없습니다.", null, false);
	}

	/* ============================
	 *  데이터베이스 / 영속성 관련 예외
	 * ============================ */

	/**
	 * 데이터 무결성 제약 위반 (스프링 DataAccess 추상화 예외)
	 *
	 * 예: NOT NULL, FK 제약 위반 등
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public Object handleDiv(HttpServletRequest req, DataIntegrityViolationException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.CONFLICT, "무결성 제약 위반")
				: buildJson(req, ErrorCode.CONFLICT, "무결성 제약 위반", null, false);
	}

	/**
	 * 중복 키 예외 처리 (예: 고유 제약 위반을 응용레벨에서 DuplicateKeyException으로 포장한 경우)
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	public Object handleDup(HttpServletRequest req, DuplicateKeyException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.CONFLICT, "중복 키")
				: buildJson(req, ErrorCode.CONFLICT, "중복 키", null, false);
	}

	/**
	 * SQL 무결성 제약 위반 (JDBC 수준 예외)
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(SQLIntegrityConstraintViolationException.class)
	public Object handleSql(HttpServletRequest req, SQLIntegrityConstraintViolationException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.CONFLICT, "SQL 무결성 위반")
				: buildJson(req, ErrorCode.CONFLICT, "SQL 무결성 위반", null, false);
	}

	/**
	 * 엔티티를 찾을 수 없음 처리 (예: JPA EntityManager#getReference 후 접근 시)
	 *
	 * 중요도: ★★★★☆ (4/5)
	 */
	@ExceptionHandler(EntityNotFoundException.class)
	public Object handleEnf(HttpServletRequest req, EntityNotFoundException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.NOT_FOUND, ex.getMessage())
				: buildJson(req, ErrorCode.NOT_FOUND, ex.getMessage(), null, false);
	}

	/* ============================
	 *  외부 연동 / 기타 예외
	 * ============================ */

	/**
	 * 외부 서비스 호출 실패(REST 클라이언트 예외)
	 *
	 * 예: RestTemplate/Feign/WebClient 호출 실패 시
	 *
	 * 중요도: ★★★☆☆ (3/5)
	 */
	@ExceptionHandler(RestClientException.class)
	public Object handleRest(HttpServletRequest req, RestClientException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.INTERNAL_ERROR, "외부 연동 오류")
				: buildJson(req, ErrorCode.INTERNAL_ERROR, "외부 연동 오류", null, true);
	}

	/**
	 * 일반적인 잘못된 인자 / 상태 예외
	 *
	 * 예: IllegalArgumentException, IllegalStateException 등 (개발자가 throw 하는 예외)
	 *
	 * 중요도: ★★★☆☆ (3/5)
	 */
	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public Object handleIllegal(HttpServletRequest req, RuntimeException ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.BAD_REQUEST, ex.getMessage())
				: buildJson(req, ErrorCode.BAD_REQUEST, ex.getMessage(), null, false);
	}

	/**
	 * 최종 방어선: 모든 예외를 잡아 500으로 변환
	 *
	 * - 애플리케이션에서 예상치 못한 예외가 발생하면 이 핸들러로 들어옵니다.
	 * - stack=true로 로깅 시 스택트레이스를 남기도록 설정되어 있음(진단용).
	 *
	 * 중요도: ★★★★★ (5/5)
	 */
	@ExceptionHandler(Exception.class)
	public Object handleUnknown(HttpServletRequest req, Exception ex) {
		return wantsHtml(req) ? buildHtml(req, ErrorCode.INTERNAL_ERROR, "예상치 못한 오류")
				: buildJson(req, ErrorCode.INTERNAL_ERROR, "예상치 못한 오류", null, true);
	}
}
