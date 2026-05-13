package entity;

public class Problem {
    private int id;
    private String title;
    private String content; // Nội dung đề bài
    private int timeLimitMs; // Giới hạn thời gian (milliseconds)
    private int memoryLimitMb; // Giới hạn bộ nhớ (Megabytes)
    private String source; // Nguồn: IOI, ICPC...
    private String originalImageBase64; // Lưu trữ ảnh gốc dạng base64 nếu có
    private String originalRawText; // Lưu lại đúng y xì đúc text người dùng nhập vào

    public Problem() {}

    public Problem(int id, String title, String content, int timeLimitMs, int memoryLimitMb, String source) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.source = source;
    }

    // --- Getters và Setters ---
    public String getOriginalImageBase64() { return originalImageBase64; }
    public void setOriginalImageBase64(String originalImageBase64) { this.originalImageBase64 = originalImageBase64; }

    public String getOriginalRawText() { return originalRawText; }
    public void setOriginalRawText(String originalRawText) { this.originalRawText = originalRawText; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getTimeLimitMs() { return timeLimitMs; }
    public void setTimeLimitMs(int timeLimitMs) { this.timeLimitMs = timeLimitMs; }

    public int getMemoryLimitMb() { return memoryLimitMb; }
    public void setMemoryLimitMb(int memoryLimitMb) { this.memoryLimitMb = memoryLimitMb; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    /**
     * Trả về nội dung đề bài dạng text để truyền vào AI prompt.
     * Nếu title/source đã được set thì thêm vào context.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank() && !title.equals("Mock")) {
            sb.append("Tên bài: ").append(title).append("\n");
        }
        if (timeLimitMs > 0) {
            sb.append("Giới hạn thời gian: ").append(timeLimitMs).append(" ms\n");
        }
        if (memoryLimitMb > 0) {
            sb.append("Giới hạn bộ nhớ: ").append(memoryLimitMb).append(" MB\n");
        }
        
        // Ưu tiên dùng lại nguyên vẹn text gốc do người dùng nhập để tránh AI tóm tắt làm mất mô tả
        if (originalRawText != null && !originalRawText.isBlank()) {
            sb.append("\nNội dung đề bài (Raw):\n").append(originalRawText);
        } else if (content != null && !content.isBlank()) {
            sb.append("\nNội dung đề bài:\n").append(content);
        }
        return sb.toString().trim();
    }
}