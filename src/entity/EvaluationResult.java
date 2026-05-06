package entity;



public class EvaluationResult {
    private int id;
    private int submissionId; // Code nào đang chạy
    private int testcaseId; // Đang chạy testcase nào
    private String status; // AC, WA, TLE, RE (Runtime Error)
    private String actualOutput; // Output thực tế sinh ra từ code
    private long executionTimeMs; // Thời gian chạy thực tế

    public EvaluationResult() {}

    public EvaluationResult(int id, int submissionId, int testcaseId, String status, String actualOutput, long executionTimeMs) {
        this.id = id;
        this.submissionId = submissionId;
        this.testcaseId = testcaseId;
        this.status = status;
        this.actualOutput = actualOutput;
        this.executionTimeMs = executionTimeMs;
    }

    // --- Getters và Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSubmissionId() { return submissionId; }
    public void setSubmissionId(int submissionId) { this.submissionId = submissionId; }

    public int getTestcaseId() { return testcaseId; }
    public void setTestcaseId(int testcaseId) { this.testcaseId = testcaseId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}