package com.example.demo.integration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.*;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Configuration {

    private final String s3Url;
    private final String s3Region;
    private final String s3Protocol;
    private final String s3AccessKey;
    private final String s3SecretKey;

    public S3Configuration(
            @Value("${aws.s3.url}") String s3Url,
            @Value("${aws.s3.region}") String s3Region,
            @Value("${aws.s3.protocol}")String s3Protocol,
            @Value("${aws.s3.accessKey}") String s3AccessKey,
            @Value("${aws.s3.secretKey}") String s3SecretKey) {
        this.s3Url = s3Url;
        this.s3Region = s3Region;
        this.s3Protocol = s3Protocol;
        this.s3AccessKey = s3AccessKey;
        this.s3SecretKey = s3SecretKey;
    }

    @Bean
    public AmazonS3 amazonS3() {
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(s3Url, s3Region);
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setProtocol(Protocol.valueOf(s3Protocol));
        AWSCredentials credentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
        return AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(endpointConfiguration)
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(credentialsProvider)
                .build();
    }

    @Bean
    public TransferManager s3TransferManager(AmazonS3 amazonS3) {
        return TransferManagerBuilder.standard().withS3Client(amazonS3).build();
    }

}
