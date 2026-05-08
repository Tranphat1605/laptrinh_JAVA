package gui;

import bll.EvaluationTask;
import entity.EvaluationReport;
import entity.EvaluationResult;
import entity.SampleCode;
import entity.TestCase;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TestcaseEvaluationFrame extends JFrame {

    private JTextArea logArea;
    private JButton btnRunEvaluation;
    private JProgressBar progressBar;

    public TestcaseEvaluationFrame() {
        setTitle("Hệ Thống Đánh Giá Chất Lượng Test case");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // 1. Panel Phía trên (Nút chạy và Thanh tiến độ)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnRunEvaluation = new JButton("Khởi chạy kiểm thử (Chạy Test Mock)");
        btnRunEvaluation.setFont(new Font("Arial", Font.BOLD, 14));
        btnRunEvaluation.addActionListener(e -> runEvaluationTask());
        topPanel.add(btnRunEvaluation);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(350, 28));
        topPanel.add(progressBar);

        add(topPanel, BorderLayout.NORTH);

        // 2. Khu vực Log đánh giá kết quả
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Báo cáo đánh giá (Evaluation Report)"));
        add(scrollPane, BorderLayout.CENTER);
    }

    private void runEvaluationTask() {
        btnRunEvaluation.setEnabled(false);
        logArea.setText("=== KHỞI CHẠY KIỂM THỬ TỰ ĐỘNG - MODULE CHẤM CODE ===\n\n");
        appendLog("Đang nạp dữ liệu Mock (Testcases và Sample Codes)...\n");

        // Chuẩn bị Mock Test Cases
        List<TestCase> testCases = new ArrayList<>();
        testCases.add(new TestCase(1, 101, "5\n", "10", false, "Normal"));
        testCases.add(new TestCase(2, 101, "12\n", "24", false, "Normal"));

        // Chuẩn bị Mock Sample Codes
        List<SampleCode> sampleCodes = new ArrayList<>();

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

        String tleJavaCode = 
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        while (true) {}\n" +
            "    }\n" +
            "}\n";
        sampleCodes.add(new SampleCode(tleJavaCode, "java", "TLE"));

        // Chạy EvaluationTask trong Background Thread
        long timeLimitMs = 1500;
        EvaluationTask task = new EvaluationTask(testCases, sampleCodes, null, timeLimitMs, new EvaluationTask.EvaluationListener() {
            @Override
            public void onProgress(int current, int total, String status) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(current);
                    progressBar.setString(status);
                });
            }

            @Override
            public void onComplete(EvaluationReport report) {
                SwingUtilities.invokeLater(() -> {
                    btnRunEvaluation.setEnabled(true);
                    progressBar.setValue(100);
                    progressBar.setString("Đã hoàn thành");
                    
                    appendLog("\n=== KẾT QUẢ CHẤM ĐIỂM CHI TIẾT ===\n");
                    for (EvaluationResult res : report.getDetailedResults()) {
                        appendLog(String.format("Code mẫu #%d | Testcase ID: %d | Trạng thái: %s | Thời gian chạy: %dms\n",
                                res.getSubmissionId(), res.getTestcaseId(), res.getStatus(), res.getExecutionTimeMs()));
                        if (!res.getStatus().equals("AC")) {
                            appendLog(String.format("   -> Output thực tế: \"%s\"\n", res.getActualOutput().trim()));
                        }
                    }
                    appendLog("\n" + report.generateSummary() + "\n");
                });
            }

            @Override
            public void onError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    btnRunEvaluation.setEnabled(true);
                    progressBar.setString("Lỗi");
                    appendLog("\nLỖI HỆ THỐNG: " + e.getMessage() + "\n");
                });
            }
        });

        // Bắt đầu luồng kiểm thử
        new Thread(task).start();
    }

    private void appendLog(String message) {
        logArea.append(message);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TestcaseEvaluationFrame().setVisible(true);
        });
    }
}