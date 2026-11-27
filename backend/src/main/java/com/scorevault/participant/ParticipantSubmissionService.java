package com.scorevault.participant;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.scorevault.submissions.SubmissionService;

@Service
public class ParticipantSubmissionService {
    private final JdbcTemplate jdbc;
    private final SubmissionService storage;

    public ParticipantSubmissionService(JdbcTemplate jdbc, SubmissionService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public SubmissionView submit(int userId, int teamId, int roundId, MultipartFile file, String ip) {
        var created = storage.upload(teamId, roundId, file, null, userId, ip);
        return mine(userId).stream()
                .filter(submission -> submission.submissionId() == created.submissionId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Uploaded submission could not be reloaded."));
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
                        rs.getTimestamp("submitted_at").toInstant().toString()), userId);
    }
}
