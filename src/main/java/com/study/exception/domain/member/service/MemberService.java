package com.study.exception.domain.member.service;

import com.study.exception.common.error.BusinessException;
import com.study.exception.common.error.ErrorCode;
import com.study.exception.domain.member.dto.MemberRequest;
import com.study.exception.domain.member.dto.MemberResponse;
import com.study.exception.domain.member.entity.Member;
import com.study.exception.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository repo;

	public MemberResponse create(MemberRequest req) {
		if (repo.existsByEmail(req.email())) {
			throw new DuplicateKeyException("이미 사용 중인 이메일");
		}
		if ("admin".equalsIgnoreCase(req.name())) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE, "해당 이름은 예약어입니다.", true);
		}
		Member saved = repo.save(Member.builder().name(req.name()).email(req.email()).build());
		return MemberResponse.of(saved);
	}

	public MemberResponse get(Long id) {
		Member m = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("회원 없음: id=" + id));
		return MemberResponse.of(m);
	}

	public MemberResponse update(Long id, MemberRequest req) {
		Member m = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("회원 없음: id=" + id));
		m.setName(req.name());
		if (!m.getEmail().equals(req.email()) && repo.existsByEmail(req.email())) {
			throw new DuplicateKeyException("이미 사용 중인 이메일");
		}
		m.setEmail(req.email());
		return MemberResponse.of(m);
	}

	public void delete(Long id) {
		if (!repo.existsById(id)) throw new EntityNotFoundException("회원 없음: id=" + id);
		repo.deleteById(id);
	}
}