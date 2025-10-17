package com.study.exception.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemberRequest(
		@NotNull(message = "이름을 입력해주세요") @Size(max = 20)
		String name,
		@NotNull(message = "이메일을 입력해주세요") @Email
		String email
) {}