package bll;

import entity.*;
import java.util.List;

public class EvaluationTask implements Runnable {

    public interface EvaluationListener {
        /**
         * Được gọi khi tiến trình có sự thay đổi về tiến độ
         */
        void onProgress(int current, int total, String status);

        /**
         * Được gọi khi hoàn thành toàn bộ quá trình chấm và phân tích
         */
        void onComplete(EvaluationReport report);

        /**
         * Được gọi nếu xảy ra lỗi trong quá trình thực thi hệ thống chấm
         */
        void onError(Exception e);
    }

    private final EvaluationService evaluationService;
    private final List<TestCase> testCases;
    private final List<SampleCode> sampleCodes;
    private final Checker checker;
    private final long timeLimitMs;
    private final EvaluationListener listener;

    public EvaluationTask(List<TestCase> testCases, List<SampleCode> sampleCodes, Checker checker, long timeLimitMs, EvaluationListener listener) {
        this.evaluationService = new EvaluationService();
        this.testCases = testCases;
        this.sampleCodes = sampleCodes;
        this.checker = checker;
        this.timeLimitMs = timeLimitMs;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            if (listener != null) {
                listener.onProgress(0, 100, "Khởi chạy môi trường Sandbox...");
            }

            if (testCases == null || testCases.isEmpty()) {
                throw new IllegalArgumentException("Danh sách testcase không được để trống.");
            }
            if (sampleCodes == null || sampleCodes.isEmpty()) {
                throw new IllegalArgumentException("Danh sách code mẫu không được để trống.");
            }

            if (listener != null) {
                listener.onProgress(20, 100, "Bắt đầu chạy code mẫu qua bộ testcase...");
            }

            // Gọi dịch vụ chấm điểm chính
            EvaluationReport report = evaluationService.evaluateTestCases(testCases, sampleCodes, checker, timeLimitMs);

            if (listener != null) {
                listener.onProgress(100, 100, "Hoàn thành chấm điểm và trích xuất báo cáo!");
                listener.onComplete(report);
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onError(e);
            }
        }
    }
}
