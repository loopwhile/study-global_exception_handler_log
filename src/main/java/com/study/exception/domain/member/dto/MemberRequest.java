package com.study.exception.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberRequest(
		@NotBlank @Size(max = 20) String name,
		@NotBlank @Email String email
) {}