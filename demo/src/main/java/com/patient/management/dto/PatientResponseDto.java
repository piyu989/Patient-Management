package com.patient.management.dto;

import lombok.Data;

@Data
public class PatientResponseDto {
    private String id;
    private String name;
    private String address;
    private String email;
    private String dateOfBirth;
}
