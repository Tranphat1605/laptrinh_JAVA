package bll;

import entity.*;

import java.util.List;

public class EvaluationService {

    private SandboxService sandboxService;

    public EvaluationService() {
        this.sandboxService = new SandboxService();
    }

    /**
     * Chạy code mẫu qua tất cả các testcase để kiểm tra chất lượng của testcase.
     * @param testCases Danh sách testcase lấy từ DB
     * @param sampleCodes Danh sách code mẫu (có thể là AC chuẩn, hoặc cố tình làm WA/TLE)
     * @param timeLimitMs Giới hạn thời gian (VD: 2000ms)
     * @return Báo cáo đánh giá chất lượng testcase
     */
    public EvaluationReport evaluateTestCases(List<TestCase> testCases, List<SampleCode> sampleCodes, Checker checker, long timeLimitMs) {
        EvaluationReport report = new EvaluationReport();

        for (int i = 0; i < sampleCodes.size(); i++) {
            SampleCode sample = sampleCodes.get(i);
            int passedCount = 0;
            boolean hasTLE = false;
            boolean hasRTE = false;
            String expected = sample.getExpectedVerdict().toUpperCase();

            for (TestCase tc : testCases) {
                // Sử dụng Sandbox thực thi code thí sinh/mẫu
                ExecutionResult execResult = sandboxService.executeCode(sample.getCode(), sample.getLanguage(), tc.getInputData(), timeLimitMs);
                
                String actualVerdict;
                if (checker != null) {
                    // Nếu bài toán có cung cấp custom checker
                    actualVerdict = getVerdictByChecker(execResult, tc.getInputData(), tc.getExpectedOutput(), checker);
                } else {
                    // So khớp chính xác mặc định
                    actualVerdict = getVerdict(execResult, tc.getExpectedOutput());
                }
                
                if (actualVerdict.equals("AC")) {
                    passedCount++;
                } else if (actualVerdict.equals("TLE")) {
                    hasTLE = true;
                } else if (actualVerdict.equals("RTE")) {
                    hasRTE = true;
                }

                // Cập nhật độ mạnh của Test Case 
                // Testcase nào bắt được code cố tình sai (WA) hoặc code chậm (TLE) sẽ được đánh giá là Testcase chất lượng (Strong)
                if (expected.equals("WA") && actualVerdict.equals("WA")) {
                    tc.setStrengthStatus("Strong");
                } else if (expected.equals("TLE") && actualVerdict.equals("TLE")) {
                    tc.setStrengthStatus("Strong");
                } else if (tc.getStrengthStatus() == null || tc.getStrengthStatus().isEmpty()) {
                    tc.setStrengthStatus("Normal");
                }

                // Ghi nhận chi tiết (Giả sử code mẫu đang chạy có id = i cho mục đích tracking)
                EvaluationResult tcResult = new EvaluationResult(0, i, tc.getId(), actualVerdict, execResult.getOutput(), execResult.getExecutionTime());
                report.addResult(tcResult);
            }

            // --- PHÂN TÍCH NHẬN XÉT DỰA TRÊN VERDICT CHUẨN CỦA CODE MẪU ---
            
            // 1. Nếu code mẫu là AC (Chuẩn)
            if (expected.equals("AC")) {
                if (passedCount < testCases.size()) {
                    report.setTestCaseValid(false);
                    report.addWarning("Code chuẩn (AC) nhưng lại nhận kết quả SAI (WA/TLE...). Output của Testcase có thể bị sai, hoặc Code AC thực chất đang viêt sai.");
                }
            }
            // 2. Nếu code mẫu là WA (Cố tình làm sai logic để bẫy)
            else if (expected.equals("WA")) {
                if (passedCount == testCases.size()) {
                    report.addWarning("CẢNH BÁO YẾU: Code cố tình sai (WA) vẫn Pass 100% testcase. Bộ testcase thiếu Edge Case (Trường hợp dị) để bắt lỗi logic này.");
                    // Đánh dấu để người dùng sinh thêm testcase mạnh hơn
                }
            }
            // 3. Nếu code mẫu là TLE (Cố tình vét cạn, không tối ưu O(N^2))
            else if (expected.equals("TLE")) {
                if (!hasTLE && passedCount == testCases.size()) {
                    report.addWarning("CẢNH BÁO YẾU: Code không tối ưu (TLE) vẫn chạy kịp qua toàn bộ testcase. Testcase thiếu các input có N đủ lớn để gây quá thời gian.");
                }
            }
            // Mở rộng thêm có thể có báo lỗi RTE...
        }

        return report;
    }

    /**
     * Hàm so khớp Output thực tế và Output chuẩn của Testcase
     */
    private String getVerdict(ExecutionResult execResult, String expectedOutput) {
        if (!execResult.getStatus().equals("SUCCESS")) {
            return execResult.getStatus(); // Trả luôn TLE, RTE, CE
        }

        String actual = execResult.getOutput();
        if (actual == null) actual = "";
        if (expectedOutput == null) expectedOutput = "";

        // Chuẩn hóa chuỗi trước khi so sánh (Cắt khoảng trắng thừa ở đuôi và enter dư)
        String normalizedActual = normalizeString(actual);
        String normalizedExpected = normalizeString(expectedOutput);

        if (normalizedActual.equals(normalizedExpected)) {
            return "AC"; // Accepted
        } else {
            return "WA"; // Wrong Answer
        }
    }

    /**
     * Hàm chấm dùng Custom Checker thay vì so khớp chính xác
     */
    private String getVerdictByChecker(ExecutionResult execResult, String inputData, String expectedOutput, Checker checker) {
        if (!execResult.getStatus().equals("SUCCESS")) {
            return execResult.getStatus(); // Trả luôn TLE, RTE, CE của code mẫu
        }

        String actual = execResult.getOutput();
        
        // Chuẩn bị Input cho Checker. 
        // Khuyến nghị cấu trúc: Dòng 1: [---INPUT---], Giữa: Dữ liệu input, ... để dễ parse trong checker code
        String checkerInput = "===INPUT===\n" + (inputData == null ? "" : inputData) + "\n" +
                              "===EXPECTED===\n" + (expectedOutput == null ? "" : expectedOutput) + "\n" +
                              "===ACTUAL===\n" + (actual == null ? "" : actual) + "\n";
        
        // Chạy file checker (Cho thêm time limit dư dả cho checker như 5000ms)
        ExecutionResult checkerResult = sandboxService.executeCode(checker.getCode(), checker.getLanguage(), checkerInput, 5000);
        
        if (checkerResult.getStatus().equals("SUCCESS")) {
            // Giả định Checker in ra "AC", "WA", "PE" (Presentation Error)... ở dòng cuối cùng
            String checkerOutput = checkerResult.getOutput().trim().toUpperCase();
            if (checkerOutput.contains("AC") || checkerOutput.equals("ACCEPTED") || checkerOutput.equals("1") || checkerOutput.equals("TRUE")) {
                return "AC";
            } else {
                return "WA";
            }
        }
        
        // Nếu bản thân file checker bị lỗi (Cố pháp / Lỗi khi chạy / Hết thời gian) 
        System.err.println("Checker Error: " + checkerResult.getError());
        return "CHECKER_ERROR"; 
    }

    /**
     * Chuẩn hóa: xóa khoảng trắng thừa cuối dòng và các dòng trống ở cuối
     */
    private String normalizeString(String input) {
        if (input == null) {
            return "";
        }
        // Chuẩn hóa tất cả các loại xuống dòng (\r\n, \r) thành \n
        String normalized = input.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.stripTrailing()).append("\n"); // Trim từng dòng
        }
        return sb.toString().stripTrailing(); // Trim toàn bộ đoạn văn bản cuối cùng để bỏ dòng trống thừa
    }
}