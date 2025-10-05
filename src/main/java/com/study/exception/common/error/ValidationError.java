package com.study.exception.common.error;

/**
 * 필드 단위 검증 오류 정보를 담는 단순 DTO(읽기 전용).
 *
 * 목적
 * - 입력 검증(validation) 실패 시 어떤 필드에서 어떤 값이 들어왔고,
 *   왜 실패했는지를 클라이언트에 전달하기 위해 사용합니다.
 * - 전역 예외 처리기(GlobalExceptionHandler)에서 {@link ErrorResponse#errors}에 담아 반환합니다.
 *
 * 설계 포인트 (교육생/주니어용)
 * - 모든 필드는 final로 선언되어 인스턴스 생성 이후 변경되지 않습니다(불변).
 * - rejectedValue는 사용자 입력의 원래 값을 담지만, 개인정보(예: 비밀번호)는 절대 담지 말아야 합니다.
 * - 직렬화(예: JSON) 시 필드명이 그대로 노출되므로 민감 정보가 들어가지 않도록 주의하세요.
 *
 * 예시 JSON (ErrorResponse 내부의 errors 항목 예):
 * {
 *   "field": "email",
 *   "rejectedValue": "not-an-email",
 *   "reason": "올바른 형식의 이메일 주소여야 합니다"
 * }
 */
public class ValidationError {

	/**
	 * 검증 실패가 발생한 필드명.
	 * 예: "email", "name", "age"
	 */
	public final String field;

	/**
	 * 검증 시 전달된 원래 값(입력값).
	 * - 값이 너무 크거나 민감한 내용이면 실제 반환하지 않도록 전역 핸들러에서 조정하세요.
	 * - 예: "", "not-an-email", 123
	 */
	public final Object rejectedValue;

	/**
	 * 검증 실패 이유(사용자에게 보여줄 메시지).
	 * - 보통 validation 어노테이션의 메시지 또는 커스텀 메시지를 사용합니다.
	 * - 예: "공백일 수 없습니다", "올바른 형식의 이메일 주소여야 합니다"
	 */
	public final String reason;

	/**
	 * 생성자
	 *
	 * @param field         검증 실패한 필드명
	 * @param rejectedValue 검증 실패 시 입력된 값 (필요시 마스킹해서 전달)
	 * @param reason        검증 실패 이유(사용자용 메시지)
	 */
	public ValidationError(String field, Object rejectedValue, String reason) {
		this.field = field;
		this.rejectedValue = rejectedValue;
		this.reason = reason;
	}

	/*
	 * (선택) 필요하면 toString(), equals(), hashCode() 를 오버라이드해서
	 * 디버깅이나 단위테스트에서 더 편리하게 사용할 수 있습니다.
	 */
}
