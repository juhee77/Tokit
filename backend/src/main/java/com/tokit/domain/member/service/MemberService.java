package com.tokit.domain.member.service;

import com.tokit.domain.member.entity.Member;
import com.tokit.domain.member.repository.MemberRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member signUp(String email, String name, String walletAddress) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
        }
        Member member = Member.builder()
                .email(email)
                .name(name)
                .walletAddress(walletAddress)
                .build();
        return memberRepository.save(member);
    }

    public Member getMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
