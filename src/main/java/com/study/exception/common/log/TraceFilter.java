package com.study.exception.common.log;

import jakarta.servlet.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청별 traceId를 생성하여 MDC에 저장하는 서블릿 필터.
 *
 * 목적
 * - 한 HTTP 요청의 로그들을 같은 traceId로 묶어 로그에서 추적하기 위함.
 * - logback 패턴에서 `%X{traceId}`로 출력하면 어떤 요청에서 발생한 로그인지 쉽게 알 수 있습니다.
 *
 * 동작 흐름
 * 1. 요청이 들어오면 UUID 기반의 간단한 traceId를 생성합니다.
 * 2. MDC(Mapped Diagnostic Context)에 "traceId" 키로 넣습니다.
 * 3. filterChain.doFilter(...)로 다음 필터/서블릿을 실행합니다.
 * 4. finally 블록에서 MDC에서 traceId를 제거합니다(메모리 누수 방지).
 *
 * 중요 주의사항 (반드시 기억할 것)
 * - MDC는 스레드 로컬(ThreadLocal) 기반이므로 반드시 finally에서 제거해야 합니다.
 * - 비동기(스레드 풀)로 작업을 위임하면 MDC가 자동으로 전달되지 않습니다. 이 경우 MDC를 복사해서 전달하거나
 *   Spring의 DelegatingSecurityContext/DelegatingExecutor 등을 사용해야 합니다.
 *
 * 사용 예 (logback 패턴 예시)
 *   <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%X{traceId}] %logger{36} - %msg%n</pattern>
 *
 * 개선 아이디어
 * - 클라이언트가 traceId를 전달하는 경우(X-Trace-Id 같은 헤더)를 우선 사용하도록 변경하면,
 *   외부 시스템과 연계된 추적에 유리합니다.
 */
@Component
public class TraceFilter implements Filter {

	/**
	 * 실제 요청 처리 시 호출되는 필터 로직.
	 *
	 * @param servletRequest  서블릿 요청
	 * @param servletResponse 서블릿 응답
	 * @param filterChain     다음 필터/서블릿 체인
	 * @throws IOException      IO 에러 발생 시
	 * @throws ServletException 서블릿 예외 발생 시
	 */
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
			throws IOException, ServletException {

		// 간단한 traceId 생성 (UUID의 앞 8문자만 사용)
		// - 짧게 쓰는 이유: 로그 가독성 향상
		// - 충돌 가능성은 아주 낮지만 완전 무충돌을 원하면 전체 UUID 사용 가능
		String traceId = UUID.randomUUID().toString().substring(0, 8);

		// MDC에 traceId를 넣으면 이후 같은 스레드에서 기록되는 로그에 자동으로 포함됩니다.
		MDC.put("traceId", traceId);

		try {
			// 다음 필터/핸들러로 요청 전달
			filterChain.doFilter(servletRequest, servletResponse);
		} finally {
			// 반드시 제거: 스레드풀을 사용하는 환경에서 MDC가 남아 있으면 다른 요청의 로그에 섞일 수 있음
			MDC.remove("traceId");
		}
	}

	// (선택) 필요하면 init/destroy 구현 가능
	// @Override public void init(FilterConfig filterConfig) { }
	// @Override public void destroy() { }

	/* ===== 추가 팁 (실무에서 고려할 것들) =====
	 *
	 * 1) 요청 헤더 우선 사용
	 *    클라이언트/프록시가 이미 trace id를 제공하면 그 값을 그대로 사용하면 유용합니다.
	 *    예:
	 *      HttpServletRequest req = (HttpServletRequest) servletRequest;
	 *      String incoming = req.getHeader("X-Trace-Id");
	 *      if (incoming != null && !incoming.isBlank()) traceId = incoming;
	 *
	 * 2) 필터 순서
	 *    @Component 만으로 등록하면 Spring이 자동으로 필터로 등록하지만,
	 *    다른 필터와의 순서가 중요하면 @Order나 FilterRegistrationBean 으로 순서를 정하세요.
	 *
	 * 3) 비동기 작업(MQ, 스레드풀)
	 *    비동기 실행 시 MDC는 복사되지 않습니다. Executor를 래핑하거나
	 *    org.slf4j.MDC.MDCCopyOn... 유틸을 사용해 전달해야 traceId가 이어집니다.
	 *
	 * 4) traceId 길이/형식
	 *    - UUID 앞 8자 사용은 가독성을 고려한 트레이드오프입니다.
	 *    - 보안/프라이버시 이유로 외부에 민감한 식별자를 노출하지 않도록 주의하세요.
	 */
}
