package bll.executor;

import entity.ExecutionResult;

import java.io.*;
import java.util.concurrent.TimeUnit;

public abstract class BaseCodeExecutor implements CodeExecutor {

    protected ExecutionResult runProcess(ProcessBuilder pb, String input, long timeLimitMs) {
        // Sử dụng System.nanoTime() thay vì System.currentTimeMillis() để đo đếm thời gian chuẩn xác nhất
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

            // Gửi dữ liệu đầu vào (input) vào tiến trình con nếu có
            if (input != null && !input.isEmpty()) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(input);
                    writer.flush();
                }
            } else {
                // Đóng luồng output để báo hiệu hết dữ liệu đầu vào cho một số chương trình đợi EOF
                process.getOutputStream().close();
            }

            // Chờ tiến trình kết thúc có giới hạn thời gian
            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            long executionTime = (System.nanoTime() - startTimeNano) / 1_000_000; // Đổi từ nano ra mili giây

            if (!finished) {
                process.destroyForcibly();
                stdoutConsumer.interrupt();
                stderrConsumer.interrupt();
                return new ExecutionResult("TLE", "", "Time Limit Exceeded", executionTime);
            }

            // Chờ các luồng đọc hoàn tất việc nhận dữ liệu
            stdoutConsumer.join(1000);
            stderrConsumer.join(1000);

            String output = stdoutConsumer.getResult();
            String error = stderrConsumer.getResult();

            if (process.exitValue() != 0) {
                return new ExecutionResult("RTE", output, error, executionTime);
            }

            return new ExecutionResult("SUCCESS", output, error, executionTime);
        } catch (Exception e) {
            long executionTime = (System.nanoTime() - startTimeNano) / 1_000_000;
            if (process != null) {
                process.destroyForcibly();
            }
            return new ExecutionResult("RTE", "", e.getMessage(), executionTime);
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
