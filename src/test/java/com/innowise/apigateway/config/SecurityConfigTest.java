package com.innowise.apigateway.config;

import com.innowise.apigateway.dto.ErrorResponse;
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
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityConfigTest {

    private static final String KEY_ID = "test-key-1";
    private static final MockWebServer mockWebServer = new MockWebServer();
    private static RSAKey rsaJwk;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startServer() throws IOException, NoSuchAlgorithmException {
        rsaJwk = generateRsaJwk();

        mockWebServer.start();
        mockWebServer.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                return new MockResponse()
                        .setBody(new JWKSet(rsaJwk.toPublicJWK()).toString())
                        .addHeader("Content-Type", "application/json");
            }
        });
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

    private static RSAKey generateRsaJwk() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(KEY_ID)
                .build();
    }

    private static String signToken(RSAKey signingKey, Date expiration) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .claim("role", "USER")
                .issueTime(new Date())
                .expirationTime(expiration)
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                claims);
        signedJwt.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
        return signedJwt.serialize();
    }

    @Test
    void register_whenNoAuthToken_shouldNotBeRejectedBySecurityFilterChain() {
        var result = webTestClient.post().uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .returnResult(Void.class);

        assertThat(result.getStatus().value()).isNotIn(401, 403);
    }

    @Test
    void register_whenNoAuthTokenAndTrailingSlash_shouldNotBeRejectedBySecurityFilterChain() {
        var result = webTestClient.post().uri("/api/v1/register/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .returnResult(Void.class);

        assertThat(result.getStatus().value()).isNotIn(401, 403);
    }

    @Test
    void protectedEndpoint_whenNoAuthToken_shouldReturn401WithErrorResponseBody() {
        webTestClient.get().uri("/api/v1/users/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo(401);
                    assertThat(body.message()).isNotBlank();
                    assertThat(body.path()).isEqualTo("/api/v1/users/1");
                });
    }

    @Test
    void protectedEndpoint_whenValidToken_shouldNotBeRejectedByAuthentication() throws Exception {
        String validToken = signToken(rsaJwk, new Date(System.currentTimeMillis() + 60_000));

        var result = webTestClient.get().uri("/api/v1/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .returnResult(Void.class);

        assertThat(result.getStatus().value()).isNotIn(401, 403);
    }

    @Test
    void protectedEndpoint_whenExpiredToken_shouldReturn401() throws Exception {
        String expiredToken = signToken(rsaJwk, new Date(System.currentTimeMillis() - 60_000));

        webTestClient.get().uri("/api/v1/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo(401));
    }

    @Test
    void protectedEndpoint_whenWrongSignature_shouldReturn401() throws Exception {
        RSAKey unpublishedKey = generateRsaJwk();
        String badToken = signToken(unpublishedKey, new Date(System.currentTimeMillis() + 60_000));

        webTestClient.get().uri("/api/v1/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + badToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> assertThat(body.status()).isEqualTo(401));
    }
}
