package com.demandline.library.service;

import com.demandline.library.repository.MemberRepository;
import com.demandline.library.service.model.Member;
import com.demandline.library.service.model.filter.MemberFilter;
import com.demandline.library.service.model.input.MemberInput;
import com.demandline.library.service.model.input.MemberUpdateInput;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member getMemberById(String memberId) {
        return null;
    }

    public List<Member> getAllMembers(MemberFilter memberFilter, int limit, int offset) {
        return null;
    }

    public Member createMember(MemberInput member) {
        return null;
    }

    public Member updateMember(MemberUpdateInput member) {
        return null;
    }

    public void deleteMember(String memberId) {
    }
}
