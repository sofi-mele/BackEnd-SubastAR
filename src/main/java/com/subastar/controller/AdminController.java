package com.subastar.controller;

import com.subastar.dto.bien.RechazarBienRequest;
import com.subastar.model.SubastaExtra;
import com.subastar.repository.SubastaExtraRepository;
import com.subastar.service.AuthService;
import com.subastar.service.BienService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;
    private final BienService bienService;
    private final SubastaExtraRepository subastaExtraRepository;

    @PostMapping("/registros/{id}/aprobar")
    public ResponseEntity<Map<String, String>> aprobarRegistro(@PathVariable Integer id) {
        authService.aprobarRegistro(id);
        return ResponseEntity.ok(Map.of(
                "message", "Registro aprobado. Codigo generado correctamente. Use el endpoint de envio para mandar el email."
        ));
    }

    @PostMapping("/registros/{id}/enviar-codigo-aprobacion")
    public ResponseEntity<Map<String, String>> enviarCodigoAprobacion(@PathVariable Integer id) {
        authService.enviarCodigoAprobacion(id);
        return ResponseEntity.ok(Map.of(
                "message", "Codigo de aprobacion enviado por email correctamente."
        ));
    }

    @PostMapping("/bienes/{id}/rechazar")
    public ResponseEntity<Map<String, String>> rechazarBien(
            @PathVariable Integer id,
            @Valid @RequestBody RechazarBienRequest req) {
        bienService.rechazarBien(id, req.getMotivo());
        return ResponseEntity.ok(Map.of("message", "Bien rechazado y notificación enviada al usuario."));
    }

    @PostMapping("/subastas/{subastaId}/iniciar-lote")
    public ResponseEntity<Map<String, Object>> iniciarLote(@PathVariable Integer subastaId) {
        SubastaExtra extra = subastaExtraRepository.findBySubastaId(subastaId)
                .orElseThrow(() -> new com.subastar.exception.ResourceNotFoundException("Subasta no encontrada"));
        extra.setFechaInicioLote(LocalDateTime.now());
        extra.setFechaUltimaPuja(null);
        subastaExtraRepository.save(extra);
        return ResponseEntity.ok(Map.of("message", "Timer iniciado", "segundosRestantes", 60));
    }
}
