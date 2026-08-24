package io.radius.listing.service;

import io.radius.listing.api.Dtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Photos never pass through this service. The client asks for a pre-signed PUT,
 * uploads straight to the object store, then attaches the key. That keeps large
 * multipart bodies off the JVM heap and out of the gateway.
 */
@Service
public class PhotoService {

    private static final Duration EXPIRY = Duration.ofMinutes(10);

    private final S3Presigner presigner;
    private final String bucket;
    private final String publicEndpoint;

    public PhotoService(S3Presigner presigner,
                        @Value("${radius.s3.bucket}") String bucket,
                        @Value("${radius.s3.public-endpoint}") String publicEndpoint) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint.replaceAll("/+$", "");
    }

    public Dtos.PresignResponse presign(UUID ownerId, Dtos.PresignRequest req) {
        String key = "listings/%s/%s.%s".formatted(ownerId, UUID.randomUUID(), extension(req.contentType()));

        var put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(req.contentType())
                .build();

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(EXPIRY)
                .putObjectRequest(put)
                .build());

        return new Dtos.PresignResponse(presigned.url().toString(), key, publicUrl(key),
                (int) EXPIRY.toSeconds());
    }

    public String publicUrl(String objectKey) {
        return "%s/%s/%s".formatted(publicEndpoint, bucket, objectKey);
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
