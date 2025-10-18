package com.study.exception.common.log;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class TraceFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String traceId = Optional.ofNullable(((HttpServletRequest) request).getHeader("X-Request-Id"))
				.filter(s -> !s.isBlank())
				.orElse(UUID.randomUUID().toString().substring(0, 8));

		MDC.put("traceId", traceId);
		try {
			((HttpServletResponse) response).setHeader("X-Request-Id", traceId);
			chain.doFilter(request, response);
		} finally {
			MDC.remove("traceId");
		}
	}

	@Bean
	public TaskDecorator mdcTaskDecorator() {
		return runnable -> {
			Map<String, String> context = MDC.getCopyOfContextMap();
			return () -> {
				Map<String, String> prev = MDC.getCopyOfContextMap();
				try {
					if (context != null) MDC.setContextMap(context); else MDC.clear();
					runnable.run();
				} finally {
					if (prev != null) MDC.setContextMap(prev); else MDC.clear();
				}
			};
		};
	}
}
