package bll.executor;

import entity.ExecutionResult;

import java.io.*;
import java.util.concurrent.TimeUnit;

public abstract class BaseCodeExecutor implements CodeExecutor {

    protected ExecutionResult runProcess(ProcessBuilder pb, String input, long timeLimitMs) {
        return runProcess(pb, input, timeLimitMs, true);
    }

    protected ExecutionResult runProcess(ProcessBuilder pb, String input, long timeLimitMs, boolean measureCpu) {
        // Kiểm tra HDH để lai (Hybrid OS)
        boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");

        if (isLinux && measureCpu) {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("/usr/bin/time");
            command.add("-f");
            command.add("%U");
            command.addAll(pb.command());
            pb.command(command);
        }

        long startTimeNano = System.nanoTime();
        Process process = null;
        StreamConsumer stdoutConsumer = null;
        StreamConsumer stderrConsumer = null;
        try {
            process = pb.start();

            // Khởi động các luồng đọc bất đồng bộ ngay sau khi tiến trình bắt đầu
            stdoutConsumer = new StreamConsumer(process.getInputStream());
            stderrConsumer = new StreamConsumer(process.getErrorStream());
            stdoutConsumer.start();
            stderrConsumer.start();

            // Tác vụ A: Gửi dữ liệu đầu vào (input) vào tiến trình con nếu có
            if (input != null && !input.isEmpty()) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(input);
                    writer.flush();
                }
            } else {
                // Đóng luồng output để báo hiệu hết dữ liệu đầu vào
                process.getOutputStream().close();
            }

            // Tác vụ D (Watchdog): Đếm ngược timeLimitMs * 2 để xử lý infinite loop
            boolean finished = process.waitFor(timeLimitMs * 2, TimeUnit.MILLISECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                stdoutConsumer.interrupt();
                stderrConsumer.interrupt();
                return new ExecutionResult("TLE", "", "Time Limit Exceeded (Watchdog)", timeLimitMs * 2, -1);
            }

            // Chờ các luồng đọc hoàn tất việc nhận dữ liệu
            stdoutConsumer.join(1000);
            stderrConsumer.join(1000);

            String output = stdoutConsumer.getResult();
            String errorRaw = stderrConsumer.getResult();
            
            long executionTimeMs = 0;
            String error = errorRaw;

            // Tác vụ C: Parsing CPU time từ stderr nếu là Linux và cờ đo được bật
            if (isLinux && measureCpu) {
                String[] errorLines = errorRaw.trim().split("\n");
                if (errorLines.length > 0) {
                    String lastLine = errorLines[errorLines.length - 1].trim();
                    try {
                        double cpuTimeSec = Double.parseDouble(lastLine);
                        executionTimeMs = Math.round(cpuTimeSec * 1000); // %U -> mili-giây
                        
                        // Lọc bỏ dòng thời gian ra khỏi stderr thực tế
                        StringBuilder actualError = new StringBuilder();
                        for (int i = 0; i < errorLines.length - 1; i++) {
                            actualError.append(errorLines[i]).append("\n");
                        }
                        error = actualError.toString();
                    } catch (NumberFormatException e) {
                        // Fallback nếu không parse được
                        executionTimeMs = (System.nanoTime() - startTimeNano) / 1_000_000;
                    }
                }
            } else {
                // OS khác (Windows/Mac) hoặc không bật cờ đo -> Fallback đo theo Wall-clock chuẩn
                executionTimeMs = (System.nanoTime() - startTimeNano) / 1_000_000;
            }

            // Đánh giá TLE dựa trên phần trăm CPU thực thụ
            // Cho phép bù trừ độ trễ khởi động JVM tốn khoảng 50ms-100ms
            if (measureCpu && executionTimeMs > timeLimitMs) {
                return new ExecutionResult("TLE", output, error, executionTimeMs, process.exitValue());
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return new ExecutionResult("RTE", output, error, executionTimeMs, exitCode);
            }

            return new ExecutionResult("SUCCESS", output, error, executionTimeMs, exitCode);
        } catch (Exception e) {
            long executionTimeMs = (System.nanoTime() - startTimeNano) / 1_000_000;
            int exitCode = -1;
            if (process != null) {
                try {
                    exitCode = process.exitValue();
                } catch (IllegalThreadStateException ignored) {} // chưa thoát
                process.destroyForcibly();
            }
            return new ExecutionResult("RTE", "", e.getMessage(), executionTimeMs, exitCode);
        }
    }

    private static class StreamConsumer extends Thread {
        private final InputStream is;
        private final StringBuilder sb = new StringBuilder();
        private IOException exception = null;

        public StreamConsumer(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                this.exception = e;
            }
        }

        public String getResult() throws IOException {
            if (exception != null) {
                throw exception;
            }
            return sb.toString();
        }
    }
}
