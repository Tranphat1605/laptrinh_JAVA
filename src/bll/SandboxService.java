package bll;

import bll.executor.CodeExecutor;
import bll.executor.CodeExecutorFactory;
import entity.ExecutionResult;

import java.nio.file.Files;
import java.nio.file.Path;

public class SandboxService {

    /**
     * Chạy code với luồng thực thi đầy đủ
     */
    public ExecutionResult executeCode(String code, String language, String input, long timeLimitMs) {
        try {
            CodeExecutor executor = CodeExecutorFactory.getExecutor(language);
            
            if (executor == null) {
                return new ExecutionResult("CE", "", "Unsupported language: " + language, 0);
            }
            
            Path tempDir = Files.createTempDirectory("sandbox");
            return executor.execute(tempDir, code, input, timeLimitMs);
            
        } catch (Exception e) {
            return new ExecutionResult("RTE", "", e.getMessage(), 0);
        }
    }
}
