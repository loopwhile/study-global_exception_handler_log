package com.study.exception.common.error;

/**
 * 애플리케이션에서 사용하는 비즈니스 예외 타입.
 *
 * 주요 포인트:
 * - ErrorCode 를 함께 가지고 있어 에러 응답이나 로깅에서 사용하기 쉽습니다.
 * - noStackTrace 플래그로 스택트레이스 생성/출력을 제어할 수 있음.
 *   -> 반복적으로 발생하지만 원인을 추적할 필요가 없는 예외(예: 빈 입력으로 인한 빠른 거부 등)에 유용.
 *   -> 단, 스택트레이스를 남기지 않으면 디버깅이 어려워지는 단점이 있으니 주의해서 사용하세요.
 */
public class BusinessException extends RuntimeException {
	// ErrorCode: 이 예외가 어떤 분류의 에러인지(HTTP 상태, 코드, 기본 메시지 등)를 담는 열거형
	private final ErrorCode errorCode;

	// noStackTrace 가 true 면 스택트레이스(호출 정보)를 남기지 않음
	// 반복 발생하는 예외에서 로그가 불필요하게 커지는 것을 막기 위해 사용
	private final boolean noStackTrace;

	/**
	 * 기본 생성자: 스택트레이스를 남깁니다(noStackTrace = false).
	 *
	 * @param errorCode 비즈니스 에러 코드 (ErrorCode enum)
	 * @param message   예외 메시지(사람/클라이언트에게 보여줄 내용)
	 */
	public BusinessException(ErrorCode errorCode, String message) {
		this(errorCode, message, false);
	}

	/**
	 * 스택트레이스 제어가 가능한 생성자.
	 *
	 * 내부적으로 RuntimeException의 다음 생성자를 호출합니다:
	 *   super(message, cause, enableSuppression, writableStackTrace)
	 *
	 * - cause: null (체인 예외를 사용하려면 별도 생성자를 추가할 수 있음)
	 * - enableSuppression: true  (예외 억제 허용)
	 * - writableStackTrace: !noStackTrace (noStackTrace가 true면 스택트레이스 비활성화)
	 *
	 * 이 방식으로 스택트레이스 생성을 JVM 단계에서 방지(또는 허용)합니다.
	 *
	 * @param errorCode    비즈니스 에러 코드
	 * @param message      예외 메시지
	 * @param noStackTrace true이면 스택트레이스를 남기지 않음
	 */
	public BusinessException(ErrorCode errorCode, String message, boolean noStackTrace) {
		// cause=null, enableSuppression=true, writableStackTrace = !noStackTrace
		super(message, null, true, !noStackTrace);
		this.errorCode = errorCode;
		this.noStackTrace = noStackTrace;
	}

	/**
	 * 예외에 연결된 비즈니스 코드 반환.
	 * 전역 예외처리기(GlobalExceptionHandler)에서 이 값을 읽어 HTTP 상태/응답 코드를 결정합니다.
	 *
	 * @return ErrorCode enum
	 */
	public ErrorCode getErrorCode() {
		return errorCode;
	}

	/**
	 * 스택트레이스 생성을 제어하기 위해 fillInStackTrace를 오버라이드합니다.
	 * - 상위 구현은 synchronized로 되어 있으니 동일하게 유지합니다.
	 * - noStackTrace 가 true 면 스택을 채우지 않고 this를 반환(비용 절감).
	 *
	 * 왜 오버라이드 하는가?
	 * - 생성자에서 writableStackTrace=false 로 했더라도 일부 환경/라이브러리에서
	 *   fillInStackTrace 를 직접 호출할 수 있으므로 안전을 위해 명시적으로 제어합니다.
	 *
	 * 주의:
	 * - 스택트레이스가 없으면 문제 원인 추적이 어려워집니다. 운영 로그에 남기지 않아도 되는
	 *   예상 가능한(그리고 자주 발생하는) 예외에만 적용하세요.
	 */
	@Override
	public synchronized Throwable fillInStackTrace() {
		return noStackTrace ? this : super.fillInStackTrace();
	}
}
