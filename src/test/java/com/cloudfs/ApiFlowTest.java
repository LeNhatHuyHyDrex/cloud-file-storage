package com.cloudfs;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Kiem thu end-to-end cac API qua MockMvc, voi H2 + storage local (khong can Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String register(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private static String uniq(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode upload(String token, String name, String text, String folderId) throws Exception {
        var req = multipart("/api/files")
                .file(new MockMultipartFile("file", name, "text/plain", text.getBytes()))
                .header("Authorization", token);
        if (folderId != null) {
            req.param("folderId", folderId);
        }
        return json.readTree(mvc.perform(req).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/files").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerValidatesInputAndRejectsDuplicates() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a b\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
        String user = uniq("dup");
        register(user);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginIsLockedAfterRepeatedFailures() throws Exception {
        String user = uniq("lock");
        register(user);
        String bad = "{\"username\":\"" + user + "\",\"password\":\"wrongpass\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void uploadListDownloadRenameDelete() throws Exception {
        String token = register(uniq("u"));
        String id = upload(token, "hello.txt", "hello cloud", null).get("id").asText();

        mvc.perform(get("/api/files").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("hello.txt")))
                .andExpect(jsonPath("$[0].category", is("document")));

        mvc.perform(get("/api/files/" + id + "/download").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().string("hello cloud"));

        mvc.perform(patch("/api/files/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"../renamed.txt\"}"))
                .andExpect(jsonPath("$.name", is("renamed.txt")));

        mvc.perform(put("/api/files/" + id + "/star").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"starred\":true}"))
                .andExpect(jsonPath("$.starred", is(true)));
        mvc.perform(get("/api/files?view=starred").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(delete("/api/files/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/" + id).header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void otherUsersCannotSeeMyFiles() throws Exception {
        String alice = register(uniq("alice"));
        String bob = register(uniq("bob"));
        String id = upload(alice, "secret.txt", "top secret", null).get("id").asText();

        mvc.perform(get("/api/files/" + id + "/download").header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/files").header("Authorization", bob)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void quotaIsEnforced() throws Exception {
        String token = register(uniq("q"));
        upload(token, "a.txt", "x".repeat(900), null);
        mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "b.txt", "text/plain", "y".repeat(200).getBytes()))
                        .header("Authorization", token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail", is("Vượt quá dung lượng cho phép")));
        mvc.perform(get("/api/me").header("Authorization", token))
                .andExpect(jsonPath("$.usedBytes", is(900)))
                .andExpect(jsonPath("$.quotaBytes", is(1000)))
                .andExpect(jsonPath("$.storageBackend", is("local")));
    }

    @Test
    void foldersNestMoveAndDeleteRecursively() throws Exception {
        String token = register(uniq("f"));
        String parent = json.readTree(mvc.perform(post("/api/folders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Docs\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String child = json.readTree(mvc.perform(post("/api/folders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026\",\"parentId\":\"" + parent + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/folders/" + child + "/path").header("Authorization", token))
                .andExpect(jsonPath("$[0].name", is("Docs")))
                .andExpect(jsonPath("$[1].name", is("2026")));

        String fileId = upload(token, "in-child.txt", "nested", child).get("id").asText();
        String rootFile = upload(token, "root.txt", "root", null).get("id").asText();
        mvc.perform(post("/api/files/" + rootFile + "/move").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"folderId\":\"" + parent + "\"}"))
                .andExpect(jsonPath("$.folderId", is(parent)));

        mvc.perform(delete("/api/folders/" + parent).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/" + fileId).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/files/" + rootFile).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/folders").header("Authorization", token)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void publicShareLinkWorksUntilRevoked() throws Exception {
        String token = register(uniq("s"));
        String fileId = upload(token, "shared.txt", "public data", null).get("id").asText();
        JsonNode share = json.readTree(mvc.perform(post("/api/files/" + fileId + "/shares")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInHours\":24}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String t = share.get("token").asText();

        mvc.perform(get("/api/public/shares/" + t))
                .andExpect(jsonPath("$.fileName", is("shared.txt")));
        mvc.perform(get("/api/public/shares/" + t + "/download"))
                .andExpect(content().string("public data"))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
        mvc.perform(get("/api/shares").header("Authorization", token))
                .andExpect(jsonPath("$[0].downloads", is(1)));

        mvc.perform(delete("/api/shares/" + share.get("id").asText()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/public/shares/" + t)).andExpect(status().isNotFound());
    }

    @Test
    void htmlIsNeverServedInline() throws Exception {
        String token = register(uniq("x"));
        String id = json.readTree(mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes()))
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/files/" + id + "/download?inline=true").header("Authorization", token))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void presignIsNotAvailableOnLocalBackend() throws Exception {
        String token = register(uniq("p"));
        String id = upload(token, "p.txt", "p", null).get("id").asText();
        mvc.perform(get("/api/files/" + id + "/presign").header("Authorization", token))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
