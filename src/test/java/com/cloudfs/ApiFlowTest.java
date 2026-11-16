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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudfs.service.TrashService;

/** Kiem thu end-to-end cac API qua MockMvc, voi H2 + storage local (khong can Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate db;

    @Autowired
    TrashService trashService;

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
                .andExpect(jsonPath("$.detail", is("Storage quota exceeded")));
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
    void zipDownloadPreservesNestedFoldersAndRejectsOtherUsersFiles() throws Exception {
        String owner = register(uniq("zipowner"));
        String stranger = register(uniq("zipother"));
        String root = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Photos\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String child = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"2026\",\"parentId\":\"" + root + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        upload(owner, "one.txt", "nested file", child);
        String duplicate1 = upload(owner, "same.txt", "first", null).get("id").asText();
        String duplicate2 = upload(owner, "same.txt", "second", null).get("id").asText();
        String body = "{\"folderIds\":[\"" + root + "\"],\"fileIds\":[\"" + duplicate1 + "\",\"" + duplicate2 + "\"]}";
        mvc.perform(post("/api/downloads/zip").header("Authorization", stranger).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        var pending = mvc.perform(post("/api/downloads/zip").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted()).andReturn();
        byte[] zip = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Map<String, String> entries = new HashMap<>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals("nested file", entries.get("Photos/2026/one.txt"));
        org.junit.jupiter.api.Assertions.assertEquals("first", entries.get("same.txt"));
        org.junit.jupiter.api.Assertions.assertEquals("second", entries.get("same (2).txt"));
    }

    @Test
    void trashHidesRestoresAndPermanentlyDeletesFilesAndFolders() throws Exception {
        String owner = register(uniq("trashowner"));
        String stranger = register(uniq("trashother"));
        String folder = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Archive\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String file = upload(owner, "inside.txt", "data", folder).get("id").asText();
        mvc.perform(delete("/api/folders/" + folder).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/" + file).header("Authorization", owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/trash").header("Authorization", owner)).andExpect(jsonPath("$[0].name", is("Archive")));
        mvc.perform(post("/api/trash/folders/" + folder + "/restore").header("Authorization", stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/api/trash/folders/" + folder + "/restore").header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/" + file).header("Authorization", owner)).andExpect(status().isOk());
        mvc.perform(delete("/api/files/" + file).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/trash/files/" + file).header("Authorization", stranger)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/trash/files/" + file).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/trash/files/" + file + "/restore").header("Authorization", owner)).andExpect(status().isNotFound());
    }

    @Test
    void trashKeepsEarlierDeletionSeparateAndDisablesShareUntilRestored() throws Exception {
        String owner = register(uniq("recover"));
        String folder = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Archive\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String earlier = upload(owner, "earlier.txt", "early", folder).get("id").asText();
        String current = upload(owner, "current.txt", "current", folder).get("id").asText();
        JsonNode link = json.readTree(mvc.perform(post("/api/files/" + current + "/shares").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String publicUrl = "/api/public/shares/" + link.get("token").asText();
        mvc.perform(delete("/api/files/" + earlier).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/folders/" + folder).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get(publicUrl)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me").header("Authorization", owner)).andExpect(jsonPath("$.usedBytes", is(12)));
        mvc.perform(post("/api/trash/folders/" + folder + "/restore").header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get(publicUrl)).andExpect(status().isOk());
        mvc.perform(get("/api/files/" + earlier).header("Authorization", owner)).andExpect(status().isNotFound());
        mvc.perform(post("/api/trash/files/" + earlier + "/restore").header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/" + earlier).header("Authorization", owner)).andExpect(jsonPath("$.folderId", is(folder)));
    }

    @Test
    void expiredTrashCannotBeRestoredAndIsPurged() throws Exception {
        String owner = register(uniq("expire"));
        String file = upload(owner, "expire.txt", "old", null).get("id").asText();
        mvc.perform(delete("/api/files/" + file).header("Authorization", owner)).andExpect(status().isNoContent());
        db.update("UPDATE files SET deleted_at = ? WHERE id = ?", java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(31))), UUID.fromString(file));
        mvc.perform(post("/api/trash/files/" + file + "/restore").header("Authorization", owner)).andExpect(status().isGone());
        trashService.purgeExpired();
        mvc.perform(get("/api/trash").header("Authorization", owner)).andExpect(jsonPath("$", hasSize(0)));
        trashService.deleteQueuedObjects();
        org.junit.jupiter.api.Assertions.assertEquals(0, db.queryForObject("SELECT count(*) FROM pending_object_deletions", Integer.class));
    }

    @Test
    void restoredFileReturnsToRootWhenItsOriginalFolderIsInTrash() throws Exception {
        String owner = register(uniq("moved"));
        String folder = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Old\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String file = upload(owner, "saved.txt", "hello", folder).get("id").asText();
        mvc.perform(delete("/api/files/" + file).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/folders/" + folder).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/trash/files/" + file + "/restore").header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files").header("Authorization", owner)).andExpect(jsonPath("$[0].name", is("saved.txt")));
        mvc.perform(delete("/api/trash/folders/" + folder).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/" + file).header("Authorization", owner)).andExpect(status().isOk());
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
    void folderSharingRequiresAcceptanceAndRestrictsAccessToSubtree() throws Exception {
        String owner = register(uniq("owner"));
        String recipientName = uniq("recipient");
        String recipient = register(recipientName);
        String stranger = register(uniq("stranger"));
        String root = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Shared\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String child = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nested\",\"parentId\":\"" + root + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String inside = upload(owner, "inside.txt", "inside", child).get("id").asText();
        String outside = upload(owner, "outside.txt", "private", null).get("id").asText();
        JsonNode invitation = json.readTree(mvc.perform(post("/api/folders/" + root + "/invitations")
                .header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + recipientName + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = invitation.get("id").asText();
        mvc.perform(post("/api/folders/" + root + "/invitations").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + recipientName + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/folders/" + root + "/invitations").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/folder-invitations/" + id).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/folder-invitations").header("Authorization", recipient))
                .andExpect(jsonPath("$[0].status", is("PENDING")));
        mvc.perform(get("/api/folder-invitations/" + id + "/contents").header("Authorization", recipient))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/folder-invitations/" + id + "/zip").header("Authorization", recipient))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/folder-invitations/" + id + "/accept").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/folder-invitations/" + id + "/accept").header("Authorization", recipient))
                .andExpect(status().isOk());
        mvc.perform(get("/api/folder-invitations/" + id + "/contents").header("Authorization", recipient))
                .andExpect(jsonPath("$.folders[0].name", is("Nested")));
        mvc.perform(get("/api/folder-invitations/" + id + "/files/" + inside).header("Authorization", recipient))
                .andExpect(content().string("inside"));
        mvc.perform(get("/api/folder-invitations/" + id + "/files/" + outside).header("Authorization", recipient))
                .andExpect(status().isNotFound());
        var archive = mvc.perform(get("/api/folder-invitations/" + id + "/zip").header("Authorization", recipient))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(archive)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/zip"));
        mvc.perform(get("/api/folder-invitations/" + id + "/zip?folderId=" + UUID.randomUUID()).header("Authorization", recipient))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/folder-invitations/" + id).header("Authorization", owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/folder-invitations/" + id + "/contents").header("Authorization", recipient))
                .andExpect(status().isNotFound());
    }

    @Test
    void folderLinkOnlyExposesItsSubtreeAndCanBeRevoked() throws Exception {
        String owner = register(uniq("linkowner"));
        String root = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Public\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String inside = upload(owner, "inside.txt", "public", root).get("id").asText();
        String outside = upload(owner, "outside.txt", "private", null).get("id").asText();
        JsonNode link = json.readTree(mvc.perform(post("/api/folders/" + root + "/links")
                .header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String url = "/api/public/folders/" + link.get("token").asText();
        mvc.perform(get(url)).andExpect(jsonPath("$.files[0].name", is("inside.txt")));
        mvc.perform(get(url + "/files/" + inside)).andExpect(content().string("public"));
        mvc.perform(get(url + "/files/" + outside)).andExpect(status().isNotFound());
        var archive = mvc.perform(get(url + "/zip")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(archive)).andExpect(status().isOk());
        mvc.perform(get(url + "?folderId=" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/folder-links/" + link.get("id").asText()).header("Authorization", owner))
                .andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void recipientCanDeclineAndOwnerCanInviteAgain() throws Exception {
        String owner = register(uniq("declineowner"));
        String username = uniq("declineto");
        String recipient = register(username);
        String folder = json.readTree(mvc.perform(post("/api/folders").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Private\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String inviteBody = "{\"username\":\"" + username + "\"}";
        String invitation = json.readTree(mvc.perform(post("/api/folders/" + folder + "/invitations")
                .header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(inviteBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/folder-invitations/" + invitation + "/decline").header("Authorization", recipient))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/folder-invitations").header("Authorization", recipient)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/folders/" + folder + "/invitations").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(inviteBody)).andExpect(status().isCreated());
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
