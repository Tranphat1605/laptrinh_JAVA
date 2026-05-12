package bll;

import bll.executor.CodeExecutor;
import bll.executor.CodeExecutorFactory;
import entity.ExecutionResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class SandboxService {

    /**
     * Chạy code với luồng thực thi đầy đủ
     */
    public ExecutionResult executeCode(String code, String language, String input, long timeLimitMs) {
        try {
            CodeExecutor executor = CodeExecutorFactory.getExecutor(language);
            
            if (executor == null) {
                return new ExecutionResult("CE", "", "Unsupported language: " + language, 0, -1);
            }
            
            Path tempDir = Files.createTempDirectory("sandbox");
            return executor.execute(tempDir, code, input, timeLimitMs);
            
        } catch (Exception e) {
            return new ExecutionResult("RTE", "", e.getMessage(), 0, -1);
        }
    }

    /**
     * Biên dịch custom checker 1 lần duy nhất
     */
    public ExecutionResult compileChecker(String checkerCode) {
        try {
            Path tempDir = Files.createTempDirectory("checker_compile");
            File tempFolder = tempDir.toFile();
            
            File sourceFile = new File(tempFolder, "checker.cpp");
            Files.writeString(sourceFile.toPath(), checkerCode);
            
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            File exeFile = new File(tempFolder, isWindows ? "checker.exe" : "checker");
            
            String rootDir = System.getProperty("user.dir");
            File libDir = new File(rootDir, "lib");

            ProcessBuilder compilePb = new ProcessBuilder(
                    "g++", "-O2", "-std=c++17", 
                    "-I" + libDir.getAbsolutePath(), 
                    sourceFile.getAbsolutePath(), 
                    "-o", exeFile.getAbsolutePath()
            );
            
            compilePb.redirectErrorStream(true);
            Process compileProcess = compilePb.start();
            boolean compiled = compileProcess.waitFor(15000, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!compiled || compileProcess.exitValue() != 0 || !exeFile.exists()) {
                String error = new String(compileProcess.getInputStream().readAllBytes());
                if (compileProcess.isAlive()) compileProcess.destroyForcibly();
                return new ExecutionResult("CE", "", "Lỗi biên dịch Checker:\n" + error, 0, compileProcess.exitValue());
            }
            
            return new ExecutionResult("SUCCESS", exeFile.getAbsolutePath(), "", 0, 0);
            
        } catch (Exception e) {
            return new ExecutionResult("RTE", "", "Lỗi khi compile Checker: " + e.getMessage(), 0, -1);
        }
    }

    /**
     * Chạy custom checker (testlib.h) với file exe đã biên dịch
     */
    public ExecutionResult executeChecker(String exePath, String inputData, String expectedOutput, String actualOutput, long timeLimitMs) {
        try {
            Path tempDir = Files.createTempDirectory("checker_run");
            File tempFolder = tempDir.toFile();
            
            File inFile = new File(tempFolder, "input.txt");
            Files.writeString(inFile.toPath(), inputData != null ? inputData : "");
            
            File actualFile = new File(tempFolder, "actual.txt");
            Files.writeString(actualFile.toPath(), actualOutput != null ? actualOutput : "");
            
            File expectedFile = new File(tempFolder, "expected.txt");
            Files.writeString(expectedFile.toPath(), expectedOutput != null ? expectedOutput : "");
            
            ProcessBuilder runPb = new ProcessBuilder(
                    exePath,
                    inFile.getAbsolutePath(),
                    actualFile.getAbsolutePath(),
                    expectedFile.getAbsolutePath()
            );
            
            // Dùng BaseCodeExecutor để chạy tiến trình và lấy ExitCode
            class CheckerExecutor extends bll.executor.BaseCodeExecutor {
                @Override
                public ExecutionResult compile(Path dir, String code) { return null; }
                @Override
                public ExecutionResult runCode(Path dir, String in, long time) { return null; }
                @Override
                public ExecutionResult execute(Path dir, String code, String in, long time) { return null; }
                
                public ExecutionResult runCheckerProcess() throws Exception {
                    return this.runProcess(runPb, "", timeLimitMs, false);
                }
            }
            
            return new CheckerExecutor().runCheckerProcess();
            
        } catch (Exception e) {
            return new ExecutionResult("RTE", "", "Lỗi khi chạy Checker: " + e.getMessage(), 0, -1);
        }
    }
}
