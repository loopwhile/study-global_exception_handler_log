package com.study.exception.common.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API 오류 응답(표준화된 JSON 바디) 객체.
 *
 * 목적
 * - 예외 발생 시 클라이언트에 일관된 형태로 에러 정보를 반환하기 위한 DTO입니다.
 * - 전역 예외 처리기(GlobalExceptionHandler)에서 생성하여 사용합니다.
 *
 * 설계 포인트 (교육생/주니어용 설명)
 * - 불변(immutable) 방식으로 설계되어 응답 생성 후 값이 바뀌지 않도록 합니다.
 * - {@link #timestamp}는 인스턴스 생성 시점(서버 시간)을 기록합니다.
 * - {@link #requestId} 같은 필드는 분산 추적(Trace)이나 로깅과 연계해 요청 단위를 식별할 때 사용합니다.
 * - {@link #errors}는 필드 검증 오류 등 상세한 검증 정보를 담는 리스트이며, 없을 경우 null 또는 빈 리스트가 될 수 있습니다.
 *
 * 직렬화 주의사항
 * - Jackson으로 직렬화할 때 OffsetDateTime 포맷이 기본값으로 나옵니다. 원하는 포맷이 있다면
 *   컨트롤러/모듈 레벨에서 {@code @JsonFormat}을 사용하거나 ObjectMapper 설정을 권장합니다.
 */
public class ErrorResponse {

	/**
	 * 오류 발생 시각 (서버 기준). 인스턴스 생성 시점으로 고정됩니다.
	 * - OffsetDateTime.now() 를 사용하므로 타임존이 포함된 ISO-8601 포맷으로 직렬화됩니다.
	 */
	public final OffsetDateTime timestamp = OffsetDateTime.now();

	/**
	 * 요청 식별자 (옵션)
	 * - 분산 시스템에서 traceId 나 correlationId 등을 넣어 요청 단위로 추적할 때 사용합니다.
	 * - GlobalExceptionHandler에서 로깅(또는 MDC)과 함께 설정할 수 있습니다.
	 */
	public final String requestId;

	/**
	 * HTTP 상태 코드 (예: 400, 404, 500)
	 * - 클라이언트는 이 값을 보고 에러 종류(클라이언트/서버)를 판단합니다.
	 */
	public final int status;

	/**
	 * 서비스 내부 에러 코드 (예: "COMMON-400", "BIZ-400")
	 * - 로그/문서/프론트엔드에서 에러를 식별하는 용도로 사용합니다.
	 */
	public final String code;

	/**
	 * 클라이언트에게 보여줄 메시지
	 * - 전역 핸들러에서 상황에 맞는 맞춤형 메시지로 덮어쓸 수 있으며,
	 *   없을 경우 ErrorCode.defaultMessage() 가 사용됩니다.
	 */
	public final String message;

	/**
	 * 요청된 경로(URI)
	 * - 디버깅 목적이나 클라이언트 안내용으로 유용합니다.
	 */
	public final String path;

	/**
	 * HTTP 메서드 (GET, POST, ...)
	 */
	public final String method;

	/**
	 * 상세 검증 오류 목록(선택)
	 * - 예: 필드별 에러 정보(필드명, 입력값, 메시지) 등을 담습니다.
	 * - 검증 오류가 없으면 null 또는 빈 리스트로 설정될 수 있습니다.
	 */
	public final List<ValidationError> errors;

	/**
	 * 기본 생성자(모든 필드를 초기화).
	 *
	 * @param requestId 요청 식별자(옵션)
	 * @param status    HTTP 상태 코드(예: 400, 404, 500)
	 * @param code      서비스 내부 에러 코드(예: "COMMON-400")
	 * @param message   클라이언트에게 보여줄 메시지(비어있으면 기본 메시지 사용)
	 * @param path      요청된 URI
	 * @param method    HTTP 메서드
	 * @param errors    상세 검증 오류 목록(선택)
	 */
	public ErrorResponse(String requestId, int status, String code, String message,
						 String path, String method, List<ValidationError> errors) {
		this.requestId = requestId;
		this.status = status;
		this.code = code;
		this.message = message;
		this.path = path;
		this.method = method;
		this.errors = errors;
	}

	/**
	 * 팩토리 메서드: ErrorCode와 상세 정보를 받아 ErrorResponse를 생성합니다.
	 *
	 * 동작:
	 * - ErrorCode의 상태값(status)와 코드(code)를 사용합니다.
	 * - 전달된 message가 null 이거나 비어있으면(ErrorCode의) 기본 메시지(defaultMessage)로 채웁니다.
	 *
	 * 사용 예:
	 * <pre>
	 *     // 전역 예외 핸들러에서 사용
	 *     ErrorResponse body = ErrorResponse.of(reqId, ErrorCode.NOT_FOUND, ex.getMessage(), req.getRequestURI(), req.getMethod(), null);
	 * </pre>
	 *
	 * @param requestId 요청 식별자(옵션, MDC의 traceId 등)
	 * @param ec        에러 코드(enum)
	 * @param message   실제 보여줄 메시지(없으면 ec.defaultMessage() 사용)
	 * @param path      요청 경로
	 * @param method    HTTP 메서드
	 * @param errors    상세 검증 오류 목록(필드 에러 등). 없으면 null 가능
	 * @return 생성된 ErrorResponse 인스턴스
	 */
	public static ErrorResponse of(String requestId, ErrorCode ec, String message,
								   String path, String method, List<ValidationError> errors) {
		return new ErrorResponse(
				requestId,
				ec.status().value(),
				ec.code(),
				(message == null || message.isBlank()) ? ec.defaultMessage() : message,
				path,
				method,
				errors
		);
	}
}
