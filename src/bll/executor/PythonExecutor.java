package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PythonExecutor extends BaseCodeExecutor {
    @Override
    public ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception {
        File sourceFile = new File(tempDir.toFile(), "Main.py");
        Files.writeString(sourceFile.toPath(), code);

        // Chạy trực tiếp python không cần biên dịch
        ProcessBuilder pb = new ProcessBuilder("python", sourceFile.getAbsolutePath());
        return runProcess(pb, input, timeLimitMs);
    }
}
