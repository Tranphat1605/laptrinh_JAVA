package bll.executor;

import java.util.HashMap;
import java.util.Map;

public class CodeExecutorFactory {
    private static final Map<String, CodeExecutor> EXECUTORS = new HashMap<>();

    static {
        // Tự động load trước các module hỗ trợ
        EXECUTORS.put("java", new JavaExecutor());
        EXECUTORS.put("python", new PythonExecutor());
        EXECUTORS.put("cpp", new CppExecutor());
    }

    public static CodeExecutor getExecutor(String language) {
        if (language == null) return null;
        return EXECUTORS.get(language.toLowerCase());
    }
}
