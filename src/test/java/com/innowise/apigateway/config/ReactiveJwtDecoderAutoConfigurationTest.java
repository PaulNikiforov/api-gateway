package com.innowise.apigateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ReactiveJwtDecoderAutoConfigurationTest {

    private static final String KEY_ID = "test-key-1";
    private static final MockWebServer mockWebServer = new MockWebServer();
    private static RSAKey rsaJwk;

    @Autowired
    private ReactiveJwtDecoder jwtDecoder;

    @BeforeAll
    static void startServer() throws IOException, NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(KEY_ID)
                .build();

        mockWebServer.start();
        mockWebServer.enqueue(new MockResponse()
                .setBody(new JWKSet(rsaJwk.toPublicJWK()).toString())
                .addHeader("Content-Type", "application/json"));
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void registerJwkSetUri(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> mockWebServer.url("/oauth2/jwks").toString());
    }

    private static String signValidToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .claim("role", "USER")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                claims);
        signedJwt.sign(new RSASSASigner(rsaJwk.toRSAPrivateKey()));
        return signedJwt.serialize();
    }

    @Test
    void jwtDecoder_whenTokenSignedWithPublishedKey_shouldDecodeSuccessfully() throws Exception {
        String validToken = signValidToken();

        StepVerifier.create(jwtDecoder.decode(validToken))
                .assertNext(jwt -> assertThat(jwt.getSubject()).isEqualTo("42"))
                .verifyComplete();
    }
}
