package com.study.exception.domain.member.dto;

import com.study.exception.domain.member.entity.Member;

public record MemberResponse(Long id, String name, String email) {
	public static MemberResponse of(Member m) {
		return new MemberResponse(m.getId(), m.getName(), m.getEmail());
	}
}
