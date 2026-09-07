package com.infra.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.amazon.awscdk.services.route53.CfnHealthCheckProps;
import software.amazon.awscdk.services.msk.*;

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
        CfnCluster kafkaService=createMskCluster();

        this.ecsCluster = createEcsCluster();
    }

    private FargateService createFargateService(String id,String imageName,List<Integer>ports,
                                                DatabaseInstance db,Map<String,String>envVars){
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, id + "TaskDef")
                .cpu(254)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions containerOptions = ContainerDefinitionOptions.builder()
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
                                .logGroupName("ecs/" + imageName)
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
//                        .streamPrefix(id + "Log")
                        .build()))
                .environment(envVars)
                .build();

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

    private CfnCluster createMskCluster() {
        SecurityGroup mskSg = SecurityGroup.Builder.create(this, "KafkaSecurityGroup")
                .vpc(this.vpc)
                .description("Allow Kafka broker access from ECS microservices")
                .allowAllOutbound(true)
                .build();
        mskSg.addIngressRule(Peer.ipv4(this.vpc.getVpcCidrBlock()), Port.tcp(9092), "Plaintext traffic from VPC");
        mskSg.addIngressRule(Peer.ipv4(this.vpc.getVpcCidrBlock()), Port.tcp(9094), "TLS traffic from VPC");

        List<String> privateSubnetIds = this.vpc.getPrivateSubnets().stream()
                .map(ISubnet::getSubnetId)
                .toList();

        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("PatientManagementMSK")
                .kafkaVersion("3.5.1")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.t3.small")
                        .clientSubnets(privateSubnetIds)
                        .securityGroups(List.of(mskSg.getSecurityGroupId()))
                        .build())
                .build();
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
