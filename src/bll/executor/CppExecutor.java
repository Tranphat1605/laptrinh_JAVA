package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class CppExecutor extends BaseCodeExecutor {

    @Override
    public ExecutionResult compile(Path tempDir, String code) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        File sourceFile = new File(tempDir.toFile(), "main.cpp");
        File exeFile = new File(tempDir.toFile(), isWindows ? "main.exe" : "main");
        
        Files.writeString(sourceFile.toPath(), code);

        // Biên dịch với cờ tối ưu hóa -O2 và chuẩn C++17
        ProcessBuilder compilePb = new ProcessBuilder("g++", "-O2", "-std=c++17", sourceFile.getAbsolutePath(), "-o", exeFile.getAbsolutePath());
        ExecutionResult compileResult = runProcess(compilePb, "", 45000, false); // Tăng thời gian biên dịch C++ lên 45s để tránh Timeout (nhất là khi dùng testlib.h)
        
        if (!exeFile.exists() || !compileResult.getStatus().equals("SUCCESS")) {
            return new ExecutionResult("CE", "", compileResult.getError(), 0, compileResult.getExitCode());
        }
        return new ExecutionResult("SUCCESS", "", "", 0, 0);
    }

    @Override
    public ExecutionResult runCode(Path tempDir, String input, long timeLimitMs) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        File exeFile = new File(tempDir.toFile(), isWindows ? "main.exe" : "main");
        
        ProcessBuilder runPb = new ProcessBuilder(exeFile.getAbsolutePath());
        return runProcess(runPb, input, timeLimitMs);
    }

    @Override
    public ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception {
        try {
            ExecutionResult compileResult = compile(tempDir, code);
            if (!compileResult.getStatus().equals("SUCCESS")) {
                return compileResult;
            }
            return runCode(tempDir, input, timeLimitMs);
        } finally {
            // Dọn dẹp tệp tạm thời và thư mục tạm
            try {
                File[] files = tempDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {}
        }
    }
}
