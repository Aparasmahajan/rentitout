package io.radius.listing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    /**
     * The presigner uses the endpoint the browser will talk to — the signature
     * covers the host, so signing against the internal name would fail from a
     * phone. Path-style addressing keeps MinIO happy.
     */
    @Bean
    public S3Presigner s3Presigner(@Value("${radius.s3.public-endpoint}") String endpoint,
                                   @Value("${radius.s3.region}") String region,
                                   @Value("${radius.s3.access-key}") String accessKey,
                                   @Value("${radius.s3.secret-key}") String secretKey) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
