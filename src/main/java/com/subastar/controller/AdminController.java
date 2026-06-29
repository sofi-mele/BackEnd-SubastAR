package com.subastar.controller;

import com.subastar.dto.admin.AsignarPolizaRequest;
import com.subastar.dto.admin.AsignarSubastaRequest;
import com.subastar.dto.admin.CrearSubastaRequest;
import com.subastar.dto.admin.ModificarSubastaRequest;
import com.subastar.dto.bien.AceptarBienRequest;
import com.subastar.dto.bien.RechazarBienRequest;
import com.subastar.model.MedioPago;
import com.subastar.model.Subastador;
import com.subastar.model.SubastaExtra;
import com.subastar.repository.MedioPagoRepository;
import com.subastar.repository.SubastaExtraRepository;
import com.subastar.repository.SubastadorRepository;
import com.subastar.service.AdminService;
import com.subastar.service.AuthService;
import com.subastar.service.BienService;
import com.subastar.service.SubastaEnVivoTimerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
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
    private final SubastadorRepository subastadorRepository;
    private final SubastaEnVivoTimerService timerService;

    @GetMapping("/subastadores")
    public ResponseEntity<List<Map<String, Object>>> listarSubastadores() {
        List<Map<String, Object>> result = subastadorRepository.findAll().stream()
                .map(s -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", s.getIdentificador());
                    m.put("matricula", s.getMatricula());
                    m.put("region", s.getRegion());
                    m.put("nombre", s.getPersona() != null ? s.getPersona().getNombre() : null);
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

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
        bienService.rechazarBien(id, req);
        return ResponseEntity.ok(Map.of("message", "Bien rechazado y notificación enviada al usuario."));
    }

    @PostMapping("/subastas/crear")
    public ResponseEntity<Map<String, Object>> crearSubasta(@RequestBody CrearSubastaRequest req) {
        Integer id = adminService.crearSubasta(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "message", "Subasta creada correctamente."));
    }

    @PutMapping("/subastas/{id}")
    public ResponseEntity<Map<String, String>> modificarSubasta(
            @PathVariable Integer id,
            @RequestBody ModificarSubastaRequest req) {
        adminService.modificarSubasta(id, req);
        return ResponseEntity.ok(Map.of("message", "Subasta modificada correctamente."));
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

    @PostMapping("/bienes/{id}/indicar-direccion-envio")
    public ResponseEntity<Map<String, String>> indicarDireccionEnvio(@PathVariable Integer id) {
        adminService.indicarDireccionEnvio(id);
        return ResponseEntity.ok(Map.of("message", "Dirección de envío informada al usuario correctamente."));
    }

    @PostMapping("/bienes/{id}/asignar-poliza")
    public ResponseEntity<Map<String, String>> asignarPoliza(
            @PathVariable Integer id,
            @Valid @RequestBody AsignarPolizaRequest req) {
        adminService.asignarPoliza(id, req);
        return ResponseEntity.ok(Map.of("message", "Póliza asignada al bien correctamente."));
    }

    @PostMapping("/compras/{id}/costo-envio")
    public ResponseEntity<Map<String, String>> cargarCostoEnvio(
            @PathVariable Integer id,
            @RequestBody Map<String, java.math.BigDecimal> body) {
        java.math.BigDecimal costoEnvio = body.get("costo_envio");
        if (costoEnvio == null || costoEnvio.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new com.subastar.exception.BadRequestException("El costo de envío debe ser mayor a 0");
        }
        adminService.cargarCostoEnvio(id, costoEnvio);
        return ResponseEntity.ok(Map.of("message", "Costo de envío registrado y notificación enviada al cliente."));
    }

    @PostMapping("/medios-pago/{id}/verificar")
    public ResponseEntity<Map<String, String>> verificarMedioPago(@PathVariable Integer id) {
        MedioPago medioPago = medioPagoRepository.findById(id)
                .orElseThrow(() -> new com.subastar.exception.ResourceNotFoundException("Medio de pago no encontrado"));
        medioPago.setVerificado(true);
        medioPagoRepository.save(medioPago);
        return ResponseEntity.ok(Map.of("message", "Medio de pago verificado correctamente."));
    }

    @PostMapping("/medios-pago/{id}/rechazar")
    public ResponseEntity<Map<String, String>> rechazarMedioPago(@PathVariable Integer id) {
        MedioPago medioPago = medioPagoRepository.findById(id)
                .orElseThrow(() -> new com.subastar.exception.ResourceNotFoundException("Medio de pago no encontrado"));
        medioPago.setRechazado(true);
        medioPago.setVerificado(false);
        medioPagoRepository.save(medioPago);
        return ResponseEntity.ok(Map.of("message", "Medio de pago rechazado correctamente."));
    }

    @PostMapping("/clientes/{email}/readmitir")
    public ResponseEntity<Map<String, String>> readmitirCliente(@PathVariable String email) {
        adminService.readmitirCliente(email);
        return ResponseEntity.ok(Map.of("message", "Cliente readmitido correctamente."));
    }

    @PostMapping("/subastas/{subastaId}/iniciar-lote")
    public ResponseEntity<Map<String, Object>> iniciarLote(@PathVariable Integer subastaId) {
        SubastaExtra extra = subastaExtraRepository.findBySubastaId(subastaId)
                .orElseThrow(() -> new com.subastar.exception.ResourceNotFoundException("Subasta no encontrada"));
        extra.setFechaInicioLote(LocalDateTime.now());
        extra.setFechaUltimaPuja(null);
        subastaExtraRepository.save(extra);
        timerService.resetTimer(subastaId);
        return ResponseEntity.ok(Map.of("message", "Timer iniciado", "segundosRestantes", 60));
    }
}
