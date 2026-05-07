package entity;

public class Checker {
    private int id;
    private int problemId;
    private String code;
    private String language; // "java", "python", "cpp"

    public Checker() {}

    public Checker(String code, String language) {
        this(0, 0, code, language);
    }

    public Checker(int problemId, String code, String language) {
        this(0, problemId, code, language);
    }

    public Checker(int id, int problemId, String code, String language) {
        this.id = id;
        this.problemId = problemId;
        this.code = code;
        this.language = language;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProblemId() { return problemId; }
    public void setProblemId(int problemId) { this.problemId = problemId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
