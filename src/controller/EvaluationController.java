package controller;

import bll.EvaluationTask;
import entity.EvaluationReport;
import entity.SampleCode;
import entity.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller điều phối logic đánh giá chất lượng Testcase.
 * Chứa mock data mẫu và khởi chạy EvaluationTask.
 * View (TestcaseEvaluationFrame) chỉ cần gọi runEvaluation() và implement EvaluationListener.
 */
public class EvaluationController {

    public interface EvaluationListener {
        /** Gọi ngay khi bắt đầu — trên EDT */
        void onStart();
        /** Cập nhật tiến độ — gọi từ background thread */
        void onProgress(int current, int total, String status);
        /** Hoàn thành — gọi từ background thread */
        void onComplete(EvaluationReport report);
        /** Có lỗi — gọi từ background thread */
        void onError(Exception e);
    }

    /**
     * Chạy bộ kiểm thử với mock data có sẵn.
     * onStart() gọi đồng bộ trên luồng hiện tại (EDT).
     * Các callback còn lại gọi từ background thread — View tự bọc SwingUtilities nếu cần.
     */
    public void runEvaluation(EvaluationListener listener) {
        listener.onStart();

        List<TestCase> testCases = buildMockTestCases();
        List<SampleCode> sampleCodes = buildMockSampleCodes();

        EvaluationTask task = new EvaluationTask(
            testCases, sampleCodes, null, 1500L,
            new EvaluationTask.EvaluationListener() {
                @Override public void onProgress(int cur, int tot, String status) {
                    listener.onProgress(cur, tot, status);
                }
                @Override public void onComplete(EvaluationReport report) {
                    listener.onComplete(report);
                }
                @Override public void onError(Exception e) {
                    listener.onError(e);
                }
            });

        new Thread(task, "EvaluationThread").start();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private List<TestCase> buildMockTestCases() {
        List<TestCase> list = new ArrayList<>();
        list.add(new TestCase(1, 101, "5\n",  "10", false, "Normal"));
        list.add(new TestCase(2, 101, "12\n", "24", false, "Normal"));
        return list;
    }

    private List<SampleCode> buildMockSampleCodes() {
        List<SampleCode> list = new ArrayList<>();

        // AC: nhân 2 đúng
        list.add(new SampleCode(
            "import java.util.Scanner;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 2);\n" +
            "    }\n" +
            "}\n",
            "java", "AC"));

        // WA: nhân 3 sai logic
        list.add(new SampleCode(
            "import java.util.Scanner;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 3);\n" +
            "    }\n" +
            "}\n",
            "java", "WA"));

        // TLE: vòng lặp vô hạn
        list.add(new SampleCode(
            "public class Main {\n" +
            "    public static void main(String[] args) { while (true) {} }\n" +
            "}\n",
            "java", "TLE"));

        return list;
    }
}
