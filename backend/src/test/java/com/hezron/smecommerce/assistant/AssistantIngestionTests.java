package com.hezron.smecommerce.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.FakeEmbeddingModel;
import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Issue "Assistant part 1: ingestion". Done when: documents for tenant A are stored with their tenant id.
 * <p>
 * The fake embedding model stands in for Gemini (TestcontainersConfiguration), so these tests need no key.
 * Indexing runs after the request, so the tests wait for each document to become READY.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AssistantIngestionTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeEmbeddingModel embeddings;

    @Autowired
    JwtEncoder jwtEncoder;

    private Shop shop;

    @BeforeEach
    void aShop() throws Exception {
        shop = signUp();
    }

    @Test
    void eachShopsProductsAreStoredWithThatShopsId() throws Exception {
        Shop otherShop = signUp();
        String ours = createProduct(shop, "Sukuma bundle", "Fresh sukuma wiki, tied in bundles.", 5000, "ACTIVE");
        String theirs = createProduct(otherShop, "Avocado", "Hass avocados from Murang'a.", 3000, "ACTIVE");

        String ourDocument = waitForProductDocument(shop, ours, "READY");
        String theirDocument = waitForProductDocument(otherShop, theirs, "READY");

        // Done-when: every chunk carries the id of the shop (and the document) it came from.
        List<Map<String, Object>> ourChunks = chunksOf(ourDocument);
        assertThat(ourChunks).isNotEmpty();
        for (Map<String, Object> chunk : ourChunks) {
            assertThat(chunk.get("tenant_id")).isEqualTo(shop.tenantId());
            assertThat(chunk.get("document_id")).isEqualTo(ourDocument);
        }
        assertThat(ourChunks.get(0).get("content")).isEqualTo(
                "Sukuma bundle costs KES 50.00. Fresh sukuma wiki, tied in bundles.");
        for (Map<String, Object> chunk : chunksOf(theirDocument)) {
            assertThat(chunk.get("tenant_id")).isEqualTo(otherShop.tenantId());
        }
        // And no chunk anywhere mixes them up.
        Integer mixed = jdbc.queryForObject("""
                SELECT count(*) FROM assistant.vector_store v JOIN assistant.documents d
                  ON d.id::text = v.metadata ->> 'document_id'
                WHERE d.tenant_id::text <> v.metadata ->> 'tenant_id'
                """, Integer.class);
        assertThat(mixed).isZero();
    }

    @Test
    void editingAProductReplacesItsChunks() throws Exception {
        String productId = createProduct(shop, "Sukuma bundle", "Fresh sukuma wiki.", 5000, "ACTIVE");
        String documentId = waitForProductDocument(shop, productId, "READY");

        updateProduct(productId, "Sukuma bundle", "Now with free delivery in Kilimani.", 5500, "ACTIVE");

        waitUntil(() -> {
            List<Map<String, Object>> chunks = chunksOf(documentId);
            return chunks.size() == 1 && chunks.get(0).get("content").toString().contains("Kilimani");
        }, "the chunks never showed the new description");
        List<Map<String, Object>> chunks = chunksOf(documentId);
        assertThat(chunks.get(0).get("content")).isEqualTo(
                "Sukuma bundle costs KES 55.00. Now with free delivery in Kilimani.");
        assertThat(documentStatus(documentId)).isEqualTo("READY");
    }

    @Test
    void savingAProductWithTheSameTextCostsNoEmbedding() throws Exception {
        String productId = createProduct(shop, "Sukuma bundle", "Fresh sukuma wiki.", 5000, "ACTIVE");
        waitForProductDocument(shop, productId, "READY");
        int before = embeddings.textsEmbedded();

        // Same name, description and price: catalog still announces a change (e.g. a new image).
        updateProduct(productId, "Sukuma bundle", "Fresh sukuma wiki.", 5000, "ACTIVE");
        Thread.sleep(1500);

        assertThat(embeddings.textsEmbedded()).isEqualTo(before);
    }

    @Test
    void aDraftIsNotKnownUntilItGoesOnSaleAndAnArchivedProductIsForgotten() throws Exception {
        String productId = createProduct(shop, "Mango", "Ripe Ngowe mangoes.", 2000, "DRAFT");
        Thread.sleep(1000);
        assertThat(productDocumentCount(productId)).isZero();

        updateProduct(productId, "Mango", "Ripe Ngowe mangoes.", 2000, "ACTIVE");
        String documentId = waitForProductDocument(shop, productId, "READY");
        assertThat(chunksOf(documentId)).hasSize(1);

        MvcTestResult archived = mvc.delete().uri("/api/admin/products/" + productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();
        assertThat(archived).hasStatus(HttpStatus.NO_CONTENT);

        waitUntil(() -> productDocumentCount(productId) == 0, "the archived product's document stayed");
        assertThat(chunksOf(documentId)).isEmpty();
    }

    @Test
    void anUploadedPolicyIsCutIntoOverlappingChunksAllTaggedWithTheShop() throws Exception {
        String policy = longPolicy(900); // about 900 words: three overlapping chunks

        MvcTestResult result = upload(shop.token(), "Delivery policy", "delivery.md", policy.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PROCESSING");
        String documentId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        waitForDocument(documentId, "READY");

        List<Map<String, Object>> chunks = chunksOf(documentId);
        assertThat(chunks).hasSize(3);
        for (Map<String, Object> chunk : chunks) {
            assertThat(chunk.get("tenant_id")).isEqualTo(shop.tenantId());
            assertThat(chunk.get("title")).isEqualTo("Delivery policy");
            assertThat(chunk.get("source_type")).isEqualTo("UPLOAD");
        }
        Integer chunkCount = jdbc.queryForObject("SELECT chunk_count FROM assistant.documents WHERE id = ?::uuid",
                Integer.class, documentId);
        assertThat(chunkCount).isEqualTo(3);

        MvcTestResult list = mvc.get().uri("/api/admin/assistant/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$[0].title").isEqualTo("Delivery policy");
    }

    @Test
    void filesThatCantBeUsedAreRefusedClearly() throws Exception {
        byte[] text = "We deliver on Saturdays.".getBytes(StandardCharsets.UTF_8);

        assertInvalid(upload(shop.token(), "Price list", "prices.pdf", text), "Only .txt and .md files can be uploaded.");
        assertInvalid(upload(shop.token(), "Empty", "empty.txt", new byte[0]), "The file is empty.");
        assertInvalid(upload(shop.token(), "Big", "big.txt", new byte[2 * 1024 * 1024 + 1]), "The file is larger than 2 MB.");
        assertInvalid(upload(shop.token(), "Binary", "photo.txt", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
                "The file isn't UTF-8 text.");
        assertInvalid(upload(shop.token(), " ", "notes.txt", text), "Give the document a title.");

        Integer saved = jdbc.queryForObject("SELECT count(*) FROM assistant.documents WHERE tenant_id = ?::uuid",
                Integer.class, shop.tenantId());
        assertThat(saved).isZero();
    }

    @Test
    void onlyTheOwnerCanUploadOrDelete() throws Exception {
        String staffToken = staffTokenFor(shop.tenantId());

        MvcTestResult upload = upload(staffToken, "Hours", "hours.txt", "Open 8 to 6.".getBytes(StandardCharsets.UTF_8));
        assertThat(upload).hasStatus(HttpStatus.FORBIDDEN);

        // Staff may still look at the list.
        MvcTestResult list = mvc.get().uri("/api/admin/assistant/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken).exchange();
        assertThat(list).hasStatus(HttpStatus.OK);
    }

    @Test
    void whenEmbeddingFailsTheDocumentSaysSoAndOthersAreFine() throws Exception {
        MvcTestResult bad = upload(shop.token(), "Broken", "broken.txt",
                ("Returns " + FakeEmbeddingModel.FAIL_MARKER).getBytes(StandardCharsets.UTF_8));
        MvcTestResult good = upload(shop.token(), "Hours", "hours.txt", "Open 8 to 6.".getBytes(StandardCharsets.UTF_8));
        String badId = JsonPath.read(bad.getResponse().getContentAsString(), "$.id");
        String goodId = JsonPath.read(good.getResponse().getContentAsString(), "$.id");

        waitForDocument(badId, "FAILED");
        waitForDocument(goodId, "READY");

        String error = jdbc.queryForObject("SELECT error FROM assistant.documents WHERE id = ?::uuid", String.class, badId);
        assertThat(error).startsWith("Indexing failed").hasSizeLessThanOrEqualTo(300);
        assertThat(chunksOf(badId)).isEmpty();
        assertThat(chunksOf(goodId)).hasSize(1);
    }

    @Test
    void deletingAnUploadRemovesItsChunksAndAnotherShopCanNeitherSeeNorDeleteIt() throws Exception {
        Shop otherShop = signUp();
        MvcTestResult result = upload(shop.token(), "Hours", "hours.txt", "Open 8 to 6.".getBytes(StandardCharsets.UTF_8));
        String documentId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        waitForDocument(documentId, "READY");

        MvcTestResult theirList = mvc.get().uri("/api/admin/assistant/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherShop.token()).exchange();
        assertThat(theirList).bodyJson().extractingPath("$.length()").isEqualTo(0);
        MvcTestResult theirDelete = mvc.delete().uri("/api/admin/assistant/documents/" + documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherShop.token()).exchange();
        assertThat(theirDelete).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(chunksOf(documentId)).hasSize(1);

        MvcTestResult ourDelete = mvc.delete().uri("/api/admin/assistant/documents/" + documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();
        assertThat(ourDelete).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chunksOf(documentId)).isEmpty();
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM assistant.documents WHERE id = ?::uuid",
                Integer.class, documentId);
        assertThat(rows).isZero();
    }

    @Test
    void aProductsDocumentCantBeDeletedDirectly() throws Exception {
        String productId = createProduct(shop, "Sukuma bundle", "Fresh.", 5000, "ACTIVE");
        String documentId = waitForProductDocument(shop, productId, "READY");

        MvcTestResult result = mvc.delete().uri("/api/admin/assistant/documents/" + documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_DOCUMENT");
    }

    @Test
    void reindexTeachesTheAssistantProductsItNeverHeardAbout() throws Exception {
        String productId = createProduct(shop, "Sukuma bundle", "Fresh.", 5000, "ACTIVE");
        String documentId = waitForProductDocument(shop, productId, "READY");
        // As if the product existed before the assistant listened: no document, no chunks.
        jdbc.update("DELETE FROM assistant.vector_store WHERE metadata ->> 'document_id' = ?", documentId);
        jdbc.update("DELETE FROM assistant.documents WHERE id = ?::uuid", documentId);

        MvcTestResult result = mvc.post().uri("/api/admin/assistant/products/reindex")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.productsQueued").isEqualTo(1);
        String newDocument = waitForProductDocument(shop, productId, "READY");
        assertThat(chunksOf(newDocument)).hasSize(1);

        // A second reindex finds nothing new to do.
        MvcTestResult again = mvc.post().uri("/api/admin/assistant/products/reindex")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();
        assertThat(again).bodyJson().extractingPath("$.productsQueued").isEqualTo(0);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void assertInvalid(MvcTestResult result, String detail) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_DOCUMENT");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
    }

    private MvcTestResult upload(String token, String title, String fileName, byte[] bytes) {
        MockMultipartFile file = new MockMultipartFile("file", fileName, MediaType.TEXT_PLAIN_VALUE, bytes);
        return mvc.post().uri("/api/admin/assistant/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .multipart()
                .file(file)
                .param("title", title)
                .exchange();
    }

    /** The document's chunks, each as its metadata plus its text under "content". */
    private List<Map<String, Object>> chunksOf(String documentId) {
        return jdbc.queryForList("""
                SELECT content, metadata ->> 'tenant_id' AS tenant_id, metadata ->> 'document_id' AS document_id,
                       metadata ->> 'title' AS title, metadata ->> 'source_type' AS source_type
                FROM assistant.vector_store WHERE metadata ->> 'document_id' = ?
                ORDER BY (metadata ->> 'chunk_index')::int
                """, documentId);
    }

    private String documentStatus(String documentId) {
        List<String> found = jdbc.queryForList("SELECT status FROM assistant.documents WHERE id = ?::uuid",
                String.class, documentId);
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    private int productDocumentCount(String productId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM assistant.documents WHERE source_type = 'PRODUCT' AND source_id = ?::uuid",
                Integer.class, productId);
        return count;
    }

    private void waitForDocument(String documentId, String status) throws InterruptedException {
        waitUntil(() -> status.equals(documentStatus(documentId)),
                "document never became " + status + "; it is " + documentStatus(documentId));
    }

    private String waitForProductDocument(Shop shop, String productId, String status) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            List<String> found = jdbc.queryForList("""
                    SELECT id::text FROM assistant.documents
                    WHERE tenant_id = ?::uuid AND source_type = 'PRODUCT' AND source_id = ?::uuid AND status = ?
                    """, String.class, shop.tenantId(), productId, status);
            if (!found.isEmpty()) {
                return found.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The product's document never became " + status);
    }

    private void waitUntil(Condition condition, String failure) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (condition.holds()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(failure);
    }

    private interface Condition {
        boolean holds();
    }

    private static String longPolicy(int wordCount) {
        StringBuilder text = new StringBuilder("# Delivery\n\n");
        for (int i = 1; i <= wordCount; i++) {
            text.append("word").append(i).append(' ');
            if (i % 20 == 0) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    private String createProduct(Shop shop, String name, String description, long priceMinor, String status) {
        MvcTestResult created = mvc.post().uri("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "description": "%s", "priceMinor": %d, "status": "%s"}
                        """.formatted(name, description, priceMinor, status))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void updateProduct(String productId, String name, String description, long priceMinor, String status) {
        // The update must name the version it saw (optimistic locking).
        Integer version = jdbc.queryForObject("SELECT version FROM catalog.products WHERE id = ?::uuid",
                Integer.class, productId);
        MvcTestResult updated = mvc.put().uri("/api/admin/products/" + productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "name": "%s", "description": "%s", "priceMinor": %d, "status": "%s"}
                        """.formatted(version, name, description, priceMinor, status))
                .exchange();
        assertThat(updated).hasStatus(HttpStatus.OK);
    }

    private String staffTokenFor(String tenantId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sme-commerce")
                .subject(UUID.randomUUID().toString())
                .claim("tid", tenantId)
                .claim("role", "STAFF")
                .claim("typ", "STAFF")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "ai-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "ai+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String tenantId = jdbc.queryForObject("SELECT id::text FROM tenant.tenants WHERE slug = ?", String.class, slug);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"), tenantId);
    }

    private record Shop(String slug, String token, String tenantId) {
    }

}
