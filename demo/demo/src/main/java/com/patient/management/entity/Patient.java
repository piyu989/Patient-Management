package com.patient.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@ToString
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private String id;

    @NotNull
    private String name;
    @NotNull(message = "can not be blank")
    @Column(unique = true,comment = "must ne unique")
    private String email;

    @NotNull
    private String dateOfBirth;
    @NotNull
    private String address;
    @NotNull
    private String registeredDate;
}
