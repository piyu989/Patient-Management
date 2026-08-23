package com.patient.management.service;

import com.patient.management.dto.PatientRequestDto;
import com.patient.management.dto.PatientResponseDto;
import com.patient.management.entity.Patient;
import com.patient.management.mapper.PatientMapper;
import com.patient.management.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    @Autowired
    PatientRepository patientRepository ;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<PatientResponseDto> findAllPatients() {
        List<Patient> all = patientRepository.findAll();
        List<PatientResponseDto> patientResponseDtos = all.stream().map(PatientMapper::toDto).toList();
        return patientResponseDtos;
    }

    public Patient savePatient(PatientRequestDto patientResponseDto) {
        Patient patient = PatientMapper.toEntity(patientResponseDto);
        return patientRepository.save(patient);
    }

    public boolean findByEmail(String email){
        Patient byEmail = patientRepository.findByEmail(email);
        return byEmail != null;
    }

    public void deleteById(UUID id){
        patientRepository.deleteById(String.valueOf(id));
    }


}
