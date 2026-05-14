package com.reservation.flashsale.member.repository;

import com.reservation.flashsale.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
