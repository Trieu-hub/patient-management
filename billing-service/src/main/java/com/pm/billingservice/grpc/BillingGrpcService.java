package com.pm.billingservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;

@GrpcService
public class BillingGrpcService extends BillingServiceImplBase{
    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    @Override
    public void createBillingAccount(BillingRequest billingRequest, StreamObserver<BillingResponse> responseObserver){
        log.info("createBillingAccount request received {}", billingRequest.toString());

        //Save to db and perform calculates
        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId("12345").setStatus("ACTIVE").build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    @Bean
    public GrpcServerConfigurer grpcServerConfigurer() {
        return serverBuilder ->
                serverBuilder.addService(ProtoReflectionService.newInstance());
    }
}
