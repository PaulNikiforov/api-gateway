package com.innowise.apigateway.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
@Testcontainers
class SystemE2ETest {

    private static final String GATEWAY_BASE_URL = "http://localhost:8090";

    private static final String TEST_EMAIL = "e2e_golden@example.com";
    private static final String TEST_PASSWORD = "Password1!";

    private static final String TEST_JWT_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEuwIBADANBgkqhkiG9w0BAQEFAASCBKUwggShAgEAAoIBAQDiSu58gZkJegfs
            15ghGEs6mDevoZ57aDxvi4qSmyfpwYz/OQSfsVorDsXQsU8sUd7uv8OnqyrJQgxu
            NrhBCivz6Ovy/uNXo9N9RfMOXzom6rUDIEDJKUMNcsJ8rpLRvken/THS8iLkK+qf
            1AwmlHpnUth2oW25OSIqB7JU8QJTlG+15jHksxrI9PCeDuKQNv50EDjxRDW7GK6D
            Ge8dipWT8y80XkJyApWZjDwruX302arU5qHEfuKUlnvs+gXr6JT55ZbCHZDmLCEN
            EtAJIwhgYn7uBLO3vw4DKVVb5cqiZTkxls140GSoI/S2ncI+miYoRDWUX62FGqHK
            VmulgBR1AgMBAAECggEAOA7N84P7UFCto+tooVIuWK6apOSJqRKSXiOYSWcsRQkQ
            j60lSxYZOy9mq6Mw9M63Rje1FVUevUqiX68oh9woNT0PLlwcH3rTCmaIppfKhReB
            jeuwgOS62psCOPbaIiFcCO59KD+ZiyKh9cQG2ovPosbwHrswvkC8CONtNwOZSvC0
            UjW2333p8uycF4PgYhObPs1XFWdyQWH2hf7pVnwLs9p6HATeAUBl0P6RaaRXwyGA
            CNKd61WLAButeYYAP2v1+Iw69rYTJji7+JvYcDr1ieFD7wngf4BQgG5EKWiVcxlW
            uv0ESgAeu1q0FUdOHDO4uLWqpnqLYFdBhkWICIRNAQKBgQD1V11WXbI7cErTGbSw
            B0ij8rA0FsJ1JCGL/oIYo9h0PvB8SADoS077pqYPANd/H749LJMkpsmWtxNv4Ym+
            ddDny/5wSQ8B7kvXjApBbiLrN7orvjsMsX/LkL6WgZkOIDm7NNv4AfzKTWuSouEk
            a69DT3/LtALhR80fisAw4t/IwQKBgQDsH7Z1XFJbSd1s6K/qgWzr4jMYuct5lljS
            UgxskYwgGo3jHpZezQ1Gcu63a+pguyHoFAuq8JZmmSUrJw6tJSJcfAAqqw7QHl5e
            Q/8wm9yHPEK/cp3/Ph4VMhXm9PfXbh2mcZv0du41XbWZ2afX6Bznmhz+qi3fksX3
            OdCRXAwktQKBgHinnGlq7so4cTPcAnZHPrwSEAGt57gAKtdUNNq1SS/x/AbCyl9z
            Gca8sBHU0iXckIw5Lavqsl0Cb/anrjwSaMh2FA1YgJ7seDPq1OhUp6uR3mbAyP13
            FWghKPmPhpvh0UJ1vm/7WjyLUonsvFhS9QBfSnP9dSUhUIlgjR/9kxyBAn9T7vHs
            xeSAjsEm9Y+SzG7rany/TUwG7GqmWIQSE6q7vrSxBy5shHczk6dHjBTETcC/vmBn
            Yx4TWlzb+gY9hfWw6mMkx6l8UU86MvGDVeQOLl2LsDJ5iJso7aTbdDilW38uqzPE
            soH6dlUXW0dOeDPOH/oujE/CKWo1d1esVAv9AoGBAJ673UlYEslZA+18bP7t+AxN
            MYtbCrOVR07xlWzPPGA3GJ0M2630Mf/YNg4/T38DbjZqVWwvBmdBvY6WpJLA91yK
            KR64GvwSEdkJic7AAMJYSg+6RMz5oTShwLZ4UIx+8dFgG5E4ghH8LhruN8na/hIH
            gdl+pDMIgvEu9J6XEHO/
            -----END PRIVATE KEY-----""";

    private static final String TEST_JWT_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4krufIGZCXoH7NeYIRhL
            Opg3r6Gee2g8b4uKkpsn6cGM/zkEn7FaKw7F0LFPLFHe7r/Dp6sqyUIMbja4QQor
            8+jr8v7jV6PTfUXzDl86Juq1AyBAySlDDXLCfK6S0b5Hp/0x0vIi5Cvqn9QMJpR6
            Z1LYdqFtuTkiKgeyVPECU5RvteYx5LMayPTwng7ikDb+dBA48UQ1uxiugxnvHYqV
            k/MvNF5CcgKVmYw8K7l99Nmq1OahxH7ilJZ77PoF6+iU+eWWwh2Q5iwhDRLQCSMI
            YGJ+7gSzt78OAylVW+XKomU5MZbNeNBkqCP0tp3CPpomKEQ1lF+thRqhylZrpYAU
            dQIDAQAB
            -----END PUBLIC KEY-----""";

    @Container
    static final DockerComposeContainer<?> ENVIRONMENT = new DockerComposeContainer<>(
            new File("../compose.yaml")
    )
            .withLocalCompose(true)
            .withEnv("JWT_PRIVATE_KEY", TEST_JWT_PRIVATE_KEY)
            .withEnv("JWT_PUBLIC_KEY", TEST_JWT_PUBLIC_KEY);

    static WebTestClient client;
    static long registeredUserId;
    static String accessToken;

    @BeforeAll
    static void setUpGoldenPathUser() throws Exception {
        client = WebTestClient.bindToServer()
                .baseUrl(GATEWAY_BASE_URL)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        waitForGatewayHealth();

        Map<String, Object> body = client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "Golden",
                        "surname", "Path",
                        "birthDate", "1990-01-01",
                        "email", TEST_EMAIL,
                        "password", TEST_PASSWORD
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        registeredUserId = ((Number) body.get("userId")).longValue();
        accessToken = (String) body.get("accessToken");
    }

    @Test
    void register_createsUserAndReturnsTokens() {
        String uniqueEmail = "e2e_reg_" + System.nanoTime() + "@example.com";

        Map<String, Object> body = client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "New",
                        "surname", "User",
                        "birthDate", "1995-06-15",
                        "email", uniqueEmail,
                        "password", TEST_PASSWORD
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.get("userId")).isNotNull();
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("refreshToken")).isNotNull();
    }

    @Test
    void login_withValidCredentials_returnsTokens() {
        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", TEST_EMAIL, "password", TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty();
    }

    @Test
    void getUser_withValidJwtToken_returnsUserData() {
        client.get()
                .uri("/api/v1/users/" + registeredUserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(registeredUserId)
                .jsonPath("$.email").isEqualTo(TEST_EMAIL);
    }

    private static void waitForGatewayHealth() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY_BASE_URL + "/actuator/health"))
                .GET().build();

        for (int attempt = 0; attempt < 90; attempt++) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) return;
            } catch (Exception ignored) {}
            Thread.sleep(10_000);
        }
        throw new RuntimeException("API Gateway did not become healthy within 15 minutes");
    }
}
