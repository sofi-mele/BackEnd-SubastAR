package com.subastar.repository;

import com.subastar.model.CuentaCobro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaCobroRepository extends JpaRepository<CuentaCobro, Integer> {
    List<CuentaCobro> findByClienteId(Integer clienteId);
}
