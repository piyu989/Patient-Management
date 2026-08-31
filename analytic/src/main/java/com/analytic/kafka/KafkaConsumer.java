package com.analytic.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
@Slf4j
public class KafkaConsumer {

    @KafkaListener(topics = "patient-events", groupId = "analytic-service")
    public void consume(byte[] arr) {
        try {
            PatientEvent patientEvent = PatientEvent.parseFrom(arr);
            log.info("consumed patient event from kafka message: {} ", patientEvent.toString());
        }catch (Exception e){
            log.error("exception while parsing patient event from kafka message: {} ", e.getMessage(), e);
        }
    }

}
