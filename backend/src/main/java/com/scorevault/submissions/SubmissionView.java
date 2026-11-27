package com.scorevault.submissions;

public record SubmissionView(
        int submissionId,
        int roundId,
        String roundName,
        String fileName,
        long fileSizeBytes,
        String mimeType,
        String submissionNotes,
        String submittedAt
) {}
