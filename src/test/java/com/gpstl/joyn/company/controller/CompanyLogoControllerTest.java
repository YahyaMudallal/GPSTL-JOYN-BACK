package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration des logos d'entreprise (R84), sur la vraie base et avec de vrais tokens.
 * Les tests tournent en stockage local (src/test/resources/application.properties : joyn.storage.provider=local).
 *
 * Données créées avant chaque test :
 *  - entreprise "Acme" (recruteuse Rita) et entreprise "Rival" (recruteur Ravi) ;
 *  - une candidate Lina et un administrateur Adam.
 * Tout est supprimé après chaque test (suffixe unique "run"), y compris les fichiers des logos.
 */
@SpringBootTest
class CompanyLogoControllerTest {

    private static final String KEY_PATTERN =
            "^company_logos/[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.webp$";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    @Value("${joyn.media.local-directory:./media}")
    private String mediaDirectory;

    private MockMvc mockMvc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private long acmeId;
    private long rivalId;
    private long ritaUserId;
    private long raviUserId;
    private long linaUserId;
    private long adamUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        acmeId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Acme " + run);
        rivalId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Rival " + run);

        ritaUserId = insertUser("rita", "RECRUITER");
        insertRecruiter(ritaUserId, acmeId, "Rita");
        raviUserId = insertUser("ravi", "RECRUITER");
        insertRecruiter(raviUserId, rivalId, "Ravi");
        linaUserId = insertUser("lina", "CANDIDATE");
        adamUserId = insertUser("adam", "ADMIN");
    }

    @AfterEach
    void tearDown() throws IOException {
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%-" + run + "@logo.test");
        jdbc.update("DELETE FROM companies WHERE id IN (?, ?)", acmeId, rivalId);
        for (long companyId : new long[]{acmeId, rivalId}) {
            FileSystemUtils.deleteRecursively(logoFolder(companyId));
        }
    }

    // ================================================================ sécurité et droits

    @Test
    void uploadWithoutTokenIsRejected() throws Exception {
        upload(acmeId, null, logoPng(200, 200)).andExpect(status().isUnauthorized());
    }

    @Test
    void candidateCannotChangeALogo() throws Exception {
        upload(acmeId, bearer(linaUserId, "CANDIDATE"), logoPng(200, 200)).andExpect(status().isForbidden());
        assertThat(storedKey(acmeId)).isNull();
    }

    @Test
    void recruiterCannotChangeAnotherCompanyLogo() throws Exception {
        upload(acmeId, bearer(raviUserId, "RECRUITER"), logoPng(200, 200))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Vous ne pouvez modifier que le logo de votre entreprise."));
        mockMvc.perform(delete(url(acmeId)).header("Authorization", bearer(raviUserId, "RECRUITER")))
                .andExpect(status().isForbidden());
        assertThat(storedKey(acmeId)).isNull();
    }

    @Test
    void unknownCompanyGives404() throws Exception {
        upload(999_999_999L, bearer(adamUserId, "ADMIN"), logoPng(200, 200)).andExpect(status().isNotFound());
        mockMvc.perform(get(url(999_999_999L))).andExpect(status().isNotFound());
    }

    // ================================================================ envoi

    @Test
    void recruiterUploadsLogoOfOwnCompany() throws Exception {
        String json = upload(acmeId, bearer(ritaUserId, "RECRUITER"), logoPng(800, 400))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_id").value(acmeId))
                .andExpect(jsonPath("$.logo_url").value(startsWith("/api/v1/companies/logos/" + acmeId + "/")))
                .andExpect(jsonPath("$.logo_url").value(endsWith(".webp")))
                .andReturn().getResponse().getContentAsString();

        // La base garde la clé de stockage (pas l'URL), comme pour les photos de profil (U86).
        String key = storedKey(acmeId);
        assertThat(key).matches(KEY_PATTERN);

        // L'URL renvoyée sert bien le fichier, en WebP, avec les proportions gardées (800x400 -> 512x256).
        String logoUrl = JsonPath.read(json, "$.logoUrl");
        byte[] served = mockMvc.perform(get(logoUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(image.getHeight()).isEqualTo(256);
    }

    @Test
    void acceptsTheLogoFieldNameAndThePostAlias() throws Exception {
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", pngBytes(100, 100));
        mockMvc.perform(multipart(url(acmeId)).file(logo).header("Authorization", bearer(ritaUserId, "RECRUITER")))
                .andExpect(status().isOk());
        assertThat(storedKey(acmeId)).matches(KEY_PATTERN);
    }

    @Test
    void replacingTheLogoDeletesTheOldFile() throws Exception {
        String token = bearer(ritaUserId, "RECRUITER");
        upload(acmeId, token, logoPng(200, 200)).andExpect(status().isOk());
        String firstKey = storedKey(acmeId);
        assertThat(fileOf(firstKey)).exists();

        upload(acmeId, token, logoPng(300, 150)).andExpect(status().isOk());
        String secondKey = storedKey(acmeId);

        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(fileOf(secondKey)).exists();
        assertThat(fileOf(firstKey)).doesNotExist();
    }

    @Test
    void invalidFilesAreRejectedWith400() throws Exception {
        String token = bearer(ritaUserId, "RECRUITER");

        // Pas d'image du tout
        mockMvc.perform(multipart(HttpMethod.PUT, url(acmeId)).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // Du texte déguisé en .png
        MockMultipartFile fake = new MockMultipartFile("file", "logo.png", "image/png",
                "pas une image".getBytes(StandardCharsets.UTF_8));
        upload(acmeId, token, fake).andExpect(status().isBadRequest());

        // Un SVG
        MockMultipartFile svg = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"><script>alert(1)</script></svg>"
                        .getBytes(StandardCharsets.UTF_8));
        upload(acmeId, token, svg).andExpect(status().isBadRequest());

        // Trop petit
        upload(acmeId, token, logoPng(20, 20)).andExpect(status().isBadRequest());

        assertThat(storedKey(acmeId)).isNull();
    }

    // ================================================================ lecture

    @Test
    void anyoneCanReadTheLogoAndItIsNullBeforeUpload() throws Exception {
        mockMvc.perform(get(url(acmeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_id").value(acmeId))
                .andExpect(jsonPath("$.logo_url").value(nullValue()));

        upload(acmeId, bearer(ritaUserId, "RECRUITER"), logoPng(200, 200)).andExpect(status().isOk());

        mockMvc.perform(get(url(acmeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo_url").value(startsWith("/api/v1/companies/logos/" + acmeId + "/")));
    }

    @Test
    void companyProfileAndAdminListReturnAnUrlAndNotTheStorageKey() throws Exception {
        upload(acmeId, bearer(ritaUserId, "RECRUITER"), logoPng(200, 200)).andExpect(status().isOk());

        // Profil de l'entreprise (A64) vu par sa recruteuse
        mockMvc.perform(get("/api/v1/companies/" + acmeId).param("include_jobs", "false")
                        .header("Authorization", bearer(ritaUserId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(startsWith("/api/v1/companies/logos/" + acmeId + "/")));

        // Liste des entreprises du back-office
        mockMvc.perform(get("/api/v1/admin/companies").param("search", "Acme " + run)
                        .header("Authorization", bearer(adamUserId, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(acmeId))
                .andExpect(jsonPath("$.data[0].logo_url").value(startsWith("/api/v1/companies/logos/" + acmeId + "/")));
    }

    // ================================================================ suppression

    @Test
    void adminDeletesTheLogo() throws Exception {
        upload(acmeId, bearer(ritaUserId, "RECRUITER"), logoPng(200, 200)).andExpect(status().isOk());
        String key = storedKey(acmeId);

        mockMvc.perform(delete(url(acmeId)).header("Authorization", bearer(adamUserId, "ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(storedKey(acmeId)).isNull();
        assertThat(fileOf(key)).doesNotExist();
        mockMvc.perform(get(url(acmeId))).andExpect(jsonPath("$.logo_url").value(nullValue()));
    }

    @Test
    void adminCanUploadForAnyCompany() throws Exception {
        upload(rivalId, bearer(adamUserId, "ADMIN"), logoPng(200, 200)).andExpect(status().isOk());
        assertThat(storedKey(rivalId)).matches(KEY_PATTERN);
    }

    // ================================================================ outils

    private ResultActions upload(long companyId, String token, MockMultipartFile file) throws Exception {
        var request = multipart(HttpMethod.PUT, url(companyId)).file(file);
        if (token != null) {
            request.header("Authorization", token);
        }
        return mockMvc.perform(request);
    }

    private static String url(long companyId) {
        return "/api/v1/companies/" + companyId + "/logo";
    }

    private String storedKey(long companyId) {
        return jdbc.queryForObject("SELECT logo_url FROM companies WHERE id = ?", String.class, companyId);
    }

    private Path logoFolder(long companyId) {
        return Path.of(mediaDirectory).toAbsolutePath().normalize()
                .resolve("company-logos").resolve("company_logos").resolve(String.valueOf(companyId));
    }

    /** Même rangement que LocalCompanyLogoStorage (et que les photos U86) : <media>/company-logos/<clé>. */
    private Path fileOf(String key) {
        return Path.of(mediaDirectory).toAbsolutePath().normalize().resolve("company-logos").resolve(key);
    }

    private static MockMultipartFile logoPng(int width, int height) throws IOException {
        return new MockMultipartFile("file", "logo.png", "image/png", pngBytes(width, height));
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private long insertUser(String name, String role) {
        return id("INSERT INTO users (email, password_hash, role) VALUES (?, 'not-a-real-hash', ?) RETURNING id",
                name + "-" + run + "@logo.test", role);
    }

    private void insertRecruiter(long userId, long companyId, String firstName) {
        jdbc.update("""
                INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name)
                VALUES (?, ?, ?, 'Test')""", userId, companyId, firstName);
    }

    private long id(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql, Long.class, args);
        return id == null ? 0 : id;
    }

    /** Token signé comme ceux de la route de connexion (U16). */
    private String bearer(long userId, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(jwtSecret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
