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

        String checkerExePath = null;
        if (checker != null) {
            ExecutionResult compileResult = sandboxService.compileChecker(checker.getCode());
            if (!compileResult.getStatus().equals("SUCCESS")) {
                report.addWarning("Biên dịch Custom Checker thất bại: " + compileResult.getError());
                return report;
            }
            checkerExePath = compileResult.getOutput(); // The output contains the path to the executable
        }

        for (int i = 0; i < sampleCodes.size(); i++) {
            SampleCode sample = sampleCodes.get(i);
            int passedCount = 0;
            boolean hasTLE = false;
            String expected = sample.getExpectedVerdict().toUpperCase();

            try {
                // Tách bước Biên dịch
                bll.executor.CodeExecutor executor = bll.executor.CodeExecutorFactory.getExecutor(sample.getLanguage());
                if (executor == null) continue;

                java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("eval_sandbox");
                ExecutionResult compileResult = executor.compile(tempDir, sample.getCode());

                if (!compileResult.getStatus().equals("SUCCESS")) {
                    report.addWarning("Mã nguồn mẫu (" + expected + ") không thể biên dịch: " + compileResult.getError());
                    continue;
                }

                java.util.concurrent.atomic.AtomicInteger atomicPassedCount = new java.util.concurrent.atomic.AtomicInteger();
                java.util.concurrent.atomic.AtomicBoolean atomicHasTLE = new java.util.concurrent.atomic.AtomicBoolean();
                final int currentSampleIndex = i;
                final String finalCheckerExePath = checkerExePath;

                testCases.parallelStream().forEach(tc -> {
                    int sampleIndex = currentSampleIndex;
                    try {
                        // Chạy code mẫu qua từng testcase
                        ExecutionResult execResult = executor.runCode(tempDir, tc.getInputData(), timeLimitMs);
                        
                        String actualVerdict;
                        if (finalCheckerExePath != null) {
                            // Nếu bài toán có cung cấp custom checker
                            actualVerdict = getVerdictByChecker(execResult, tc.getInputData(), tc.getExpectedOutput(), finalCheckerExePath);
                        } else {
                            // So khớp chính xác mặc định
                            actualVerdict = getVerdict(execResult, tc.getExpectedOutput());
                        }
                        
                        if (actualVerdict.equals("AC")) {
                            atomicPassedCount.incrementAndGet();
                        } else if (actualVerdict.equals("TLE")) {
                            atomicHasTLE.set(true);
                        }

                        // Cập nhật độ mạnh của Test Case 
                        synchronized(tc) {
                            if (expected.equals("WA") && actualVerdict.equals("WA")) {
                                tc.setStrengthStatus("Strong");
                            } else if (expected.equals("TLE") && actualVerdict.equals("TLE")) {
                                tc.setStrengthStatus("Strong");
                            } else if (tc.getStrengthStatus() == null || tc.getStrengthStatus().isEmpty()) {
                                tc.setStrengthStatus("Normal");
                            }
                        }

                        // Ghi nhận chi tiết
                        EvaluationResult tcResult = new EvaluationResult(0, sampleIndex, tc.getId(), actualVerdict, execResult.getOutput(), execResult.getExecutionTime());
                        synchronized(report) {
                            report.addResult(tcResult);
                        }
                    } catch (Exception ex) {
                        // ignore ex
                    }
                });

                passedCount = atomicPassedCount.get();
                hasTLE = atomicHasTLE.get();

                // Xóa file tạm sau khi chấm hết các TC
                try {
                    java.io.File[] files = tempDir.toFile().listFiles();
                    if (files != null) {
                        for (java.io.File f : files) f.delete();
                    }
                    java.nio.file.Files.deleteIfExists(tempDir);
                } catch (Exception ignored) {}

            } catch (Exception e) {
                report.addWarning("Lỗi khi đánh giá code mẫu: " + e.getMessage());
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
     * Dùng để chấm bài của Học sinh, trả về AC, WA, TLE, CE, v.v.
     * Chạy code qua từng testcase cho đến khi xong hoặc gặp testcase sai.
     */
    public ExecutionResult evaluateStudentSubmission(String code, String language, List<TestCase> testCases, Checker checker, long timeLimitMs) {
        bll.executor.CodeExecutor executor = bll.executor.CodeExecutorFactory.getExecutor(language);
        if (executor == null) return new ExecutionResult("CE", "", "Unsupported language: " + language, 0, -1);

        try {
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("student_eval");
            ExecutionResult compileResult = executor.compile(tempDir, code);
            
            if (!compileResult.getStatus().equals("SUCCESS")) {
                return new ExecutionResult("CE", "", compileResult.getError(), 0, compileResult.getExitCode());
            }

            String checkerExePath = null;
            if (checker != null) {
                ExecutionResult checkerCompileResult = sandboxService.compileChecker(checker.getCode());
                if (!checkerCompileResult.getStatus().equals("SUCCESS")) {
                    return new ExecutionResult("CE", "", "Lỗi biên dịch Custom Checker: " + checkerCompileResult.getError(), 0, checkerCompileResult.getExitCode());
                }
                checkerExePath = checkerCompileResult.getOutput();
            }

            long maxTime = 0;
            int passedCount = 0;

            for (int i = 0; i < testCases.size(); i++) {
                TestCase tc = testCases.get(i);
                ExecutionResult execResult = executor.runCode(tempDir, tc.getInputData(), timeLimitMs);
                maxTime = Math.max(maxTime, execResult.getExecutionTime());
                
                String actualVerdict;
                if (checkerExePath != null) {
                    actualVerdict = getVerdictByChecker(execResult, tc.getInputData(), tc.getExpectedOutput(), checkerExePath);
                } else {
                    actualVerdict = getVerdict(execResult, tc.getExpectedOutput());
                }
                
                if (actualVerdict.equals("AC")) {
                    passedCount++;
                } else {
                    // Trả về ngay khi gặp testcase lỗi
                    String outputStr = "Sai ở Testcase #" + (i + 1) + " (Passed: " + passedCount + "/" + testCases.size() + ")\n\n"
                            + "Input:\n" + tc.getInputData() + "\n\n"
                            + "Output của bạn:\n" + execResult.getOutput() + "\n\n"
                            + "Output mong đợi:\n" + tc.getExpectedOutput();
                    return new ExecutionResult(actualVerdict, outputStr, execResult.getError(), maxTime, execResult.getExitCode());
                }
            }

            // AC ALL
            return new ExecutionResult("AC", "Đã qua toàn bộ " + testCases.size() + " Testcases!", "", maxTime, 0);

        } catch (Exception e) {
            return new ExecutionResult("RTE", "", "Lỗi hệ thống khi chấm: " + e.getMessage(), 0, -1);
        }
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
    private String getVerdictByChecker(ExecutionResult execResult, String inputData, String expectedOutput, String checkerExePath) {
        if (!execResult.getStatus().equals("SUCCESS")) {
            return execResult.getStatus(); // Trả luôn TLE, RTE, CE của code mẫu
        }

        String actual = execResult.getOutput();
        
        ExecutionResult checkerResult = sandboxService.executeChecker(
                checkerExePath,
                inputData,
                expectedOutput,
                actual,
                5000 // Time limit dư dả cho checker
        );
        
        System.out.println("Checker run result: ExitCode=" + checkerResult.getExitCode() + ", Output=" + checkerResult.getOutput() + ", Error=" + checkerResult.getError());

        // Testlib.h Exit codes:
        // 0 = OK (Accepted)
        // 1 = WA (Wrong Answer) 
        // 2 = PE (Presentation Error)
        // 3 = FAIL (Checker error)
        
        if (checkerResult.getExitCode() == 0) {
            return "AC";
        } else if (checkerResult.getExitCode() == 1) {
            return "WA";
        } else if (checkerResult.getExitCode() == 2) {
            return "PE"; // Có thể nhóm chung vào WA hoặc để PE
        } else {
            return "CHECKER_ERROR " + checkerResult.getExitCode() + ":" + checkerResult.getError() + " | actual: " + actual;
        }
    }

    private String normalizeString(String input) {
        if (input == null) return "";
        String normalized = input.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.stripTrailing()).append("\n"); // Trim từng dòng
        }
        return sb.toString().stripTrailing(); // Trim toàn bộ đoạn văn bản cuối cùng để bỏ dòng trống thừa
    }
}