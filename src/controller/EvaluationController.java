package controller;

import bll.EvaluationTask;
import entity.Checker;
import entity.EvaluationReport;
import entity.SampleCode;
import entity.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller điều phối logic đánh giá chất lượng Testcase.
 * Nhận dữ liệu thực từ TeacherFrame (checker, ac, wa code) hoặc
 * dùng mock data mặc định khi các trường được để trống.
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
     * Chạy bộ kiểm thử với dữ liệu thực từ Tab 2 và Tab 3 của TeacherFrame.
     * Nếu checkerCode / acCode / waCode để trống, tự động dùng mock mặc định.
     *
     * @param checkerCode  Code Checker (Java) từ Tab 2. Truyền null/empty để dùng so khớp chính xác mặc định.
     * @param acCode       Code mẫu AC (Java) từ Tab 3. Truyền null/empty để dùng mock.
     * @param waCode       Code mẫu WA (Java) từ Tab 3. Truyền null/empty để dùng mock.
     * @param listener     Callback nhận kết quả về giao diện.
     */
    public void runEvaluation(String checkerCode, String acCode, String waCode,
                              EvaluationListener listener) {
        listener.onStart();

        // Testcase: hiện dùng mock; có thể mở rộng nhận từ DB sau này
        List<TestCase> testCases = buildMockTestCases();

        // SampleCode: dùng dữ liệu thực nếu có, ngược lại fallback mock
        List<SampleCode> sampleCodes = buildSampleCodes(acCode, waCode);

        // Checker: dùng checker code thực nếu có, ngược lại null (so khớp chính xác)
        Checker checker = buildChecker(checkerCode);

        EvaluationTask task = new EvaluationTask(
            testCases, sampleCodes, checker, 1500L,
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

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Trả về Checker thực nếu checkerCode hợp lệ, ngược lại null. */
    private Checker buildChecker(String checkerCode) {
        if (isBlank(checkerCode)) return null;
        return new Checker(checkerCode, "java");
    }

    /** Xây dựng danh sách SampleCode từ code thực hoặc mock. */
    private List<SampleCode> buildSampleCodes(String acCode, String waCode) {
        List<SampleCode> list = new ArrayList<>();

        if (!isBlank(acCode)) {
            list.add(new SampleCode(acCode, "java", "AC"));
        } else {
            // Mock AC: nhân 2 đúng
            list.add(new SampleCode(
                "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 2);\n" +
                "    }\n" +
                "}\n",
                "java", "AC"));
        }

        if (!isBlank(waCode)) {
            list.add(new SampleCode(waCode, "java", "WA"));
        } else {
            // Mock WA: nhân 3 sai logic
            list.add(new SampleCode(
                "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        if (sc.hasNextInt()) System.out.println(sc.nextInt() * 3);\n" +
                "    }\n" +
                "}\n",
                "java", "WA"));

            // Mock TLE (chỉ thêm khi dùng toàn mock, không thêm khi có code thực)
            list.add(new SampleCode(
                "public class Main {\n" +
                "    public static void main(String[] args) { while (true) {} }\n" +
                "}\n",
                "java", "TLE"));
        }

        return list;
    }

    /** Mock testcase chuẩn — sẽ mở rộng kết nối DB. */
    private List<TestCase> buildMockTestCases() {
        List<TestCase> list = new ArrayList<>();
        list.add(new TestCase(1, 101, "5\n",  "10", false, "Normal"));
        list.add(new TestCase(2, 101, "12\n", "24", false, "Normal"));
        return list;
    }
}
