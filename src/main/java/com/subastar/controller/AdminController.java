package com.subastar.controller;

import com.subastar.dto.admin.AsignarPolizaRequest;
import com.subastar.dto.admin.AsignarSubastaRequest;
import com.subastar.dto.admin.CrearSubastaRequest;
import com.subastar.dto.bien.AceptarBienRequest;
import com.subastar.dto.bien.RechazarBienRequest;
import com.subastar.model.MedioPago;
import com.subastar.model.SubastaExtra;
import com.subastar.repository.MedioPagoRepository;
import com.subastar.repository.SubastaExtraRepository;
import com.subastar.service.AdminService;
import com.subastar.service.AuthService;
import com.subastar.service.BienService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final AdminService adminService;
    private final SubastaExtraRepository subastaExtraRepository;
    private final MedioPagoRepository medioPagoRepository;

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

    @PostMapping("/bienes/{id}/aceptar")
    public ResponseEntity<Map<String, String>> aceptarBien(
            @PathVariable Integer id,
            @Valid @RequestBody AceptarBienRequest req) {
        bienService.aceptarBien(id, req);
        return ResponseEntity.ok(Map.of("message", "Bien aceptado correctamente."));
    }

    @PostMapping("/bienes/{id}/rechazar")
    public ResponseEntity<Map<String, String>> rechazarBien(
            @PathVariable Integer id,
            @Valid @RequestBody RechazarBienRequest req) {
        bienService.rechazarBien(id, req.getMotivo());
        return ResponseEntity.ok(Map.of("message", "Bien rechazado y notificación enviada al usuario."));
    }

    @PostMapping("/subastas/crear")
    public ResponseEntity<Map<String, Object>> crearSubasta(@Valid @RequestBody CrearSubastaRequest req) {
        Integer id = adminService.crearSubasta(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "message", "Subasta creada correctamente."));
    }

    @PostMapping("/subastas/{id}/resetear")
    public ResponseEntity<Map<String, String>> resetearSubasta(@PathVariable Integer id) {
        adminService.resetearSubasta(id);
        return ResponseEntity.ok(Map.of("message", "Subasta reseteada correctamente."));
    }

    @PostMapping("/bienes/{id}/asignar-subasta")
    public ResponseEntity<Map<String, String>> asignarSubasta(
            @PathVariable Integer id,
            @Valid @RequestBody AsignarSubastaRequest req) {
        adminService.asignarSubasta(id, req);
        return ResponseEntity.ok(Map.of("message", "Bien asignado a la subasta correctamente."));
    }

    @PostMapping("/bienes/{id}/asignar-poliza")
    public ResponseEntity<Map<String, String>> asignarPoliza(
            @PathVariable Integer id,
            @Valid @RequestBody AsignarPolizaRequest req) {
        adminService.asignarPoliza(id, req);
        return ResponseEntity.ok(Map.of("message", "Póliza asignada al bien correctamente."));
    }

    @PostMapping("/medios-pago/{id}/verificar")
    public ResponseEntity<Map<String, String>> verificarMedioPago(@PathVariable Integer id) {
        MedioPago medioPago = medioPagoRepository.findById(id)
                .orElseThrow(() -> new com.subastar.exception.ResourceNotFoundException("Medio de pago no encontrado"));
        medioPago.setVerificado(true);
        medioPagoRepository.save(medioPago);
        return ResponseEntity.ok(Map.of("message", "Medio de pago verificado correctamente."));
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
