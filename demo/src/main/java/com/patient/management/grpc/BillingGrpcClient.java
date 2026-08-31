package com.patient.management.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BillingGrpcClient {
    private BillingServiceGrpc.BillingServiceBlockingStub billingServiceBlockingStub;

    public BillingGrpcClient(@Value("${billing.grpc.server.address}") String serverAddress,
                             @Value("${billing.grpc.server.port}") int serverPort){
        // Initialize the gRPC channel and stub here)) {
        log.info("Initializing gRPC client for Billing Service at {}:{}", serverAddress, serverPort);

        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext() // Use plaintext for simplicity; consider using TLS in production
                .build();

        billingServiceBlockingStub = BillingServiceGrpc.newBlockingStub(managedChannel);
    }

    public BillingResponse createBillingAccount(String patietId,String name,String email){
        BillingRequest build = BillingRequest.newBuilder().setEmail(email).setPatientId(patietId).setName(name).build();

        BillingResponse billingAccount = billingServiceBlockingStub.createBillingAccount(build);
        return billingAccount;
    }

}
