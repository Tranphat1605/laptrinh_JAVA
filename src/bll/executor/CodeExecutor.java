package bll.executor;

import entity.ExecutionResult;
import java.nio.file.Path;

public interface CodeExecutor {
    ExecutionResult execute(Path tempDir, String code, String input, long timeLimitMs) throws Exception;
}
