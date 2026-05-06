package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaExecutor extends BaseCodeExecutor {
    @Override
    public ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception {
        File sourceFile = new File(tempDir.toFile(), "Main.java");
        Files.writeString(sourceFile.toPath(), code);

        // Biên dịch
        ProcessBuilder compilePb = new ProcessBuilder("javac", sourceFile.getAbsolutePath());
        ExecutionResult compileResult = runProcess(compilePb, "", 10000);
        if (compileResult.getError() != null && !compileResult.getError().isEmpty() && compileResult.getStatus().equals("RTE")) {
            return new ExecutionResult("CE", "", compileResult.getError(), 0);
        }

        // Chạy
        ProcessBuilder runPb = new ProcessBuilder("java", "-cp", tempDir.toAbsolutePath().toString(), "Main");
        return runProcess(runPb, input, timeLimitMs);
    }
}
