package com.patient.management.mapper;

import com.patient.management.dto.PatientRequestDto;
import com.patient.management.dto.PatientResponseDto;
import com.patient.management.entity.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PatientMapper {
    public static PatientResponseDto toDto(Patient patient) {
        PatientResponseDto dto = new PatientResponseDto();
        dto.setId(patient.getId());
        dto.setName(patient.getName());
        dto.setAddress(patient.getAddress());
        dto.setEmail(patient.getEmail());
        dto.setDateOfBirth(patient.getDateOfBirth().toString());
        return dto;
    }

    public static Patient toEntity(PatientRequestDto patient) {
        Patient dto = new Patient();
        dto.setId(patient.getId());
        dto.setName(patient.getName());
        dto.setAddress(patient.getAddress());
        dto.setEmail(patient.getEmail());
        dto.setDateOfBirth(patient.getDateOfBirth());
        dto.setRegisteredDate(LocalDateTime.now().toString());
        return dto;
    }
}
