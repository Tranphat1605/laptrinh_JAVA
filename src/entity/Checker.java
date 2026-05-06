package entity;

public class Checker {
    private String code;
    private String language; // "java", "python", "cpp"

    public Checker(String code, String language) {
        this.code = code;
        this.language = language;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
