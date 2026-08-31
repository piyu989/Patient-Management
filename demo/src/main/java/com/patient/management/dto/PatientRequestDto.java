package com.patient.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientRequestDto {
    private String id;

    @NotBlank(message = "can not be blank")
    private String name;

    @NotBlank(message = "can not be blank")
    private String address;

    @NotBlank(message = "can not be blank")
    private String email;

    @NotBlank(message = "can not be blank")
    private String dateOfBirth;
}
