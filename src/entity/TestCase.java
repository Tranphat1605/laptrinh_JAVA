package entity;


public class TestCase {
    private int id;
    private int problemId; // Khóa ngoại liên kết với Problem
    private String inputData;
    private String expectedOutput;
    private boolean isHidden; // Phân biệt testcase ẩn hay testcase ví dụ
    private String strengthStatus; // Trạng thái: Mạnh, Yếu, Bình thường

    public TestCase() {}

    public TestCase(int id, int problemId, String inputData, String expectedOutput, boolean isHidden, String strengthStatus) {
        this.id = id;
        this.problemId = problemId;
        this.inputData = inputData;
        this.expectedOutput = expectedOutput;
        this.isHidden = isHidden;
        this.strengthStatus = strengthStatus;
    }

    // --- Getters và Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProblemId() { return problemId; }
    public void setProblemId(int problemId) { this.problemId = problemId; }

    public String getInputData() { return inputData; }
    public void setInputData(String inputData) { this.inputData = inputData; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public boolean isHidden() { return isHidden; }
    public void setHidden(boolean hidden) { this.isHidden = hidden; }

    public String getStrengthStatus() { return strengthStatus; }
    public void setStrengthStatus(String strengthStatus) { this.strengthStatus = strengthStatus; }
}