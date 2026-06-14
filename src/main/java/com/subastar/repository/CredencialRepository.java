package com.subastar.repository;

import com.subastar.model.Credencial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredencialRepository extends JpaRepository<Credencial, Integer> {
    Optional<Credencial> findByEmail(String email);
    Optional<Credencial> findByPersonaId(Integer personaId);
    boolean existsByEmail(String email);
}
