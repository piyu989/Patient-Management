package com.patient.management.repository;

import com.patient.management.entity.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String>{

    Patient findByEmail(String email);

}
