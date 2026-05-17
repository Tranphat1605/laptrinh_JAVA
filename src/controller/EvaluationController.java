package controller;

import bll.AIService;
import bll.EvaluationTask;
import entity.Checker;
import entity.EvaluationReport;
import entity.Problem;
import entity.SampleCode;
import entity.TestCase;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Nhận dữ liệu thực từ TeacherFrame (checker, ac, wa code).
 */
public class EvaluationController {

    public interface EvaluationListener {
        /** Gọi ngay khi bắt đầu — trên EDT */
        void onStart();
        /** Cập nhật tiến độ — gọi từ background thread */
        void onProgress(int current, int total, String status);
        /** Hoàn thành — gọi từ background thread */
        void onComplete(EvaluationReport report);
        /** Có lỗi — gọi từ background thread */
        void onError(Exception e);
    }

    /**
     * Pipeline sinh Testcase Tự động và đánh giá
     * @param totalCases Số lượng testcase. Ví dụ: 20
     */
    public void runAutomatedEvaluation(Problem problem, AIService aiService, int totalCases, EvaluationListener listener) {
        listener.onStart();

        new Thread(() -> {
            try {
                // 1. Phân bổ số lượng Testcase
                // Tỷ lệ: 20% edge, 20% max, 60% random (tương đương 4-4-12 nếu tổng=20)
                int edgeCount = (int) Math.ceil(totalCases * 0.2);
                int maxCount = (int) Math.ceil(totalCases * 0.2);
                int randomCount = totalCases - edgeCount - maxCount;

                List<String> modeList = new ArrayList<>();
                for (int i = 0; i < edgeCount; i++) modeList.add("edge");
                for (int i = 0; i < maxCount; i++) modeList.add("max");
                for (int i = 0; i < randomCount; i++) modeList.add("random");

                listener.onProgress(0, totalCases, "Đang gọi AI sinh Generator Code (C++)...");
                String generatorCode = aiService.generateGeneratorCode(problem);
                
                listener.onProgress(0, totalCases, "Đang gọi AI sinh Solution Code AC (C++)...");
                String acCode = aiService.generateSampleCode(problem, "AC");

                listener.onProgress(0, totalCases, "Đang phân tích xem có cần Custom Checker không...");
                boolean needsChecker = aiService.checkIfCheckerIsNeeded(problem);
                
                entity.Checker checker = null;
                if (needsChecker) {
                    listener.onProgress(0, totalCases, "Đang gọi AI sinh Checker Code (C++)...");
                    String checkerCodeRaw = aiService.generateChecker(problem);
                    
                    // Lưu Checker vào Database
                    if (problem != null && problem.getId() <= 0) {
                        new dal.ProblemDAO().addProblem(problem);
                    }
                    checker = new entity.Checker(0, problem != null ? problem.getId() : 1, cleanMarkdown(checkerCodeRaw), "cpp");
                    dal.CheckerDAO checkerDAO = new dal.CheckerDAO();
                    int checkerId = checkerDAO.addChecker(checker);
                    if (checkerId > 0) checker.setId(checkerId);
                } else {
                    listener.onProgress(0, totalCases, "Bài toán đơn giản, sử dụng so khớp chính xác (Skip Checker).");
                }

                // Thư mục tạm
                Path tempDir = Files.createTempDirectory("auto_pipeline_");
                // Copy testlib.h vào thư mục tạm để biên dịch Generator
                File testlibSrc = new File("lib/testlib.h");
                if (testlibSrc.exists()) {
                    Files.copy(testlibSrc.toPath(), tempDir.resolve("testlib.h"), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new Exception("Không tìm thấy file lib/testlib.h trong dự án!");
                }

                // 2. Biên dịch Generator (C++)
                listener.onProgress(0, totalCases, "Đang biên dịch Generator...");
                File genCpp = new File(tempDir.toFile(), "gen.cpp");
                Files.writeString(genCpp.toPath(), cleanMarkdown(generatorCode));
                File genExe = new File(tempDir.toFile(), "gen.exe");

                ProcessBuilder pbGen = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), genCpp.getAbsolutePath(), "-o", genExe.getAbsolutePath());
                pbGen.redirectErrorStream(true);
                Process pGen = pbGen.start();
                if (!pGen.waitFor(15, TimeUnit.SECONDS) || pGen.exitValue() != 0) {
                    String err = new String(pGen.getInputStream().readAllBytes());
                    if (pGen.isAlive()) pGen.destroyForcibly();
                    throw new Exception("Biên dịch Generator thất bại! Vui lòng kiểm tra lại code AI sinh ra. Output: " + err);
                }

                // 3. Biên dịch AC Code (C++)
                listener.onProgress(0, totalCases, "Đang biên dịch Solution chuẩn (AC)...");
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                Files.writeString(acCpp.toPath(), cleanMarkdown(acCode));
                File acExe = new File(tempDir.toFile(), "ac.exe");

                ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                pbAc.redirectErrorStream(true);
                Process pAc = pbAc.start();
                if (!pAc.waitFor(15, TimeUnit.SECONDS) || pAc.exitValue() != 0) {
                    String err = new String(pAc.getInputStream().readAllBytes());
                    if (pAc.isAlive()) pAc.destroyForcibly();
                    throw new Exception("Biên dịch AC Code thất bại! Output: " + err);
                }

                // 4. Vòng lặp Sinh Input & Lấy Output chuẩn
                List<TestCase> testCases = new ArrayList<>();
                dal.TestCaseDAO tcDAO = new dal.TestCaseDAO();
                int problemId = problem != null ? problem.getId() : 1;
                
                // Xóa testcase cũ của bài này trước khi sinh mới để tránh dồn ứ DB
                tcDAO.deleteTestCasesByProblemId(problemId);
                
                for (int i = 0; i < totalCases; i++) {
                    String currentMode = modeList.get(i);
                    String seed = String.valueOf(System.currentTimeMillis() + i);
                    listener.onProgress(i, totalCases, "Đang sinh Testcase " + (i + 1) + "/" + totalCases + " (Mode: " + currentMode + ")");
                    
                    // Chạy generator
                    ProcessBuilder pbRunGen = new ProcessBuilder(genExe.getAbsolutePath(), seed, currentMode);
                    Process runGen = pbRunGen.start();
                    String generatedInput = new String(runGen.getInputStream().readAllBytes());
                    runGen.waitFor(5, TimeUnit.SECONDS);

                    // Đẩy input vào AC Code để lấy Output
                    ProcessBuilder pbRunAc = new ProcessBuilder(acExe.getAbsolutePath());
                    Process runAc = pbRunAc.start();
                    runAc.getOutputStream().write(generatedInput.getBytes());
                    runAc.getOutputStream().flush();
                    runAc.getOutputStream().close();
                    
                    String expectedOutput = new String(runAc.getInputStream().readAllBytes());
                    runAc.waitFor(5, TimeUnit.SECONDS);

                    // Add TestCase & Lưu DB
                    TestCase tc = new TestCase(0, problemId, generatedInput, expectedOutput, false, currentMode);
                    tcDAO.addTestCase(tc); // Lưu vào cơ sở dữ liệu
                    
                    // Vì DAO có thể không gán ID trực tiếp vào Object, ta cứ add vào List (Trong thực tế cần lấy lại ID tự tăng nếu EvaluationService dùng)
                    // (Tuy nhiên EvaluationService chỉ duyệt mảng không phụ thuộc ID cứng)
                    testCases.add(tc);
                }

                // Dọn dẹp thư mục tạm
                for (File f : tempDir.toFile().listFiles()) f.delete();
                Files.delete(tempDir);

                // 5. Xin AI thêm WA, TLE sample code để Test
                listener.onProgress(totalCases, totalCases, "Đang sinh các Sample Code độc hại (WA, TLE)...");
                List<SampleCode> sampleCodes = new ArrayList<>();
                sampleCodes.add(new SampleCode(problemId, cleanMarkdown(acCode), "cpp", "AC"));
                sampleCodes.add(new SampleCode(problemId, cleanMarkdown(aiService.generateSampleCode(problem, "WA")), "cpp", "WA"));
                sampleCodes.add(new SampleCode(problemId, cleanMarkdown(aiService.generateSampleCode(problem, "TLE")), "cpp", "TLE"));

                // Lưu các Sample Code vừa sinh vào DB
                dal.SampleCodeDAO sampleCodeDAO = new dal.SampleCodeDAO();
                for (SampleCode sc : sampleCodes) {
                    sampleCodeDAO.addSampleCode(sc);
                }

                // 6. Ném vào EvaluationTask để chạy Sandbox đánh giá sức mạnh
                EvaluationTask task = new EvaluationTask(
                    testCases, sampleCodes, checker, 1500L,
                    new EvaluationTask.EvaluationListener() {
                        @Override public void onProgress(int cur, int tot, String status) {
                            listener.onProgress(cur, tot, "Đánh giá Sandbox: " + status);
                        }
                        @Override public void onComplete(EvaluationReport report) {
                            listener.onComplete(report);
                        }
                        @Override public void onError(Exception e) {
                            listener.onError(e);
                        }
                    });
                task.run();

            } catch (Exception e) {
                listener.onError(e);
            }
        }, "AutoPipelineThread").start();
    }

    public void compileAndGenerateTestcases(String generatorCode, String acCode, String checkerCode, Problem problem, int totalCases, EvaluationListener listener) {
        listener.onStart();
        new Thread(() -> {
            try {
                int edgeCount = (int) Math.ceil(totalCases * 0.2);
                int maxCount = (int) Math.ceil(totalCases * 0.2);
                int randomCount = totalCases - edgeCount - maxCount;

                List<String> modeList = new ArrayList<>();
                for (int i = 0; i < edgeCount; i++) modeList.add("edge");
                for (int i = 0; i < maxCount; i++) modeList.add("max");
                for (int i = 0; i < randomCount; i++) modeList.add("random");

                // Tạo thư mục lưu trữ testcase ngay trong dự án
                File workspaceDir = new File("testcases_data/problem_" + problem.getId());
                if (!workspaceDir.exists()) {
                    workspaceDir.mkdirs();
                }
                Path tempDir = workspaceDir.toPath();

                File testlibSrc = new File("lib/testlib.h");
                if (testlibSrc.exists()) {
                    Files.copy(testlibSrc.toPath(), tempDir.resolve("testlib.h"), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new Exception("Không tìm thấy file lib/testlib.h trong dự án!");
                }

                listener.onProgress(0, totalCases, "Đang biên dịch Generator (C++)...");
                File genCpp = new File(tempDir.toFile(), "gen.cpp");
                Files.writeString(genCpp.toPath(), cleanMarkdown(generatorCode));
                File genExe = new File(tempDir.toFile(), "gen.exe");

                ProcessBuilder pbGen = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), genCpp.getAbsolutePath(), "-o", genExe.getAbsolutePath());
                pbGen.redirectErrorStream(true);
                Process pGen = pbGen.start();
                if (!pGen.waitFor(60, TimeUnit.SECONDS) || pGen.exitValue() != 0) {
                    String err = new String(pGen.getInputStream().readAllBytes());
                    if (pGen.isAlive()) pGen.destroyForcibly();
                    throw new Exception("Biên dịch Generator thất bại! Exit: " + (pGen.isAlive() ? "TIMEOUT" : pGen.exitValue()) + " Lỗi: " + err);
                }

                listener.onProgress(0, totalCases, "Đang biên dịch chuẩn AC Code (C++)...");
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                Files.writeString(acCpp.toPath(), cleanMarkdown(acCode));
                File acExe = new File(tempDir.toFile(), "ac.exe");

                ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                pbAc.redirectErrorStream(true);
                Process pAc = pbAc.start();
                if (!pAc.waitFor(60, TimeUnit.SECONDS) || pAc.exitValue() != 0) {
                    String err = new String(pAc.getInputStream().readAllBytes());
                    if (pAc.isAlive()) pAc.destroyForcibly();
                    throw new Exception("Biên dịch AC Code thất bại! Exit: " + (pAc.isAlive() ? "TIMEOUT" : pAc.exitValue()) + " Lỗi: " + err);
                }

                if (problem != null && problem.getId() <= 0) {
                    boolean success = new dal.ProblemDAO().addProblem(problem);
                    if (!success) {
                        throw new Exception("Không thể lưu bài tập vào Database! Vui lòng kiểm tra kết nối CSDL hoặc tiêu đề bài tập.");
                    }
                }

                dal.TestCaseDAO dao = new dal.TestCaseDAO();
                
                // Xóa testcase cũ của bài này trước khi sinh mới để tránh dồn ứ DB
                if (problem != null && problem.getId() > 0) {
                    dao.deleteTestCasesByProblemId(problem.getId());
                } else {
                    throw new Exception("ID bài tập không hợp lệ (ID=0). Không thể lưu Testcase.");
                }

                int successCount = 0;

                for (int i = 0; i < totalCases; i++) {
                    String currentMode = modeList.get(i);
                    String seed = String.valueOf(System.currentTimeMillis() + i);
                    listener.onProgress(i, totalCases, "Đang sinh Testcase " + (i + 1) + "/" + totalCases + " (Mode: " + currentMode + ")");
                    
                    File inputTxt = new File(tempDir.toFile(), "input_" + i + ".txt");
                    File outputTxt = new File(tempDir.toFile(), "output_" + i + ".txt");

                    ProcessBuilder pbRunGen = new ProcessBuilder(genExe.getAbsolutePath(), seed, currentMode);
                    pbRunGen.redirectOutput(inputTxt);
                    Process runGen = pbRunGen.start();
                    
                    if (!runGen.waitFor(300, TimeUnit.SECONDS)) {
                        runGen.destroyForcibly();
                        throw new Exception("Quá thời gian sinh Testcase (5 phút). Có thể file quá lớn hoặc vòng lặp vô hạn!");
                    }
                    String generatedInput = Files.readString(inputTxt.toPath());

                    ProcessBuilder pbRunAc = new ProcessBuilder(acExe.getAbsolutePath());
                    pbRunAc.redirectInput(inputTxt);
                    pbRunAc.redirectOutput(outputTxt);
                    Process runAc = pbRunAc.start();
                    
                    if (!runAc.waitFor(300, TimeUnit.SECONDS)) {
                        runAc.destroyForcibly();
                        throw new Exception("Quá thời gian thực thi mã chuẩn AC chạy Testcase (5 phút).");
                    }
                    String expectedOutput = Files.readString(outputTxt.toPath());

                    // Insert vào CSDL
                    TestCase tc = new TestCase();
                    tc.setProblemId(problem.getId());
                    tc.setInputData(generatedInput);
                    tc.setExpectedOutput(expectedOutput);
                    tc.setStrengthStatus(currentMode.toUpperCase()); // Ghi rõ: EDGE, MAX, RANDOM
                    if(dao.addTestCase(tc)) {
                        successCount++;
                    }
                }

                // Không xóa file đi nữa để người dùng có thể xem lại dữ liệu thô trên ổ cứng
                // (Chỉ cân nhắc xóa file .exe để dọn dẹp)
                new File(tempDir.toFile(), "gen.exe").delete();
                new File(tempDir.toFile(), "ac.exe").delete();

                listener.onProgress(totalCases, totalCases, "Đã lưu " + successCount + "/" + totalCases + " Testcases vào Database.");
                listener.onComplete(null);

            } catch (Exception e) {
                listener.onError(e);
            }
        }, "GenerateTestcasesThread").start();
    }

    private String cleanMarkdown(String code) {
        if (code == null) return "";
        code = code.trim();
        // Regex để loại bỏ tất cả text bên ngoài block code ```cpp ... ```
        if (code.contains("```")) {
            int startCode = code.indexOf("```");
            int firstNewline = code.indexOf('\n', startCode);
            int endCode = code.lastIndexOf("```");
            if (firstNewline != -1 && endCode > firstNewline) {
                return code.substring(firstNewline + 1, endCode).trim();
            }
        }
        return code.trim();
    }

    /**
     * Chạy bộ kiểm thử với mock data có sẵn.
     * onStart() gọi đồng bộ trên luồng hiện tại (EDT).
     * Các callback còn lại gọi từ background thread — View tự bọc SwingUtilities nếu cần.
     */
    public void runEvaluation(Problem problem, String checkerCode, String acCode, String waCode, String tleCode, EvaluationListener listener) {
        listener.onStart();

        // Lấy Testcase từ Database theo id của bài tập hiện tại (Problem)
        dal.TestCaseDAO testCaseDAO = new dal.TestCaseDAO();
        int problemId = problem != null ? problem.getId() : 1;
        List<TestCase> testCases = testCaseDAO.getTestCasesByProblemId(problemId);

        // Đảm bảo testCases không null
        if (testCases == null) {
            testCases = new ArrayList<>();
        }

        // SampleCode: dùng dữ liệu thực từ các trường input
        List<SampleCode> sampleCodes = buildSampleCodes(acCode, waCode, tleCode);

        // Checker: dùng checker code thực nếu có, ngược lại null (so khớp chính xác)
        Checker checker = buildChecker(checkerCode);

        EvaluationTask task = new EvaluationTask(
            testCases, sampleCodes, checker, 1500L,
            new EvaluationTask.EvaluationListener() {
                @Override public void onProgress(int cur, int tot, String status) {
                    listener.onProgress(cur, tot, status);
                }
                @Override public void onComplete(EvaluationReport report) {
                    listener.onComplete(report);
                }
                @Override public void onError(Exception e) {
                    listener.onError(e);
                }
            });

        new Thread(task, "EvaluationThread").start();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Trả về Checker thực nếu checkerCode hợp lệ, ngược lại null. */
    private Checker buildChecker(String checkerCode) {
        if (isBlank(checkerCode)) return null;
        Checker checker = new Checker(cleanMarkdown(checkerCode), "cpp");
        if (!checker.isValid()) return null;
        return checker;
    }

    /** Xây dựng danh sách SampleCode từ code thực. */
    private List<SampleCode> buildSampleCodes(String acCode, String waCode, String tleCode) {
        List<SampleCode> list = new ArrayList<>();
        String lang = "cpp";

        if (!isBlank(acCode)) {
            list.add(new SampleCode(cleanMarkdown(acCode), lang, "AC"));
        }

        if (!isBlank(waCode)) {
            list.add(new SampleCode(cleanMarkdown(waCode), lang, "WA"));
        }

        if (!isBlank(tleCode)) {
            list.add(new SampleCode(cleanMarkdown(tleCode), lang, "TLE"));
        }

        return list;
    }
}
