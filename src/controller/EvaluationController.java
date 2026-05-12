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

                    // Add TestCase
                    testCases.add(new TestCase(i + 1, problem != null ? problem.getId() : 1, generatedInput, expectedOutput, false, currentMode));
                }

                // Dọn dẹp thư mục tạm
                for (File f : tempDir.toFile().listFiles()) f.delete();
                Files.delete(tempDir);

                // 5. Xin AI thêm WA, TLE sample code để Test
                listener.onProgress(totalCases, totalCases, "Đang sinh các Sample Code độc hại (WA, TLE)...");
                List<SampleCode> sampleCodes = new ArrayList<>();
                sampleCodes.add(new SampleCode(cleanMarkdown(acCode), "cpp", "AC"));
                sampleCodes.add(new SampleCode(cleanMarkdown(aiService.generateSampleCode(problem, "WA")), "cpp", "WA"));
                sampleCodes.add(new SampleCode(cleanMarkdown(aiService.generateSampleCode(problem, "TLE")), "cpp", "TLE"));

                // 6. Ném vào EvaluationTask để chạy Sandbox đánh giá sức mạnh
                EvaluationTask task = new EvaluationTask(
                    testCases, sampleCodes, null, 1500L,
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

    public void compileAndGenerateTestcases(String generatorCode, String acCode, int problemId, int totalCases, EvaluationListener listener) {
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
                File workspaceDir = new File("testcases_data/problem_" + problemId);
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

                dal.TestCaseDAO dao = new dal.TestCaseDAO();
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
                    tc.setProblemId(problemId);
                    tc.setInputData(generatedInput);
                    tc.setExpectedOutput(expectedOutput);
                    tc.setStrengthStatus("Normal");
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
    public void runEvaluation(String checkerCode, String acCode, String waCode, EvaluationListener listener) {
        listener.onStart();

        boolean usingReal = !isBlank(checkerCode) || !isBlank(acCode) || !isBlank(waCode);

        // Lấy Testcase từ Database thật thay vì dùng hàm Mock
        dal.TestCaseDAO testCaseDAO = new dal.TestCaseDAO();
        // Giả sử lấy Testcase của problemId = 1 (Bạn có thể truyền param problemId từ UI xuống sau này)
        List<TestCase> testCases = testCaseDAO.getTestCasesByProblemId(1); 
        
        // Nếu DB chưa có dữ liệu, fallback về Mock để hệ thống không bị lỗi crash màn hình
        // if (testCases == null || testCases.isEmpty()) {
        //     System.out.println("CẢNH BÁO: Database không có Testcase nào cho Problem ID 1. Fallback về Mock TestCases.");
        //     testCases = buildMockTestCases();
        // }

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
