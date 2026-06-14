package com.subastar.repository;

import com.subastar.model.ItemCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCatalogoRepository extends JpaRepository<ItemCatalogo, Integer> {
    List<ItemCatalogo> findByCatalogoIdentificador(Integer catalogoId);
}
