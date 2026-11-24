package com.hezron.smecommerce.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.FakeChatModel;
import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Issue "Assistant part 2". Done when: tenant A's assistant can never quote tenant B's documents.
 * <p>
 * The fake chat model repeats everything in its prompt, so another shop's text would appear in the answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AssistantChatTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;

    /** The fake embeddings only compare shared words, so their similarity scores run low. */
    @DynamicPropertySource
    static void aLowerThreshold(DynamicPropertyRegistry registry) {
        registry.add("app.assistant.min-similarity", () -> "0.05");
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeChatModel chat;

    private Shop shop;

    @BeforeEach
    void aShop() throws Exception {
        shop = signUp();
    }

    @Test
    void aShopsAssistantNeverQuotesAnotherShopsDocuments() throws Exception {
        Shop otherShop = signUp();
        uploadAndWait(shop, "Delivery policy", "We deliver sukuma on Sundays from 9am.");
        // The very same question words, in the other shop: the closest match in the whole table.
        uploadAndWait(otherShop, "Secret terms", "We deliver sukuma on Sundays from SECRET-OF-SHOP-B.");

        MvcTestResult result = ask(shop, "Do you deliver sukuma on Sundays?");

        assertThat(result).hasStatus(HttpStatus.OK);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("9am").doesNotContain("SECRET-OF-SHOP-B").doesNotContain("Secret terms");
        List<String> sources = JsonPath.read(body, "$.sources");
        assertThat(sources).containsExactly("Delivery policy");
    }

    @Test
    void aQuestionOnlyAnotherShopCanAnswerGetsNoAnswerAndNoModelCall() throws Exception {
        Shop otherShop = signUp();
        uploadAndWait(shop, "Hours", "Open from eight to six.");
        uploadAndWait(otherShop, "Cement", "Cement costs 750 shillings.");
        int callsBefore = chat.calls();

        MvcTestResult result = ask(shop, "cement");

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("I don't have that information").doesNotContain("750");
        assertThat(JsonPath.<List<String>>read(body, "$.sources")).isEmpty();
        assertThat(chat.calls()).isEqualTo(callsBefore);
    }

    @Test
    void anAnswerCountsTheQuestionAndItsTokens() throws Exception {
        uploadAndWait(shop, "Hours", "Open from eight to six.");

        ask(shop, "When are you open?");

        assertThat(usage("questions")).isEqualTo(1);
        assertThat(usage("input_tokens")).isEqualTo(FakeChatModel.INPUT_TOKENS);
        assertThat(usage("output_tokens")).isEqualTo(FakeChatModel.OUTPUT_TOKENS);
    }

    @Test
    void aShopAtItsDailyQuestionLimitRestsWithoutCallingTheModel() throws Exception {
        uploadAndWait(shop, "Hours", "Open from eight to six.");
        setTodaysUsage(200, 0);
        int callsBefore = chat.calls();

        MvcTestResult result = ask(shop, "When are you open?");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.answer")
                .isEqualTo("The assistant is resting for today. Please contact the shop directly.");
        assertThat(chat.calls()).isEqualTo(callsBefore);
    }

    @Test
    void aShopAtItsDailyTokenBudgetRestsToo() throws Exception {
        uploadAndWait(shop, "Hours", "Open from eight to six.");
        setTodaysUsage(1, 100_000);

        MvcTestResult result = ask(shop, "When are you open?");

        assertThat(result).bodyJson().extractingPath("$.answer")
                .isEqualTo("The assistant is resting for today. Please contact the shop directly.");
    }

    @Test
    void anEleventhQuestionInAMinuteIsRefused() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(ask(shop, "Anything?")).hasStatus(HttpStatus.OK);
        }

        MvcTestResult eleventh = ask(shop, "Anything?");

        assertThat(eleventh).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(eleventh).bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_QUESTIONS");
    }

    @Test
    void aBlankOrTooLongQuestionIsRefused() {
        assertThat(ask(shop, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(ask(shop, "a".repeat(501))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownShopIsNotFound() {
        assertThat(ask(new Shop("no-such-shop", "", ""), "Hello?")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void whenTheModelIsDownTheCustomerGetsAPlainMessage() throws Exception {
        uploadAndWait(shop, "Hours", "Open from eight to six.");

        MvcTestResult result = ask(shop, "open " + FakeChatModel.FAIL_MARKER);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.answer")
                .isEqualTo("The assistant can't answer right now. Please try again in a moment.");
    }

    // --- helpers ----------------------------------------------------------------------------------

    private MvcTestResult ask(Shop shop, String question) {
        return mvc.post().uri("/api/store/" + shop.slug() + "/assistant/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"" + question + "\"}")
                .exchange();
    }

    private Long usage(String column) {
        return jdbc.queryForObject("SELECT " + column + "::bigint FROM assistant.usage_daily WHERE tenant_id = ?::uuid",
                Long.class, shop.tenantId());
    }

    private void setTodaysUsage(int questions, long inputTokens) {
        jdbc.update("""
                INSERT INTO assistant.usage_daily (tenant_id, day, questions, input_tokens)
                VALUES (?::uuid, (now() AT TIME ZONE 'UTC')::date, ?, ?)
                """, shop.tenantId(), questions, inputTokens);
    }

    private void uploadAndWait(Shop owner, String title, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", MediaType.TEXT_PLAIN_VALUE,
                text.getBytes(StandardCharsets.UTF_8));
        MvcTestResult result = mvc.post().uri("/api/admin/assistant/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())
                .multipart().file(file).param("title", title).exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String documentId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            String status = jdbc.queryForObject("SELECT status FROM assistant.documents WHERE id = ?::uuid",
                    String.class, documentId);
            if ("READY".equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The document never became READY");
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "chat-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "chat+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String tenantId = jdbc.queryForObject("SELECT id::text FROM tenant.tenants WHERE slug = ?", String.class, slug);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"), tenantId);
    }

    private record Shop(String slug, String token, String tenantId) {
    }

}
