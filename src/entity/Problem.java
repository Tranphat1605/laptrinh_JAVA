package entity;

public class Problem {
    private int id;
    private String title;
    private String content; // Nội dung đề bài
    private int timeLimitMs; // Giới hạn thời gian (milliseconds)
    private int memoryLimitMb; // Giới hạn bộ nhớ (Megabytes)
    private String source; // Nguồn: IOI, ICPC...

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
}