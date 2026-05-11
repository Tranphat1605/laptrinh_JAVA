package bll.executor;

import entity.ExecutionResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PythonExecutor extends BaseCodeExecutor {

    @Override
    public ExecutionResult compile(Path tempDir, String code) throws Exception {
        File sourceFile = new File(tempDir.toFile(), "Main.py");
        Files.writeString(sourceFile.toPath(), code);

        String pythonCmd = findPythonCommand();

        // Kiểm tra lỗi cú pháp
        ProcessBuilder compilePb = new ProcessBuilder(pythonCmd, "-m", "py_compile", sourceFile.getAbsolutePath());
        ExecutionResult compileResult = runProcess(compilePb, "", 5000, false);
        
        if (!compileResult.getStatus().equals("SUCCESS")) {
            return new ExecutionResult("CE", "", compileResult.getError(), 0, compileResult.getExitCode());
        }
        return new ExecutionResult("SUCCESS", "", "", 0, 0);
    }

    @Override
    public ExecutionResult runCode(Path tempDir, String input, long timeLimitMs) throws Exception {
        String pythonCmd = findPythonCommand();
        File sourceFile = new File(tempDir.toFile(), "Main.py");

        ProcessBuilder runPb = new ProcessBuilder(pythonCmd, sourceFile.getAbsolutePath());
        // Trừ bì Python chậm hơn, nhân hệ số x3 limit
        return runProcess(runPb, input, timeLimitMs * 3);
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

    private String findPythonCommand() {
        boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
        if (isLinux) {
            return "python3"; // Trên Linux bắt buộc ưu tiên gọi python3
        }

        if (isCommandAvailable("python3")) {
            return "python3";
        }
        if (isCommandAvailable("python")) {
            return "python";
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
