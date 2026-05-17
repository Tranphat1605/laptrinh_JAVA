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

    /**
     * Kiểm tra xem mã nguồn Checker có hợp lệ để biên dịch hay không.
     * Tránh biên dịch các dòng comment placeholder của hệ thống hoặc mã nguồn không có hàm main().
     */
    public boolean isValid() {
        if (code == null) return false;
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return false;
        
        // Bỏ qua các placeholder hoặc trạng thái chờ của hệ thống
        if (trimmed.startsWith("// [HỆ THỐNG]") || 
            trimmed.startsWith("// [SYSTEM]") || 
            trimmed.startsWith("// ⏳") || 
            trimmed.startsWith("// Đang") || 
            trimmed.startsWith("// ❌ Lỗi")) {
            return false;
        }
        
        // Mã nguồn C++ checker bắt buộc phải có hàm main() để làm điểm khởi chạy
        if (!trimmed.contains("main(") && !trimmed.contains("main (")) {
            return false;
        }
        
        return true;
    }
}

