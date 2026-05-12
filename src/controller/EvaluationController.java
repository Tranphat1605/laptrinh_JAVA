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

    // Danh sách lưu tạm Testcase trên bộ nhớ
    private List<TestCase> pendingTestCases = new ArrayList<>();

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
     * Pipeline sinh Testcase Tự động và đánh giá (Dùng cho luồng Auto-Generate)
     */
    public void runAutomatedEvaluation(Problem problem, AIService aiService, int totalCases, EvaluationListener listener) {
        listener.onStart();

        new Thread(() -> {
            try {
                // 1. Phân bổ số lượng Testcase
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
                File testlibSrc = new File("lib/testlib.h");
                if (testlibSrc.exists()) {
                    Files.copy(testlibSrc.toPath(), tempDir.resolve("testlib.h"), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new Exception("Không tìm thấy file lib/testlib.h trong dự án!");
                }

                // 2. Biên dịch Generator (Thử tối đa 3 lần với AI Self-Fix)
                File genCpp = new File(tempDir.toFile(), "gen.cpp");
                File genExe = new File(tempDir.toFile(), "gen.exe");
                String currentGenCode = generatorCode;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    listener.onProgress(0, totalCases, "Biên dịch Generator (Lần " + attempt + ")...");
                    Files.writeString(genCpp.toPath(), injectTestlib(cleanMarkdown(currentGenCode)));
                    File genErrFile = new File(tempDir.toFile(), "gen_err.txt");
                    ProcessBuilder pbGen = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), genCpp.getAbsolutePath(), "-o", genExe.getAbsolutePath());
                    pbGen.redirectErrorStream(true);
                    pbGen.redirectOutput(genErrFile);
                    Process pGen = pbGen.start();
                    if (pGen.waitFor(15, TimeUnit.SECONDS) && pGen.exitValue() == 0) break;
                    String errMsg = genErrFile.exists() ? Files.readString(genErrFile.toPath()) : "Lỗi không xác định";
                    if (attempt < 3) {
                        listener.onProgress(0, totalCases, "AI đang tự sửa lỗi Generator...");
                        currentGenCode = aiService.fixCodeWithAI(currentGenCode, errMsg, problem, "generator");
                    } else throw new Exception("Biên dịch Generator thất bại sau 3 lần thử! Lỗi: " + errMsg);
                }

                // 3. Biên dịch AC Code (Thử tối đa 3 lần với AI Self-Fix)
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                File acExe = new File(tempDir.toFile(), "ac.exe");
                String currentAcCode = acCode;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    listener.onProgress(0, totalCases, "Biên dịch AC Code (Lần " + attempt + ")...");
                    Files.writeString(acCpp.toPath(), cleanMarkdown(currentAcCode));
                    File acErrFile = new File(tempDir.toFile(), "ac_err.txt");
                    ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                    pbAc.redirectErrorStream(true);
                    pbAc.redirectOutput(acErrFile);
                    Process pAc = pbAc.start();
                    if (pAc.waitFor(15, TimeUnit.SECONDS) && pAc.exitValue() == 0) break;
                    String errMsg = acErrFile.exists() ? Files.readString(acErrFile.toPath()) : "Lỗi không xác định";
                    if (attempt < 3) {
                        listener.onProgress(0, totalCases, "AI đang tự sửa lỗi AC Code...");
                        currentAcCode = aiService.fixCodeWithAI(currentAcCode, errMsg, problem, "solution");
                    } else throw new Exception("Biên dịch AC Code thất bại sau 3 lần thử! Lỗi: " + errMsg);
                }

                // 4. Vòng lặp Sinh Input & Lấy Output chuẩn
                List<TestCase> testCases = new ArrayList<>();
                dal.TestCaseDAO tcDAO = new dal.TestCaseDAO();
                int problemId = problem != null ? problem.getId() : 1;
                
                for (int i = 0; i < totalCases; i++) {
                    String currentMode = modeList.get(i);
                    String seed = String.valueOf(System.currentTimeMillis() + i);
                    listener.onProgress(i, totalCases, "Đang sinh Testcase " + (i + 1) + "/" + totalCases + " (Mode: " + currentMode + ")");
                    
                    File genOutFile = new File(tempDir.toFile(), "gen_out.txt");
                    ProcessBuilder pbRunGen = new ProcessBuilder(genExe.getAbsolutePath(), seed, currentMode);
                    pbRunGen.redirectOutput(genOutFile);
                    Process runGen = pbRunGen.start();
                    if (!runGen.waitFor(5, TimeUnit.SECONDS)) throw new Exception("Generator bị treo!");
                    
                    String generatedInput = Files.readString(genOutFile.toPath()).replace("\r", "");
                    File cleanInFile = new File(tempDir.toFile(), "input_clean.txt");
                    Files.writeString(cleanInFile.toPath(), generatedInput);

                    File acOutFile = new File(tempDir.toFile(), "ac_out.txt");
                    ProcessBuilder pbRunAc = new ProcessBuilder(acExe.getAbsolutePath());
                    pbRunAc.redirectInput(cleanInFile);
                    pbRunAc.redirectOutput(acOutFile);
                    Process runAc = pbRunAc.start();
                    if (!runAc.waitFor(5, TimeUnit.SECONDS)) throw new Exception("AC Code bị treo!");
                    
                    String expectedOutput = Files.readString(acOutFile.toPath()).replace("\r", "");
                    TestCase tc = new TestCase(0, problemId, generatedInput, expectedOutput, false, currentMode);
                    tcDAO.addTestCase(tc);
                    testCases.add(tc);
                }

                // Dọn dẹp
                for (File f : tempDir.toFile().listFiles()) f.delete();
                Files.delete(tempDir);

                // 5. Xin AI thêm WA, TLE sample code để Test
                listener.onProgress(totalCases, totalCases, "Đang sinh các Sample Code độc hại (WA, TLE)...");
                List<SampleCode> sampleCodes = new ArrayList<>();
                sampleCodes.add(new SampleCode(problemId, cleanMarkdown(acCode), "cpp", "AC"));
                sampleCodes.add(new SampleCode(problemId, cleanMarkdown(aiService.generateSampleCode(problem, "WA")), "cpp", "WA"));
                sampleCodes.add(new SampleCode(problemId, cleanMarkdown(aiService.generateSampleCode(problem, "TLE")), "cpp", "TLE"));

                dal.SampleCodeDAO sampleCodeDAO = new dal.SampleCodeDAO();
                for (SampleCode sc : sampleCodes) sampleCodeDAO.addSampleCode(sc);

                EvaluationTask task = new EvaluationTask(testCases, sampleCodes, checker, 5000L, new EvaluationTask.EvaluationListener() {
                    @Override public void onProgress(int cur, int tot, String status) { listener.onProgress(cur, tot, "Đánh giá Sandbox: " + status); }
                    @Override public void onComplete(EvaluationReport report) { listener.onComplete(report); }
                    @Override public void onError(Exception e) { listener.onError(e); }
                });
                task.run();
            } catch (Exception e) { listener.onError(e); }
        }, "AutoPipelineThread").start();
    }

    /**
     * Chạy luồng sinh Testcase từ giao diện Giáo viên (Bước 4)
     */
    public void compileAndGenerateTestcases(AIService aiService, String generatorCode, String acCode, Problem problem, int totalCases, EvaluationListener listener) {
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
                } else throw new Exception("Không tìm thấy file lib/testlib.h!");

                // 1. Biên dịch Generator (Thử tối đa 3 lần với AI Self-Fix)
                File genCpp = new File(tempDir.toFile(), "gen.cpp");
                File genExe = new File(tempDir.toFile(), "gen.exe");
                String currentGenCode = generatorCode;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    listener.onProgress(0, totalCases, "Biên dịch Generator (Lần " + attempt + ")...");
                    Files.writeString(genCpp.toPath(), injectTestlib(cleanMarkdown(currentGenCode)));
                    File genErrFile = new File(tempDir.toFile(), "gen_err.txt");
                    ProcessBuilder pbGen = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), genCpp.getAbsolutePath(), "-o", genExe.getAbsolutePath());
                    pbGen.redirectErrorStream(true);
                    pbGen.redirectOutput(genErrFile);
                    Process pGen = pbGen.start();
                    if (pGen.waitFor(15, TimeUnit.SECONDS) && pGen.exitValue() == 0) break;
                    String errMsg = genErrFile.exists() ? Files.readString(genErrFile.toPath()) : "Lỗi không xác định";
                    if (attempt < 3) {
                        listener.onProgress(0, totalCases, "AI đang tự sửa lỗi Generator...");
                        currentGenCode = aiService.fixCodeWithAI(currentGenCode, errMsg, problem, "generator");
                    } else throw new Exception("Biên dịch Generator thất bại sau 3 lần thử! Lỗi: " + errMsg);
                }

                // 2. Biên dịch AC Code (Thử tối đa 3 lần với AI Self-Fix)
                File acCpp = new File(tempDir.toFile(), "ac.cpp");
                File acExe = new File(tempDir.toFile(), "ac.exe");
                String currentAcCode = acCode;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    listener.onProgress(0, totalCases, "Biên dịch AC Code (Lần " + attempt + ")...");
                    Files.writeString(acCpp.toPath(), cleanMarkdown(currentAcCode));
                    File acErrFile = new File(tempDir.toFile(), "ac_err.txt");
                    ProcessBuilder pbAc = new ProcessBuilder("g++", "-O2", "-std=c++17", "-I", tempDir.toAbsolutePath().toString(), acCpp.getAbsolutePath(), "-o", acExe.getAbsolutePath());
                    pbAc.redirectErrorStream(true);
                    pbAc.redirectOutput(acErrFile);
                    Process pAc = pbAc.start();
                    if (pAc.waitFor(15, TimeUnit.SECONDS) && pAc.exitValue() == 0) break;
                    String errMsg = acErrFile.exists() ? Files.readString(acErrFile.toPath()) : "Lỗi không xác định";
                    if (attempt < 3) {
                        listener.onProgress(0, totalCases, "AI đang tự sửa lỗi AC Code...");
                        currentAcCode = aiService.fixCodeWithAI(currentAcCode, errMsg, problem, "solution");
                    } else throw new Exception("Biên dịch AC Code thất bại sau 3 lần thử! Lỗi: " + errMsg);
                }

                // Đảm bảo Problem có ID hợp lệ
                dal.ProblemDAO problemDao = new dal.ProblemDAO();
                int safeProblemId = (problem != null && problem.getId() > 0) ? problem.getId() : 1;
                if (problemDao.getProblemById(safeProblemId) == null) {
                    entity.Problem dbP = new entity.Problem(0, "Bài tập AI", "Content", 2000, 256, "AI");
                    problemDao.addProblem(dbP);
                    safeProblemId = problemDao.getAllProblems().get(0).getId();
                }

                pendingTestCases.clear();
                for (int i = 0; i < totalCases; i++) {
                    String mode = modeList.get(i);
                    listener.onProgress(i, totalCases, "Đang sinh Testcase " + (i + 1) + " (Mode: " + mode + ")");
                    File genOut = new File(tempDir.toFile(), "gen_out.txt");
                    new ProcessBuilder(genExe.getAbsolutePath(), String.valueOf(System.currentTimeMillis() + i), mode).redirectOutput(genOut).start().waitFor();
                    String input = Files.readString(genOut.toPath()).replace("\r", "");
                    
                    File inClean = new File(tempDir.toFile(), "in_clean.txt");
                    Files.writeString(inClean.toPath(), input);
                    File acOut = new File(tempDir.toFile(), "ac_out.txt");
                    new ProcessBuilder(acExe.getAbsolutePath()).redirectInput(inClean).redirectOutput(acOut).start().waitFor();
                    String output = Files.readString(acOut.toPath()).replace("\r", "");

                    TestCase tc = new TestCase(0, safeProblemId, input, output, false, mode.toUpperCase());
                    pendingTestCases.add(tc);
                }
                listener.onProgress(totalCases, totalCases, "Hoàn tất sinh " + totalCases + " testcases.");
                listener.onComplete(null);
            } catch (Exception e) { listener.onError(e); }
        }, "GenerateTestcasesThread").start();
    }

    private String cleanMarkdown(String code) {
        if (code == null) return "";
        code = code.trim();
        if (code.startsWith("```")) {
            int firstNewline = code.indexOf('\n');
            if (firstNewline != -1) code = code.substring(firstNewline + 1);
            if (code.endsWith("```")) code = code.substring(0, code.length() - 3);
        }
        return code.trim();
    }

    private String injectTestlib(String code) {
        if (code == null || code.contains("testlib.h")) return code;
        String[] lines = code.split("\n");
        int lastInc = -1;
        for (int i = 0; i < lines.length; i++) if (lines[i].trim().startsWith("#include")) lastInc = i;
        StringBuilder sb = new StringBuilder();
        if (lastInc >= 0) {
            for (int i = 0; i <= lastInc; i++) sb.append(lines[i]).append("\n");
            sb.append("#include \"testlib.h\"\n");
            for (int i = lastInc + 1; i < lines.length; i++) sb.append(lines[i]).append("\n");
        } else sb.append("#include <bits/stdc++.h>\n#include \"testlib.h\"\n").append(code);
        return sb.toString().trim();
    }

    public void runEvaluation(Problem problem, String checkerCode, String acCode, String waCode, EvaluationListener listener) {
        listener.onStart();
        List<TestCase> testCases = (pendingTestCases != null && !pendingTestCases.isEmpty()) ? new ArrayList<>(pendingTestCases) : new dal.TestCaseDAO().getTestCasesByProblemId(problem != null ? problem.getId() : 1);
        if (testCases == null || testCases.isEmpty()) { listener.onError(new Exception("Chưa có Testcase!")); return; }
        
        List<SampleCode> sampleCodes = new ArrayList<>();
        sampleCodes.add(new SampleCode(cleanMarkdown(acCode), "cpp", "AC"));
        if (waCode != null && !waCode.isEmpty()) sampleCodes.add(new SampleCode(cleanMarkdown(waCode), "cpp", "WA"));

        Checker checker = (checkerCode == null || checkerCode.isEmpty()) ? null : new Checker(cleanMarkdown(checkerCode), "cpp");
        EvaluationTask task = new EvaluationTask(testCases, sampleCodes, checker, 5000L, new EvaluationTask.EvaluationListener() {
            @Override public void onProgress(int cur, int tot, String status) { listener.onProgress(cur, tot, status); }
            @Override public void onComplete(EvaluationReport report) { listener.onComplete(report); }
            @Override public void onError(Exception e) { listener.onError(e); }
        });
        new Thread(task, "EvaluationThread").start();
    }

    public boolean savePendingTestCasesToDB() {
        if (pendingTestCases == null || pendingTestCases.isEmpty()) return false;
        dal.TestCaseDAO dao = new dal.TestCaseDAO();
        int pId = pendingTestCases.get(0).getProblemId();
        dao.deleteTestCasesByProblemId(pId);
        int ok = 0;
        for (TestCase tc : pendingTestCases) if (dao.addTestCase(tc)) ok++;
        return ok == pendingTestCases.size();
    }
}
