import bll.EvaluationService;
import entity.*;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== KHỞI CHẠY KIỂM THỬ TỰ ĐỘNG - MODULE CHẤM CODE ===");

        // 1. Tạo danh sách Testcases giả lập cho bài toán: Nhân đôi số nguyên đầu vào
        List<TestCase> testCases = new ArrayList<>();
        testCases.add(new TestCase(1, 101, "5\n", "10", false, "Normal"));
        testCases.add(new TestCase(2, 101, "12\n", "24", false, "Normal"));

        // 2. Tạo danh sách các Code mẫu thí sinh nộp thử
        List<SampleCode> sampleCodes = new ArrayList<>();

        // Code mẫu 1: Chạy hoàn toàn ĐÚNG (AC - Accepted)
        String correctJavaCode = 
            "import java.util.Scanner;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        if (sc.hasNextInt()) {\n" +
            "            int n = sc.nextInt();\n" +
            "            System.out.println(n * 2);\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        sampleCodes.add(new SampleCode(correctJavaCode, "java", "AC"));

        // Code mẫu 2: Chạy SAI logic kết quả (WA - Wrong Answer) - Cố tình nhân 3 thay vì nhân 2
        String wrongJavaCode = 
            "import java.util.Scanner;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        if (sc.hasNextInt()) {\n" +
            "            int n = sc.nextInt();\n" +
            "            System.out.println(n * 3);\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        sampleCodes.add(new SampleCode(wrongJavaCode, "java", "WA"));

        // Code mẫu 3: Chạy QUÁ thời gian giới hạn (TLE - Time Limit Exceeded)
        String tleJavaCode = 
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        while (true) {\n" +
            "            // Vòng lặp vô hạn\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        sampleCodes.add(new SampleCode(tleJavaCode, "java", "TLE"));

        // 3. Khởi chạy quá trình chấm bài và đánh giá chất lượng testcase
        System.out.println("\nĐang chạy chấm điểm các code mẫu trên Sandbox...");
        EvaluationService service = new EvaluationService();
        
        // Cấu hình thời gian chạy tối đa là 1500ms (1.5 giây)
        EvaluationReport report = service.evaluateTestCases(testCases, sampleCodes, null, 1500);

        // 4. Hiển thị báo cáo kết quả chấm chi tiết
        System.out.println("\n=== KẾT QUẢ CHẤM ĐIỂM CHI TIẾT ===");
        for (EvaluationResult res : report.getDetailedResults()) {
            System.out.printf("Code mẫu #%d | Testcase ID: %d | Trạng thái: %s | Thời gian chạy: %dms\n",
                    res.getSubmissionId(), res.getTestcaseId(), res.getStatus(), res.getExecutionTimeMs());
            if (!res.getStatus().equals("AC")) {
                System.out.printf("   -> Output thực tế: \"%s\"\n", res.getActualOutput().trim());
            }
        }

        System.out.println("\n" + report.generateSummary());
    }
}