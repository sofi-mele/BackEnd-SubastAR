package com.subastar.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subastas_extra")
public class SubastaExtra {
    @Id
    @Column(name = "subasta_id")
    private Integer subastaId;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "moneda", nullable = false)
    private String moneda;

    @Column(name = "url_streaming")
    private String urlStreaming;

    @Column(name = "item_actual_id")
    private Integer itemActualId;

    @Column(name = "fecha_inicio_lote")
    private LocalDateTime fechaInicioLote;

    @Column(name = "fecha_ultima_puja")
    private LocalDateTime fechaUltimaPuja;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subasta_id", insertable = false, updatable = false)
    private Subasta subasta;

    @PrePersist
    public void prePersist() {
        if (moneda == null) moneda = "ARS";
    }
}
