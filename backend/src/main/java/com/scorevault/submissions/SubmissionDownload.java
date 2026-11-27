package com.scorevault.submissions;

public record SubmissionDownload(String fileName, String mimeType, byte[] content) {}
