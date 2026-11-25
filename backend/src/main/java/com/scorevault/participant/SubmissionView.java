package com.scorevault.participant;

public record SubmissionView(int submissionId, String teamName, String eventTitle, String roundName,
                             String fileName, String mimeType, long sizeBytes, String sha256,
                             String submittedAt) {}