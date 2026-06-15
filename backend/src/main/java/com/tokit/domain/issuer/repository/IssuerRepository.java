package com.tokit.domain.issuer.repository;

import com.tokit.domain.issuer.entity.Issuer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuerRepository extends JpaRepository<Issuer, Long> {
}
