package entity;

import java.util.ArrayList;
import java.util.List;

public class EvaluationReport {
    private List<EvaluationResult> detailedResults = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean isTestCaseValid = true;

    public void addResult(EvaluationResult result) {
        detailedResults.add(result);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<EvaluationResult> getDetailedResults() {
        return detailedResults;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isTestCaseValid() {
        return isTestCaseValid;
    }

    public void setTestCaseValid(boolean testCaseValid) {
        isTestCaseValid = testCaseValid;
    }

    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== BÁO CÁO ĐÁNH GIÁ TESTCASE ===\n");
        sb.append("Trạng thái Testcase: ").append(isTestCaseValid ? "HỢP LỆ" : "CÓ VẤN ĐỀ").append("\n");
        sb.append("\n[Cảnh báo & Nhận xét]:\n");
        if (warnings.isEmpty()) {
            sb.append("- Bộ testcase đủ mạnh và cấu hình đúng.\n");
        } else {
            for (String w : warnings) {
                sb.append("- ").append(w).append("\n");
            }
        }
        return sb.toString();
    }
}
