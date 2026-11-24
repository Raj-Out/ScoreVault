package com.scorevault.participant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParticipantSubmissionService {

    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("zip", "mp4", "pdf");
    private static final Map<String, String> MIME = Map.of(
            "zip", "application/zip", "mp4", "video/mp4", "pdf", "application/pdf");

    private final JdbcTemplate jdbc;
    private final Path uploadDir;

    public ParticipantSubmissionService(JdbcTemplate jdbc,
                                        @Value("${scorevault.upload-dir:uploads}") String dir) throws IOException {
        this.jdbc = jdbc;
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    @Transactional
    public SubmissionView submit(int userId, int teamId, int roundId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is larger than 50 MB.");
        }

        // The user must be on this team, and the round must belong to the team's event.
        List<String> status = jdbc.query(
                "SELECT r.status FROM team_members tm "
                        + "JOIN teams t ON t.team_id = tm.team_id "
                        + "JOIN rounds r ON r.event_id = t.event_id "
                        + "WHERE tm.user_id = ? AND t.team_id = ? AND r.round_id = ?",
                (rs, i) -> rs.getString(1), userId, teamId, roundId);
        if (status.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That team or round is not available to you.");
        }
        if (!status.get(0).equals("SCHEDULED") && !status.get(0).equals("IN_PROGRESS")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Submissions are closed for this round.");
        }

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!ALLOWED.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .zip, .mp4 and .pdf files are allowed.");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the file.");
        }
        if (!magicOk(ext, data)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content does not match its type.");
        }

        String sha256 = sha256Hex(data);
        String stored = UUID.randomUUID() + "." + ext;           // never use the user's file name on disk
        String safeName = original.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safeName.length() > 200) { safeName = safeName.substring(safeName.length() - 200); }
        try {
            Files.write(uploadDir.resolve(stored), data);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file.");
        }

        String url = "local/" + stored;
        List<Integer> existing = jdbc.queryForList(
                "SELECT submission_id FROM submissions WHERE round_id = ? AND team_id = ?",
                Integer.class, roundId, teamId);
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO submissions (round_id, team_id, file_blob_url, file_name, mime_type, "
                            + "file_size_bytes, file_sha256, submitted_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME())",
                    roundId, teamId, url, safeName, MIME.get(ext), data.length, sha256);
        } else {
            jdbc.update("UPDATE submissions SET file_blob_url = ?, file_name = ?, mime_type = ?, "
                            + "file_size_bytes = ?, file_sha256 = ?, submitted_at = SYSUTCDATETIME() "
                            + "WHERE submission_id = ?",
                    url, safeName, MIME.get(ext), data.length, sha256, existing.get(0));
        }
        return mine(userId).stream()
                .filter(s -> s.sha256().equals(sha256)).findFirst().orElseThrow();
    }

    public List<SubmissionView> mine(int userId) {
        return jdbc.query(
                "SELECT s.submission_id, t.team_name, e.title, r.round_name, s.file_name, s.mime_type, "
                        + "s.file_size_bytes, s.file_sha256, s.submitted_at "
                        + "FROM submissions s "
                        + "JOIN teams t ON t.team_id = s.team_id "
                        + "JOIN team_members tm ON tm.team_id = t.team_id AND tm.user_id = ? "
                        + "JOIN rounds r ON r.round_id = s.round_id "
                        + "JOIN events e ON e.event_id = r.event_id "
                        + "ORDER BY s.submitted_at DESC",
                (rs, i) -> new SubmissionView(rs.getInt("submission_id"), rs.getString("team_name"),
                        rs.getString("title"), rs.getString("round_name"), rs.getString("file_name"),
                        rs.getString("mime_type"), rs.getLong("file_size_bytes"), rs.getString("file_sha256"),
                        rs.getTimestamp("submitted_at").toInstant().toString()),
                userId);
    }

    // Checks the first bytes of the file, so a renamed .exe cannot pass as a .pdf.
    private static boolean magicOk(String ext, byte[] d) {
        switch (ext) {
            case "pdf": return d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "zip": return d.length > 3 && d[0] == 'P' && d[1] == 'K';
            case "mp4": return d.length > 11 && d[4] == 'f' && d[5] == 't' && d[6] == 'y' && d[7] == 'p';
            default: return false;
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}