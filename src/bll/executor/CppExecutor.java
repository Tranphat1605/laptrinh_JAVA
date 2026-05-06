package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class CppExecutor extends BaseCodeExecutor {
    @Override
    public ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception {
        File sourceFile = new File(tempDir.toFile(), "main.cpp");
        File exeFile = new File(tempDir.toFile(), "main.exe"); // Môi trường Windows (.exe)
        Files.writeString(sourceFile.toPath(), code);

        // Biên dịch
        ProcessBuilder compilePb = new ProcessBuilder("g++", sourceFile.getAbsolutePath(), "-o", exeFile.getAbsolutePath());
        ExecutionResult compileResult = runProcess(compilePb, "", 10000);
        if (!exeFile.exists() || compileResult.getStatus().equals("RTE")) {
            return new ExecutionResult("CE", "", compileResult.getError(), 0);
        }

        // Chạy
        ProcessBuilder runPb = new ProcessBuilder(exeFile.getAbsolutePath());
        return runProcess(runPb, input, timeLimitMs);
    }
}
