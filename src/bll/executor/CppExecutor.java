package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class CppExecutor extends BaseCodeExecutor {
    @Override
    public ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        File sourceFile = new File(tempDir.toFile(), "main.cpp");
        File exeFile = new File(tempDir.toFile(), isWindows ? "main.exe" : "main");
        try {
            Files.writeString(sourceFile.toPath(), code);

            // Biên dịch với cờ tối ưu hóa -O2 và chuẩn C++17
            ProcessBuilder compilePb = new ProcessBuilder("g++", "-O2", "-std=c++17", sourceFile.getAbsolutePath(), "-o", exeFile.getAbsolutePath());
            ExecutionResult compileResult = runProcess(compilePb, "", 15000); // Tăng thời gian biên dịch C++
            if (!exeFile.exists() || compileResult.getStatus().equals("RTE") || compileResult.getStatus().equals("CE")) {
                return new ExecutionResult("CE", "", compileResult.getError(), 0);
            }

            // Chạy
            ProcessBuilder runPb = new ProcessBuilder(exeFile.getAbsolutePath());
            return runProcess(runPb, input, timeLimitMs);
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
