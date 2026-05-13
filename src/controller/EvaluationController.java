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
 * Controller điều phối logic đánh giá chất lượng Testcase.
 * Nhận dữ liệu thực từ TeacherFrame (checker, ac, wa code) hoặc
 * dùng mock data mặc định khi các trường được để trống.
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

                listener.onProgress(0, totalCases, "Đang gọi AI sinh Checker Code (C++)...");
                String checkerCodeRaw = aiService.generateChecker(problem);
                
                // Lưu Checker vào Database
                entity.Checker checker = new entity.Checker(0, problem != null ? problem.getId() : 1, cleanMarkdown(checkerCodeRaw), "cpp");
                dal.CheckerDAO checkerDAO = new dal.CheckerDAO();
                int checkerId = checkerDAO.addChecker(checker);
                if (checkerId > 0) checker.setId(checkerId);

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
                Process pGen = pbGen.start();
                if (!pGen.waitFor(15, TimeUnit.SECONDS) || pGen.exitValue() != 0) {
                    throw new Exception("Biên dịch Generator thất bại! Vui lòng kiểm tra lại code AI sinh ra.");
                }

                // 3. Biên dịch AC Code (C++)
                listener.onProgress(0, totalCases, "Đang biên dịch Solution chuẩn (AC)...");
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                Files.writeString(acCpp.toPath(), cleanMarkdown(acCode));
                File acExe = new File(tempDir.toFile(), "ac.exe");

                ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                Process pAc = pbAc.start();
                if (!pAc.waitFor(15, TimeUnit.SECONDS) || pAc.exitValue() != 0) {
                    throw new Exception("Biên dịch AC Code thất bại!");
                }

                // 4. Vòng lặp Sinh Input & Lấy Output chuẩn
                List<TestCase> testCases = new ArrayList<>();
                dal.TestCaseDAO tcDAO = new dal.TestCaseDAO();
                int problemId = problem != null ? problem.getId() : 1;
                
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

    public void compileAndGenerateTestcases(String generatorCode, String acCode, String checkerCode, String waCode, Problem problem, int totalCases, EvaluationListener listener) {
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

                Path tempDir = Files.createTempDirectory("testcase_gen_");
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
                Process pGen = pbGen.start();
                if (!pGen.waitFor(15, TimeUnit.SECONDS) || pGen.exitValue() != 0) {
                    throw new Exception("Biên dịch Generator thất bại! Lỗi: " + new String(pGen.getErrorStream().readAllBytes()));
                }

                listener.onProgress(0, totalCases, "Đang biên dịch chuẩn AC Code (C++)...");
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                Files.writeString(acCpp.toPath(), cleanMarkdown(acCode));
                File acExe = new File(tempDir.toFile(), "ac.exe");

                ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                Process pAc = pbAc.start();
                if (!pAc.waitFor(15, TimeUnit.SECONDS) || pAc.exitValue() != 0) {
                    throw new Exception("Biên dịch AC Code thất bại! Lỗi: " + new String(pAc.getErrorStream().readAllBytes()));
                }

                // Bảo đảm có tham chiếu Problem hợp lệ trong DB để không bị lỗi Khóa Ngoại (Foreign Key)
                dal.ProblemDAO problemDao = new dal.ProblemDAO();
                java.util.List<entity.Problem> probs = problemDao.getAllProblems();
                int safeProblemId = problem != null ? problem.getId() : 1;
                
                // Nếu chưa có, ta tự sinh Problem từ dữ liệu AI đã phân tích được
                if (probs.isEmpty() || safeProblemId == 0) {
                    entity.Problem dbProblem = new entity.Problem(0, 
                        problem != null && problem.getTitle() != null ? problem.getTitle() : "Bài tập chưa phân loại (Auto Gen)", 
                        problem != null && problem.getContent() != null ? problem.getContent() : "Được tạo tự động bởi Hệ thống AI", 
                        problem != null ? problem.getTimeLimitMs() : 2000, 
                        problem != null ? problem.getMemoryLimitMb() : 256, 
                        "AI Sandbox"
                    );
                    problemDao.addProblem(dbProblem);
                    probs = problemDao.getAllProblems();
                    if (!probs.isEmpty()) {
                        safeProblemId = probs.get(probs.size() - 1).getId();
                        if (problem != null) {
                            problem.setId(safeProblemId); // Cập nhật lại ID cho Frontend sử dụng khi lấy List<TestCase>
                        }
                    }
                }

                // --- MỚI THÊM: LƯU CHECKER VÀ CODE MẪU (AC, WA) VÀO DATABASE ---
                if (checkerCode != null && !checkerCode.trim().isEmpty() && !checkerCode.startsWith("//")) {
                    dal.CheckerDAO checkerDao = new dal.CheckerDAO();
                    entity.Checker checker = new entity.Checker(safeProblemId, cleanMarkdown(checkerCode), "cpp");
                    checkerDao.addChecker(checker);
                }
                
                dal.SampleCodeDAO sampleDao = new dal.SampleCodeDAO();
                if (acCode != null && !acCode.trim().isEmpty() && !acCode.startsWith("Đang")) {
                    sampleDao.addSampleCode(new entity.SampleCode(safeProblemId, cleanMarkdown(acCode), "cpp", "AC"));
                }
                if (waCode != null && !waCode.trim().isEmpty() && !waCode.startsWith("Đang")) {
                    sampleDao.addSampleCode(new entity.SampleCode(safeProblemId, cleanMarkdown(waCode), "cpp", "WA"));
                }
                // -------------------------------------------------------------

                dal.TestCaseDAO dao = new dal.TestCaseDAO();
                int successCount = 0;

                for (int i = 0; i < totalCases; i++) {
                    String currentMode = modeList.get(i);
                    String seed = String.valueOf(System.currentTimeMillis() + i);
                    listener.onProgress(i, totalCases, "Đang sinh Testcase " + (i + 1) + "/" + totalCases + " (Mode: " + currentMode + ")");
                    
                    // 1. Chạy Generator (Ghi thẳng ra file để chống lag/deadlock buffer)
                    File genOutFile = new File(tempDir.toFile(), "gen_out.txt");
                    ProcessBuilder pbRunGen = new ProcessBuilder(genExe.getAbsolutePath(), seed, currentMode);
                    pbRunGen.redirectOutput(genOutFile);
                    Process runGen = pbRunGen.start();
                    
                    if (!runGen.waitFor(2, TimeUnit.SECONDS)) {  // Fast-fail sau 2s
                        runGen.destroyForcibly();
                        throw new Exception("Code Generator (Bước 2) bị treo hoặc chạy quá 2s! Vui lòng tự SỬA BẰNG TAY mã C++ trên màn hình thay vì gọi AI để đỡ tốn Quota.");
                    }
                    String generatedInput = Files.readString(genOutFile.toPath());

                    // 2. Chạy AC Code để ra Output chuẩn
                    File acOutFile = new File(tempDir.toFile(), "ac_out.txt");
                    ProcessBuilder pbRunAc = new ProcessBuilder(acExe.getAbsolutePath());
                    pbRunAc.redirectInput(genOutFile); // Đọc input trực tiếp từ file gen
                    pbRunAc.redirectOutput(acOutFile); // Ghi output thẳng ra file
                    Process runAc = pbRunAc.start();
                    
                    if (!runAc.waitFor(2, TimeUnit.SECONDS)) { // Fast-fail sau 2s
                        runAc.destroyForcibly();
                        throw new Exception("Code Mẫu AC (Bước 3) chạy quá giới hạn 2 giây (TLE)! Tự SỬA LẠI TAY thuật toán cho tối ưu hơn trên giao diện nhé.");
                    }
                    String expectedOutput = Files.readString(acOutFile.toPath());

                    // Insert vào CSDL
                    TestCase tc = new TestCase();
                    tc.setProblemId(safeProblemId);
                    tc.setInputData(generatedInput);
                    tc.setExpectedOutput(expectedOutput);
                    tc.setStrengthStatus(currentMode.toUpperCase()); // Ghi rõ: EDGE, MAX, RANDOM
                    if(dao.addTestCase(tc)) {
                        successCount++;
                    }
                }

                for (File f : tempDir.toFile().listFiles()) f.delete();
                Files.delete(tempDir);

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
        // Remove markdown wrapper if it exists (e.g., ```cpp ... ```)
        if (code.startsWith("```")) {
            // Find the end of the first line (e.g., ```cpp)
            int firstNewline = code.indexOf('\n');
            if (firstNewline != -1) {
                code = code.substring(firstNewline + 1);
            }
            // Remove the closing ``` if it exists at the end
            if (code.endsWith("```")) {
                code = code.substring(0, code.length() - 3);
            }
        }
        return code.trim();
    }

    /**
     * Chạy bộ kiểm thử với mock data có sẵn.
     * onStart() gọi đồng bộ trên luồng hiện tại (EDT).
     * Các callback còn lại gọi từ background thread — View tự bọc SwingUtilities nếu cần.
     */
    public void runEvaluation(Problem problem, String checkerCode, String acCode, String waCode, EvaluationListener listener) {
        listener.onStart();

        boolean usingReal = !isBlank(checkerCode) || !isBlank(acCode) || !isBlank(waCode);

        // Lấy Testcase từ Database theo id của bài tập hiện tại (Problem)
        dal.TestCaseDAO testCaseDAO = new dal.TestCaseDAO();
        int problemId = problem != null ? problem.getId() : 1;
        List<TestCase> testCases = testCaseDAO.getTestCasesByProblemId(problemId);

        // Đảm bảo testCases không null
        if (testCases == null) {
            testCases = new ArrayList<>();
        }

        // SampleCode: dùng dữ liệu thực nếu có, ngược lại fallback mock
        List<SampleCode> sampleCodes = buildSampleCodes(acCode, waCode, usingReal);

        // Checker: dùng checker code thực nếu có, ngược lại null (so khớp chính xác)
        Checker checker = buildChecker(checkerCode, usingReal);

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
    private Checker buildChecker(String checkerCode, boolean usingReal) {
        if (isBlank(checkerCode)) return null;
        String lang = usingReal ? "cpp" : "java";
        return new Checker(cleanMarkdown(checkerCode), lang);
    }

    /** Xây dựng danh sách SampleCode từ code thực hoặc mock. */
    private List<SampleCode> buildSampleCodes(String acCode, String waCode, boolean usingReal) {
        List<SampleCode> list = new ArrayList<>();
        String lang = usingReal ? "cpp" : "java";

        if (!isBlank(acCode)) {
            list.add(new SampleCode(cleanMarkdown(acCode), lang, "AC"));
        } else {
            // Mock AC: nhân 2 đúng
            list.add(new SampleCode(
                "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 2);\n" +
                "    }\n" +
                "}\n",
                "java", "AC"));
        }

        if (!isBlank(waCode)) {
            list.add(new SampleCode(cleanMarkdown(waCode), lang, "WA"));
        } else {
            // Mock WA: nhân 3 sai logic
            list.add(new SampleCode(
                "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 3);\n" +
                "    }\n" +
                "}\n",
                "java", "WA"));

            // Mock TLE (chỉ thêm khi dùng toàn mock, không thêm khi có code thực)
            list.add(new SampleCode(
                "public class Main {\n" +
                "    public static void main(String[] args) { while (true) {} }\n" +
                "}\n",
                "java", "TLE"));
        }

        return list;
    }

    /** Mock testcase chuẩn — sẽ mở rộng kết nối DB. */
    private List<TestCase> buildMockTestCases() {
        List<TestCase> list = new ArrayList<>();
        list.add(new TestCase(1, 101, "5\n",  "10", false, "Normal"));
        list.add(new TestCase(2, 101, "12\n", "24", false, "Normal"));
        return list;
    }
}
