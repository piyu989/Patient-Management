package com.patient.billing.grpc;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.stub.StreamObserver;

@Slf4j
@GrpcService
public class BillingGrpcService extends BillingServiceGrpc.BillingServiceImplBase {

    @Override
    public void createBillingAccount(BillingRequest request, StreamObserver<BillingResponse> responseObserver) {
        log.info("Received createBillingAccount request: {}", request);

        BillingResponse response = BillingResponse.newBuilder()
                .setPatientId("12345")
                .setStatus("SUCCESS")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
