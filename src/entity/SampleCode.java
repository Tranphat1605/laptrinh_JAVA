package entity;

public class SampleCode {
    private int id;
    private int problemId;
    private String code;
    private String language;
    private String expectedVerdict; // "AC", "WA", "TLE"

    public SampleCode() {}

    public SampleCode(String code, String language, String expectedVerdict) {
        this(0, 0, code, language, expectedVerdict);
    }

    public SampleCode(int problemId, String code, String language, String expectedVerdict) {
        this(0, problemId, code, language, expectedVerdict);
    }

    public SampleCode(int id, int problemId, String code, String language, String expectedVerdict) {
        this.id = id;
        this.problemId = problemId;
        this.code = code;
        this.language = language;
        this.expectedVerdict = expectedVerdict;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProblemId() { return problemId; }
    public void setProblemId(int problemId) { this.problemId = problemId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getExpectedVerdict() { return expectedVerdict; }
    public void setExpectedVerdict(String expectedVerdict) { this.expectedVerdict = expectedVerdict; }
}
