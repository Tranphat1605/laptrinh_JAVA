package bll.executor;

import entity.ExecutionResult;

import java.io.*;
import java.util.concurrent.TimeUnit;

public abstract class BaseCodeExecutor implements CodeExecutor {

    protected ExecutionResult runProcess(ProcessBuilder pb, String input, long timeLimitMs) {
        long startTime = System.currentTimeMillis();
        try {
            Process process = pb.start();

            if (input != null && !input.isEmpty()) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(input);
                    writer.flush();
                }
            }

            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult("TLE", "", "Time Limit Exceeded", executionTime);
            }

            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());

            if (process.exitValue() != 0) {
                return new ExecutionResult("RTE", output, error, executionTime);
            }

            return new ExecutionResult("SUCCESS", output, error, executionTime);
        } catch (Exception e) {
            return new ExecutionResult("RTE", "", e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    protected String readStream(InputStream is) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }
}
