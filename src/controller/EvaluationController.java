package controller;

import bll.AIService;
import bll.EvaluationTask;
import entity.EvaluationReport;
import entity.Problem;
import entity.SampleCode;
import entity.TestCase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller điều phối logic đánh giá chất lượng Testcase.
 * Chứa mock data mẫu và khởi chạy EvaluationTask.
 * View (TestcaseEvaluationFrame) chỉ cần gọi runEvaluation() và implement EvaluationListener.
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

                ProcessBuilder pbGen = new ProcessBuilder("g++", "-O2", "-std=c++17", genCpp.getAbsolutePath(), "-o", genExe.getAbsolutePath());
                Process pGen = pbGen.start();
                if (!pGen.waitFor(15, TimeUnit.SECONDS) || pGen.exitValue() != 0) {
                    throw new Exception("Biên dịch Generator thất bại! Vui lòng kiểm tra lại code AI sinh ra.");
                }

                // 3. Biên dịch AC Code (C++)
                listener.onProgress(0, totalCases, "Đang biên dịch Solution chuẩn (AC)...");
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                Files.writeString(acCpp.toPath(), cleanMarkdown(acCode));
                File acExe = new File(tempDir.toFile(), "ac.exe");

                ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                Process pAc = pbAc.start();
                if (!pAc.waitFor(15, TimeUnit.SECONDS) || pAc.exitValue() != 0) {
                    throw new Exception("Biên dịch AC Code thất bại!");
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

    private String cleanMarkdown(String code) {
        if (code.startsWith("```")) {
            String[] lines = code.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].startsWith("```")) break;
                sb.append(lines[i]).append("\n");
            }
            return sb.toString();
        }
        return code;
    }

    /**
     * Chạy bộ kiểm thử với mock data có sẵn.
     * onStart() gọi đồng bộ trên luồng hiện tại (EDT).
     * Các callback còn lại gọi từ background thread — View tự bọc SwingUtilities nếu cần.
     */
    public void runEvaluation(EvaluationListener listener) {
        listener.onStart();

        List<TestCase> testCases = buildMockTestCases();
        List<SampleCode> sampleCodes = buildMockSampleCodes();

        EvaluationTask task = new EvaluationTask(
            testCases, sampleCodes, null, 1500L,
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

    private List<TestCase> buildMockTestCases() {
        List<TestCase> list = new ArrayList<>();
        list.add(new TestCase(1, 101, "5\n",  "10", false, "Normal"));
        list.add(new TestCase(2, 101, "12\n", "24", false, "Normal"));
        return list;
    }

    private List<SampleCode> buildMockSampleCodes() {
        List<SampleCode> list = new ArrayList<>();

        // AC: nhân 2 đúng
        list.add(new SampleCode(
            "import java.util.Scanner;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 2);\n" +
            "    }\n" +
            "}\n",
            "java", "AC"));

        // WA: nhân 3 sai logic
        list.add(new SampleCode(
            "import java.util.Scanner;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 3);\n" +
            "    }\n" +
            "}\n",
            "java", "WA"));

        // TLE: vòng lặp vô hạn
        list.add(new SampleCode(
            "public class Main {\n" +
            "    public static void main(String[] args) { while (true) {} }\n" +
            "}\n",
            "java", "TLE"));

        return list;
    }
}
