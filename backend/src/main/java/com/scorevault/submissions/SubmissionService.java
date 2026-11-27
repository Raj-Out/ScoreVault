package com.scorevault.submissions;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.azure.core.credential.TokenCredential;
import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;

@Service
public class SubmissionService {
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final String storageEndpoint;
    private final String containerName;
    private volatile BlobContainerClient container;

    public SubmissionService(JdbcTemplate jdbc, AuditService audit,
            @Value("${scorevault.storage.account-url:}") String storageEndpoint,
            @Value("${scorevault.storage.container:participant-submissions}") String containerName) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.storageEndpoint = storageEndpoint;
        this.containerName = containerName;
    }

    public List<SubmissionView> listForTeam(int teamId, int userId) {
        requireParticipantTeamAccess(teamId, userId);
        return jdbc.query("SELECT s.submission_id,s.round_id,r.round_name,s.file_name,s.file_size_bytes,"
                        + "s.mime_type,s.submission_notes,s.submitted_at FROM submissions s "
                        + "JOIN rounds r ON r.round_id=s.round_id WHERE s.team_id=? ORDER BY s.submitted_at DESC",
                (rs, row) -> new SubmissionView(rs.getInt("submission_id"), rs.getInt("round_id"),
                        rs.getString("round_name"), rs.getString("file_name"), rs.getLong("file_size_bytes"),
                        rs.getString("mime_type"), rs.getString("submission_notes"),
                        rs.getTimestamp("submitted_at").toInstant().toString()), teamId);
    }

    @Transactional
    public SubmissionView upload(int teamId, int roundId, MultipartFile file, String notes, int userId, String ip) {
        requireParticipantTeamAccess(teamId, userId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a file to submit.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Files must be 50 MB or smaller.");
        }
        RoundTarget target = jdbc.query("SELECT r.round_id,r.round_name,r.status,e.status AS event_status "
                        + "FROM rounds r JOIN events e ON e.event_id=r.event_id JOIN teams t ON t.event_id=e.event_id "
                        + "WHERE r.round_id=? AND t.team_id=?",
                rs -> rs.next() ? new RoundTarget(rs.getInt("round_id"), rs.getString("round_name"),
                        rs.getString("status"), rs.getString("event_status")) : null, roundId, teamId);
        if (target == null) { throw new ApiException(HttpStatus.BAD_REQUEST, "That round is not part of this team's event."); }
        if (!("SCHEDULED".equalsIgnoreCase(target.status()) || "IN_PROGRESS".equalsIgnoreCase(target.status()))
                || !("UPCOMING".equalsIgnoreCase(target.eventStatus()) || "ACTIVE".equalsIgnoreCase(target.eventStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT, "This round is not accepting submissions.");
        }

        String originalName = safeFileName(file.getOriginalFilename());
        String extension = extension(originalName);
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "The selected file could not be read."); }
        validateFileSignature(extension, bytes);
        String contentType = contentType(extension);
        String blobName = "team-" + teamId + "/round-" + roundId + "/" + UUID.randomUUID() + "." + extension;
        BlobClient blob = getContainer().getBlobClient(blobName);
        try {
            blob.upload(BinaryData.fromBytes(bytes), false);
            blob.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
        } catch (RuntimeException ex) {
            try { blob.deleteIfExists(); } catch (RuntimeException ignored) { }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable. Please try again.");
        }

        String cleanNotes = notes == null || notes.isBlank() ? null : notes.trim();
        if (cleanNotes != null && cleanNotes.length() > 1000) {
            blob.deleteIfExists();
            throw new ApiException(HttpStatus.BAD_REQUEST, "Submission notes must be 1,000 characters or fewer.");
        }
        try {
            jdbc.update("EXEC sp_set_session_context @key=N'user_id', @value=?", userId);
            jdbc.update("EXEC sp_set_session_context @key=N'client_ip', @value=?", ip);
            KeyHolder keys = new GeneratedKeyHolder();
            String finalNotes = cleanNotes;
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO submissions (round_id,team_id,file_blob_url,file_name,mime_type,file_size_bytes,file_sha256,submission_notes) "
                                + "VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, roundId);
                statement.setInt(2, teamId);
                statement.setString(3, blob.getBlobUrl());
                statement.setString(4, originalName);
                statement.setString(5, contentType);
                statement.setLong(6, bytes.length);
                statement.setString(7, sha256(bytes));
                statement.setString(8, finalNotes);
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key == null) { throw new IllegalStateException("Submission ID was not returned"); }
            audit.log(userId, "SUBMISSION_UPLOADED", "submissions", String.valueOf(key.intValue()),
                    "Uploaded " + originalName + " for team " + teamId + " and round " + roundId, ip);
            return new SubmissionView(key.intValue(), roundId, target.roundName(), originalName, bytes.length,
                    contentType, cleanNotes, Instant.now().toString());
        } catch (DuplicateKeyException ex) {
            blob.deleteIfExists();
            throw new ApiException(HttpStatus.CONFLICT, "Your team already has a submission for this round.");
        } catch (RuntimeException ex) {
            blob.deleteIfExists();
            throw ex;
        }
    }

    public SubmissionDownload download(int submissionId, int userId, String role) {
        StoredSubmission stored = jdbc.query("SELECT s.file_blob_url,s.file_name,s.mime_type "
                        + "FROM submissions s WHERE s.submission_id=?",
                rs -> rs.next() ? new StoredSubmission(rs.getString("file_blob_url"), rs.getString("file_name"),
                        rs.getString("mime_type")) : null, submissionId);
        if (stored == null) { throw new ApiException(HttpStatus.NOT_FOUND, "Submission not found."); }
        boolean allowed = "ADMIN".equals(role);
        if ("PARTICIPANT".equals(role)) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM submissions s JOIN teams t ON t.team_id=s.team_id "
                            + "LEFT JOIN team_members tm ON tm.team_id=t.team_id WHERE s.submission_id=? "
                            + "AND (t.lead_user_id=? OR tm.user_id=?)", Integer.class, submissionId, userId, userId);
            allowed = count != null && count > 0;
        } else if ("JUDGE".equals(role)) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM submissions s JOIN judge_assignments ja "
                            + "ON ja.round_id=s.round_id AND ja.team_id=s.team_id WHERE s.submission_id=? AND ja.judge_user_id=?",
                    Integer.class, submissionId, userId);
            allowed = count != null && count > 0;
        }
        if (!allowed) { throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to access this submission."); }
        String blobName = blobNameFromUrl(stored.blobUrl());
        try {
            byte[] content = getContainer().getBlobClient(blobName).downloadContent().toBytes();
            return new SubmissionDownload(stored.fileName(), stored.mimeType(), content);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The submission file could not be retrieved.");
        }
    }

    private void requireParticipantTeamAccess(int teamId, int userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM teams t LEFT JOIN team_members tm ON tm.team_id=t.team_id "
                + "WHERE t.team_id=? AND (t.lead_user_id=? OR tm.user_id=?)", Integer.class, teamId, userId, userId);
        if (count == null || count == 0) { throw new ApiException(HttpStatus.FORBIDDEN, "You do not belong to this team."); }
    }

    private BlobContainerClient getContainer() {
        BlobContainerClient result = container;
        if (result != null) { return result; }
        synchronized (this) {
            if (container == null) {
                if (storageEndpoint == null || storageEndpoint.isBlank()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File storage has not been configured yet.");
                }
                TokenCredential credential = new DefaultAzureCredentialBuilder().build();
                BlobServiceClient service = new BlobServiceClientBuilder().endpoint(storageEndpoint)
                        .credential(credential).buildClient();
                BlobContainerClient candidate = service.getBlobContainerClient(containerName);
                try { candidate.createIfNotExists(); }
                catch (RuntimeException ex) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Private file storage is not ready. Check the app identity's Blob Data Contributor access.");
                }
                container = candidate;
            }
            return container;
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) { throw new ApiException(HttpStatus.BAD_REQUEST, "The file needs a name."); }
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_").trim();
        if (name.isBlank() || name.length() > 255) { throw new ApiException(HttpStatus.BAD_REQUEST, "File names must be 1–255 characters."); }
        return name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) { throw new ApiException(HttpStatus.BAD_REQUEST, "Use a PDF, DOCX, PPTX, XLSX, ZIP, or MP4 file."); }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!List.of("pdf", "docx", "pptx", "xlsx", "zip", "mp4").contains(ext)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a PDF, DOCX, PPTX, XLSX, ZIP, or MP4 file.");
        }
        return ext;
    }

    private static void validateFileSignature(String ext, byte[] b) {
        boolean valid = switch (ext) {
            case "pdf" -> b.length >= 5 && new String(b, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "mp4" -> b.length >= 12 && new String(b, 4, 4, StandardCharsets.US_ASCII).equals("ftyp");
            default -> b.length >= 4 && b[0] == 'P' && b[1] == 'K'
                    && ((b[2] == 3 && b[3] == 4) || (b[2] == 5 && b[3] == 6) || (b[2] == 7 && b[3] == 8));
        };
        if (!valid) { throw new ApiException(HttpStatus.BAD_REQUEST, "The file content does not match its file type."); }
    }

    private static String contentType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "mp4" -> "video/mp4";
            default -> "application/zip";
        };
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private String blobNameFromUrl(String blobUrl) {
        String path = URI.create(blobUrl).getRawPath();
        String prefix = "/" + containerName + "/";
        if (!path.startsWith(prefix)) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid submission storage reference."); }
        return path.substring(prefix.length());
    }

    private record RoundTarget(int roundId, String roundName, String status, String eventStatus) {}
    private record StoredSubmission(String blobUrl, String fileName, String mimeType) {}
}
