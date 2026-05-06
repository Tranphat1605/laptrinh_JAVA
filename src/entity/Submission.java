package entity;


public class Submission {
    private int id;
    private int problemId;
    private String sourceCode;
    private String language; // "C++", "Java"
    private String finalStatus; // Kết quả tổng quát: AC, WA, TLE, CE (Compile Error)
    private boolean isReferenceCode; // Đánh dấu đây là code chuẩn (đáp án) hay code cố tình viết sai để test

    public Submission() {}

    public Submission(int id, int problemId, String sourceCode, String language, String finalStatus, boolean isReferenceCode) {
        this.id = id;
        this.problemId = problemId;
        this.sourceCode = sourceCode;
        this.language = language;
        this.finalStatus = finalStatus;
        this.isReferenceCode = isReferenceCode;
    }

    // --- Getters và Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProblemId() { return problemId; }
    public void setProblemId(int problemId) { this.problemId = problemId; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFinalStatus() { return finalStatus; }
    public void setFinalStatus(String finalStatus) { this.finalStatus = finalStatus; }

    public boolean isReferenceCode() { return isReferenceCode; }
    public void setReferenceCode(boolean referenceCode) { this.isReferenceCode = referenceCode; }
}