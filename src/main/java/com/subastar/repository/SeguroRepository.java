package com.subastar.repository;

import com.subastar.model.Seguro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguroRepository extends JpaRepository<Seguro, String> {
}
