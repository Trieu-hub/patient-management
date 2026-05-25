package com.pm.stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;

public class LocalStack extends Stack {
  private final Vpc vpc;
  private final Cluster ecsCluster;

  // LocalStack Free does not support RDS or MSK.
  // Databases run via docker-compose and are reachable on the host ports below.
  private static final String AUTH_DB_URL =
      "jdbc:postgresql://host.docker.internal:5433/db";
  private static final String PATIENT_DB_URL =
      "jdbc:postgresql://host.docker.internal:5434/db";
  private static final String LOCAL_DB_PASSWORD = "password";
  // docker-compose bitnami/kafka exposes the EXTERNAL listener on host port 9094
  private static final String KAFKA_BOOTSTRAP = "host.docker.internal:9094";

  public LocalStack(final App scope, final String id, final StackProps props){
    super(scope, id, props);

    this.vpc = createVpc();
    this.ecsCluster = createEcsCluster();

    FargateService authService =
        createFargateService("AuthService",
            "auth-service",
            List.of(4005),
            AUTH_DB_URL,
            Map.of("JWT_SECRET", "c3VwZXItc2VjcmV0LWtleS1mb3Itand0LWF1dGgtMjAyNi1sb25nLWVub3VnaA=="));

    FargateService billingService =
        createFargateService("BillingService",
            "billing-service",
            List.of(4001, 9001),
            null,
            null);

    FargateService analyticsService =
        createFargateService("AnalyticsService",
            "analytics-service",
            List.of(4002),
            null,
            null);

    FargateService patientService = createFargateService("PatientService",
        "patient-service",
        List.of(4000),
        PATIENT_DB_URL,
        Map.of(
            "BILLING_SERVICE_ADDRESS", "host.docker.internal",
            "BILLING_SERVICE_GRPC_PORT", "9001"
        ));
    patientService.getNode().addDependency(billingService);

    createApiGatewayService();
  }

  private Vpc createVpc(){
    return Vpc.Builder
        .create(this, "PatientManagementVPC")
        .vpcName("PatientManagementVPC")
        .maxAzs(2)
        .build();
  }

  private Cluster createEcsCluster(){
    // defaultCloudMapNamespace (AWS::ServiceDiscovery::PrivateDnsNamespace) is Pro-only in LocalStack
    return Cluster.Builder.create(this, "PatientManagementCluster")
        .vpc(vpc)
        .build();
  }

  private FargateService createFargateService(String id,
      String imageName,
      List<Integer> ports,
      String dbUrl,
      Map<String, String> additionalEnvVars) {

    FargateTaskDefinition taskDefinition =
        FargateTaskDefinition.Builder.create(this, id + "Task")
            .cpu(256)
            .memoryLimitMiB(512)
            .build();

    ContainerDefinitionOptions.Builder containerOptions =
        ContainerDefinitionOptions.builder()
            .image(ContainerImage.fromRegistry(imageName))
            .portMappings(ports.stream()
                .map(port -> PortMapping.builder()
                    .containerPort(port)
                    .hostPort(port)
                    .protocol(Protocol.TCP)
                    .build())
                .toList())
            .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                    .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                        .logGroupName("/ecs/" + imageName)
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .retention(RetentionDays.ONE_DAY)
                        .build())
                    .streamPrefix(imageName)
                .build()));

    Map<String, String> envVars = new HashMap<>();
    envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP);

    if(additionalEnvVars != null){
      envVars.putAll(additionalEnvVars);
    }

    if(dbUrl != null){
      envVars.put("SPRING_DATASOURCE_URL", dbUrl);
      envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
      envVars.put("SPRING_DATASOURCE_PASSWORD", LOCAL_DB_PASSWORD);
      envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
      envVars.put("SPRING_SQL_INIT_MODE", "always");
      envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
    }

    containerOptions.environment(envVars);
    taskDefinition.addContainer(imageName + "Container", containerOptions.build());

    return FargateService.Builder.create(this, id)
        .cluster(ecsCluster)
        .taskDefinition(taskDefinition)
        .assignPublicIp(false)
        .serviceName(imageName)
        .build();
  }

  private void createApiGatewayService() {
    FargateTaskDefinition taskDefinition =
        FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
            .cpu(256)
            .memoryLimitMiB(512)
            .build();

    ContainerDefinitionOptions containerOptions =
        ContainerDefinitionOptions.builder()
            .image(ContainerImage.fromRegistry("api-gateway"))
            .environment(Map.of(
                "SPRING_PROFILES_ACTIVE", "prod",
                "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
            ))
            .portMappings(List.of(4004).stream()
                .map(port -> PortMapping.builder()
                    .containerPort(port)
                    .hostPort(port)
                    .protocol(Protocol.TCP)
                    .build())
                .toList())
            .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                    .logGroupName("/ecs/api-gateway")
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .retention(RetentionDays.ONE_DAY)
                    .build())
                .streamPrefix("api-gateway")
                .build()))
            .build();

    taskDefinition.addContainer("APIGatewayContainer", containerOptions);

    ApplicationLoadBalancedFargateService apiGateway =
        ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
            .cluster(ecsCluster)
            .serviceName("api-gateway")
            .taskDefinition(taskDefinition)
            .desiredCount(1)
            .healthCheckGracePeriod(Duration.seconds(60))
            .build();
  }

  public static void main(final String[] args) {
    App app = new App(AppProps.builder().outdir("./cdk.out").build());

    StackProps props = StackProps.builder()
        .synthesizer(new BootstraplessSynthesizer())
        .build();

    new LocalStack(app, "localstack", props);
    app.synth();
    System.out.println("App synthesizing in progress...");
  }
}
