package entity;

public class ExecutionResult {
    private String status; // "SUCCESS", "TLE", "RTE", "CE" (Compile Error)
    private String output;
    private String error;
    private long executionTime;
    private int exitCode; // Lưu mã thoát để testlib checker có thể dùng

    public ExecutionResult(String status, String output, String error, long executionTime) {
        this(status, output, error, executionTime, 0);
    }

    public ExecutionResult(String status, String output, String error, long executionTime, int exitCode) {
        this.status = status;
        this.output = output;
        this.error = error;
        this.executionTime = executionTime;
        this.exitCode = exitCode;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getExecutionTime() { return executionTime; }
    public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }

    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "status='" + status + '\'' +
                ", time=" + executionTime + "ms" +
                ", exit=" + exitCode +
                ", output='" + output.trim() + '\'' +
                ", error='" + error.trim() + '\'' +
                '}';
    }
}
