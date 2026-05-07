//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        System.out.println("=== BẮT ĐẦU TEST CHỨC NĂNG CHẠY CODE JAVA ===");
        
        try {
            // 1. Tạo một thư mục tạm thời để lưu file code được sinh ra
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("java_test_execute");
            System.out.println("Đã tạo thư mục tạm: " + tempDir.toAbsolutePath());

            // 2. Code người dùng nộp (CỐ TÌNH GÂY TLE CẦN TEST - Vòng lặp vô hạn)
            String userCode = 
                "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        int a = sc.nextInt();\n" +
                "        int b = sc.nextInt();\n" +
                "        // Vòng lặp vô hạn để test TLE\n" +
                "        while(true) {\n" +
                "            a++;\n" +
                "        }\n" +
                "    }\n" +
                "}";

            // 3. Dữ liệu đầu vào test case (Input giả lập hệ thống truyền vào)
            String input = "15 25\n"; 
            
            // 4. Giới hạn thời gian là 1000ms (1 giây)
            long timeLimitMs = 1000;

            // 5. Gọi Executor để chạy code
            bll.executor.JavaExecutor javaExecutor = new bll.executor.JavaExecutor();
            System.out.println("\nĐang biên dịch và thực thi...");
            entity.ExecutionResult result = javaExecutor.execute(tempDir, userCode, input, timeLimitMs);

            // 6. In kết quả trả về từ Executor
            System.out.println("\n=== KẾT QUẢ TRẢ VỀ ===");
            System.out.println("Trạng thái (Status) : " + result.getStatus());
            System.out.println("Đầu ra (Output)     : " + result.getOutput());
            System.out.println("Lỗi (Error)         : " + (result.getError() == null || result.getError().isEmpty() ? "Không có lỗi" : result.getError()));
            System.out.println("Thời gian chạy      : " + result.getExecutionTime() + " ms");

            if (result.getOutput() != null && result.getOutput().trim().equals("40")) {
                System.out.println("\n✅ TEST THÀNH CÔNG: Logic chạy code hoạt động chính xác!");
            } else {
                System.out.println("\n❌ TEST THẤT BẠI: Kết quả không mong đợi.");
            }

        } catch (Exception e) {
            System.err.println("\n❌ CÓ LỖI XẢY RA TRONG QUÁ TRÌNH TEST:");
            e.printStackTrace();
        }
    }
}