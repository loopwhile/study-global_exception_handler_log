package com.study.exception.domain.member.controller;

import com.study.exception.domain.member.dto.MemberRequest;
import com.study.exception.domain.member.dto.MemberResponse;
import com.study.exception.domain.member.repository.MemberRepository;
import com.study.exception.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class MemberController {

	private final MemberService service;
	private final MemberRepository repo;

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("count", repo.count());
		return "index";
	}

	@GetMapping("/members")
	public String list(Model model) {
		model.addAttribute("members", repo.findAll());
		return "member/list";
	}

	@GetMapping("/members/new")
	public String form(Model model) {
		model.addAttribute("member", new MemberRequest("", ""));
		model.addAttribute("action", "/members");
		return "member/form";
	}

	@PostMapping(value = "/members", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public String createForm(@Valid MemberRequest req) {
		service.create(req);
		return "redirect:/members";
	}

	@PostMapping(value = "/members", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public MemberResponse createJson(@Valid @RequestBody MemberRequest req) {
		return service.create(req);
	}

	@GetMapping("/members/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("member", service.get(id));
		return "member/detail";
	}

	@GetMapping("/members/{id}/edit")
	public String edit(@PathVariable Long id, Model model) {
		model.addAttribute("memberId", id);
		model.addAttribute("action", "/members/" + id);
		model.addAttribute("member", service.get(id));
		return "member/form";
	}

	@PostMapping(value="/members/{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public String updateForm(@PathVariable Long id, @Valid MemberRequest req) {
		service.update(id, req);
		return "redirect:/members/" + id;
	}

	@PostMapping("/members/{id}/delete")
	public String delete(@PathVariable Long id) {
		service.delete(id);
		return "redirect:/members";
	}

	// 강제 예외 테스트
	@GetMapping("/boom")
	@ResponseBody
	public String boom() { throw new IllegalStateException("데모용 강제 예외"); }
}
