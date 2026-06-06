package com.subastar.subastar.controller;

import com.subastar.subastar.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;

    @PostMapping("/registros/{id}/aprobar")
    public ResponseEntity<Map<String, String>> aprobarRegistro(@PathVariable Integer id) {
        authService.aprobarRegistro(id);
        return ResponseEntity.ok(Map.of("message", "Registro aprobado. Se envió el código al usuario por email."));
    }
}
