package com.payment.authservice.repository;

import com.payment.common.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByKakaoId(String kakaoId);
}
