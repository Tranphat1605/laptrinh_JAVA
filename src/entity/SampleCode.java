package entity;

public class SampleCode {
    private String code;
    private String language;
    private String expectedVerdict; // "AC", "WA", "TLE"

    public SampleCode(String code, String language, String expectedVerdict) {
        this.code = code;
        this.language = language;
        this.expectedVerdict = expectedVerdict;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getExpectedVerdict() { return expectedVerdict; }
    public void setExpectedVerdict(String expectedVerdict) { this.expectedVerdict = expectedVerdict; }
}
