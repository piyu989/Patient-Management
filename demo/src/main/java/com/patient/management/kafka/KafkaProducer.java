package com.patient.management.kafka;

import com.patient.management.entity.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
public class KafkaProducer {
    private final KafkaTemplate<String,byte[]> kafkaTemplate;

    @Autowired
    KafkaProducer(KafkaTemplate<String,byte[]> kafkaTemplate){
        this.kafkaTemplate=kafkaTemplate;
    }

    public void sendEvent(Patient patient){
        PatientEvent patientEvent = PatientEvent.newBuilder()
                .setPatientId(patient.getId())
                .setName(patient.getName())
                .setEmail(patient.getEmail())
                .setEventType("PATIENT_CREATED")
                .build();

        try{
            kafkaTemplate.send("patient-events", patientEvent.toByteArray());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
