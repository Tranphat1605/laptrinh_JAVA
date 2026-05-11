package bll.executor;

import entity.ExecutionResult;
import java.nio.file.Path;

public interface CodeExecutor {
    ExecutionResult compile(Path tempDir, String code) throws Exception;
    ExecutionResult runCode(Path tempDir, String input, long timeLimitMs) throws Exception;
    ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception;
}
