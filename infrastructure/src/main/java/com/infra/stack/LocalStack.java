package com.infra.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.amazon.awscdk.services.route53.CfnHealthCheckProps;

import java.util.List;
import java.util.Map;

public class LocalStack extends Stack
{

    private final Vpc vpc;
    private final Cluster ecsCluster;
    public LocalStack(final App scope, final String id,final StackProps props)
    {
        super(scope, id, props);
        this.vpc=createVpc();

        DatabaseInstance mySqlInstance=createMySqlInstance();
        DatabaseInstance mssqlInstance=createMsSqlInstance();
        CfnHealthCheck mySqlHealthCheck=createMySqlHealthCheck(mySqlInstance);
        CfnHealthCheck msSqlHealthCheck=createMsSqlHealthCheck(mssqlInstance);
        FargateService kafkaService=createKafkaService();
        CfnHealthCheck kafkaHealthCheck=createKafkaHealthCheck(kafkaService);

        this.ecsCluster = createEcsCluster();
    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(this.vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .clusterName("EcsCluster")
                .build();
    }
    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createMySqlInstance() {
        // Security group allowing MySQL port (default 3306) within the VPC
        SecurityGroup mysqlSg = SecurityGroup.Builder.create(this, "MySqlSecurityGroup")
                .vpc(this.vpc)
                .description("Allow MySQL inbound traffic")
                .allowAllOutbound(true)
                .build();
        mysqlSg.addIngressRule(Peer.ipv4(this.vpc.getVpcCidrBlock()), Port.tcp(3306), "Allow MySQL from VPC");

        return DatabaseInstance.Builder.create(this, "MySqlInstance")
                .engine(DatabaseInstanceEngine.mysql(
                        MySqlInstanceEngineProps.builder()
                                .version(MysqlEngineVersion.of("8.0.36", "8.0"))
                                .build()))
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
                .vpc(this.vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .securityGroups(java.util.List.of(mysqlSg))
                .databaseName("patient")
                .credentials(Credentials.fromPassword(
                        "root",
                        SecretValue.unsafePlainText("Piyu@98911")
                ))
                .allocatedStorage(20)
                .deletionProtection(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private DatabaseInstance createMsSqlInstance() {
        // Security group allowing MSSQL port (1433) within the VPC
        SecurityGroup mssqlSg = SecurityGroup.Builder.create(this, "MsSqlSecurityGroup")
                .vpc(this.vpc)
                .description("Allow MSSQL inbound traffic")
                .allowAllOutbound(true)
                .build();
        mssqlSg.addIngressRule(Peer.ipv4(this.vpc.getVpcCidrBlock()), Port.tcp(1433), "Allow MSSQL from VPC");

        return DatabaseInstance.Builder.create(this, "MsSqlInstance")
                .engine(DatabaseInstanceEngine.sqlServerEx(
                        SqlServerExInstanceEngineProps.builder()
                                .version(SqlServerEngineVersion
                                        .of("16.00.4135.4.v1", "16.00")) // SQL Server 2022
                                .build()))
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.SMALL))
                .vpc(this.vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .securityGroups(java.util.List.of(mssqlSg))
                .credentials(Credentials.fromPassword(
                        "sa",
                        SecretValue.unsafePlainText("Piyu@98911")
                ))
                .allocatedStorage(20)
                .deletionProtection(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private FargateService createKafkaService() {
        Cluster cluster = Cluster.Builder.create(this, "KafkaCluster")
                .vpc(this.vpc)
                .clusterName("KafkaCluster")
                .build();

        // Security group allowing Kafka client traffic on 9092 and internal 29092
        SecurityGroup kafkaSg = SecurityGroup.Builder.create(this, "KafkaSecurityGroup")
                .vpc(this.vpc)
                .description("Allow Kafka inbound traffic")
                .allowAllOutbound(true)
                .build();
        kafkaSg.addIngressRule(Peer.ipv4(this.vpc.getVpcCidrBlock()), Port.tcp(9092), "Allow Kafka Plaintext from VPC");
        kafkaSg.addIngressRule(Peer.ipv4(this.vpc.getVpcCidrBlock()), Port.tcp(29092), "Allow Kafka Internal from VPC");

        // Task definition (1 vCPU, 2GB RAM is standard for cp-kafka)
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "KafkaTaskDef")
                .cpu(1024)
                .memoryLimitMiB(2048)
                .build();

        // Map all compose.yml environment variables
        Map<String, String> kafkaEnv = Map.ofEntries(
                Map.entry("KAFKA_NODE_ID", "1"),
                Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT"),
                Map.entry("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092"),
                Map.entry("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1"),
                Map.entry("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0"),
                Map.entry("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1"),
                Map.entry("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1"),
                Map.entry("KAFKA_PROCESS_ROLES", "broker,controller"),
                Map.entry("KAFKA_KEY_FORMATED", "1"),
                Map.entry("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:29093"),
                Map.entry("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093,PLAINTEXT_HOST://0.0.0.0:9092"),
                Map.entry("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT"),
                Map.entry("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER"),
                Map.entry("KAFKA_LOG_DIRS", "/tmp/kraft-combined-logs"),
                Map.entry("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
        );

        taskDefinition.addContainer("KafkaContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("confluentinc/cp-kafka:7.5.0"))
                .containerName("kafka")
                .environment(kafkaEnv)
                .portMappings(List.of(
                        PortMapping.builder().containerPort(9092).hostPort(9092).build(),
                        PortMapping.builder().containerPort(29092).hostPort(29092).build()
                ))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder().streamPrefix("kafka").build()))
                .build());

        return FargateService.Builder.create(this, "KafkaFargateService")
                .cluster(cluster)
                .taskDefinition(taskDefinition)
                .securityGroups(List.of(kafkaSg))
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .desiredCount(1)
                .serviceName("kafka-service")
                .build();
    }

    /**
     * Separate health check method using Route 53 to verify TCP connectivity on Kafka broker port 9092
     */
    private CfnHealthCheck createKafkaHealthCheck(FargateService service) {
        return new CfnHealthCheck(this, "KafkaHealthCheck", CfnHealthCheckProps.builder()
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(9092)
                        .fullyQualifiedDomainName(service.getServiceName() + ".local")
                        .requestInterval(10)
                        .failureThreshold(3)
                        .build())
                .build());
    }

    private CfnHealthCheck createMySqlHealthCheck(DatabaseInstance db) {
        return new CfnHealthCheck(this, "MySqlHealthCheck", CfnHealthCheckProps.builder()
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(3306)
                        .fullyQualifiedDomainName(db.getDbInstanceEndpointAddress())
                        .requestInterval(10)      // Matches interval: 10s
                        .failureThreshold(5)       // Matches retries: 5
                        .build())
                .build());
    }

    private CfnHealthCheck createMsSqlHealthCheck(DatabaseInstance db) {
        return new CfnHealthCheck(this, "MsSqlHealthCheck", CfnHealthCheckProps.builder()
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(1433)
                        .fullyQualifiedDomainName(db.getDbInstanceEndpointAddress())
                        .requestInterval(10)      // Matches interval: 10s
                        .failureThreshold(10)     // Matches retries: 10
                        .build())
                .build());
    }



    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps  stackProps = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "LocalStack", stackProps);
        app.synth();
        System.out.println("done");

    }

}
