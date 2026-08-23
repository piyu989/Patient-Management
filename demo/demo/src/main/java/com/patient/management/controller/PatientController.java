package com.patient.management.controller;

import com.patient.management.Exception.EmailAlreadyExistException;
import com.patient.management.dto.PatientRequestDto;
import com.patient.management.dto.PatientResponseDto;
import com.patient.management.entity.Patient;
import com.patient.management.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patient",description = "API for managing Patient")
public class PatientController {
    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping("/all")
    @Operation(summary = "Get All Patient")
    public ResponseEntity<List<PatientResponseDto>> getAllPatient(){
        List<PatientResponseDto> allPatients = patientService.findAllPatients();
        return ResponseEntity.ok(allPatients);
    }

    @PostMapping("/add")
    @Operation(summary = "Add a New Patient")
    public ResponseEntity<Patient> savePatient(@Valid @RequestBody PatientRequestDto patientResponseDto) throws EmailAlreadyExistException {
        boolean byEmail = patientService.findByEmail(patientResponseDto.getEmail());
        if(byEmail){
            throw new EmailAlreadyExistException("email found");
        }
        Patient savedPatient = patientService.savePatient(patientResponseDto);
        return ResponseEntity.ok(savedPatient);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Delete a Patient")
    public ResponseEntity<Void> deletePatient(@Valid @PathVariable UUID id) throws EmailAlreadyExistException {
        patientService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
