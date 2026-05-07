package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PythonExecutor extends BaseCodeExecutor {
    @Override
    public ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception {
        File sourceFile = new File(tempDir.toFile(), "Main.py");
        try {
            Files.writeString(sourceFile.toPath(), code);

            // Tự động tìm lệnh Python phù hợp
            String pythonCmd = findPythonCommand();

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, sourceFile.getAbsolutePath());
            return runProcess(pb, input, timeLimitMs);
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

    private String findPythonCommand() {
        if (isCommandAvailable("python")) {
            return "python";
        }
        if (isCommandAvailable("python3")) {
            return "python3";
        }
        if (isCommandAvailable("py")) {
            return "py";
        }
        return "python"; // mặc định
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version").start();
            p.waitFor(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
