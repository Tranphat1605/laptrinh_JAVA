package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaExecutor extends BaseCodeExecutor {

    @Override
    public ExecutionResult compile(Path tempDir, String code) throws Exception {
        File sourceFile = new File(tempDir.toFile(), "Main.java");
        Files.writeString(sourceFile.toPath(), code);

        // Biên dịch
        ProcessBuilder compilePb = new ProcessBuilder("javac", sourceFile.getAbsolutePath());
        ExecutionResult compileResult = runProcess(compilePb, "", 10000, false); // Không cần đo CPU lúc biên dịch
        if (!compileResult.getStatus().equals("SUCCESS")) {
            return new ExecutionResult("CE", "", compileResult.getError(), 0, compileResult.getExitCode());
        }
        return new ExecutionResult("SUCCESS", "", "", 0, 0);
    }

    @Override
    public ExecutionResult runCode(Path tempDir, String input, long timeLimitMs) throws Exception {
        ProcessBuilder runPb = new ProcessBuilder("java", "-cp", tempDir.toAbsolutePath().toString(), "Main");
        // Java chậm, cấp thêm x2 limit thực tế tại execute
        return runProcess(runPb, input, timeLimitMs * 2);
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
