package com.study.exception.domain.member.controller;

import com.study.exception.domain.member.dto.MemberRequest;
import com.study.exception.domain.member.dto.MemberResponse;
import com.study.exception.domain.member.entity.Member;
import com.study.exception.domain.member.repository.MemberRepository;
import com.study.exception.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberRestController {

    private final MemberService service;
    private final MemberRepository repo;

    @GetMapping
    public List<MemberResponse> list() {
        return repo.findAll().stream().map(MemberResponse::of).toList();
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public MemberResponse create(@Valid @RequestBody MemberRequest req) {
        return service.create(req);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MemberResponse update(@PathVariable Long id, @Valid @RequestBody MemberRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // 강제 예외(REST)
    @GetMapping("/boom")
    public String boom() { throw new IllegalStateException("데모용 강제 예외(API)"); }
}
