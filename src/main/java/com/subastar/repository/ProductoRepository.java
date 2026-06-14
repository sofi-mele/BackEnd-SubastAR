package com.subastar.repository;

import com.subastar.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    List<Producto> findByDuenioIdentificador(Integer duenioId);
}
