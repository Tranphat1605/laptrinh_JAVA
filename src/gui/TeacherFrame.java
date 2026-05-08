package gui;

import controller.EvaluationController;
import controller.TeacherController;
import entity.EvaluationReport;
import entity.EvaluationResult;
import entity.Problem;

import java.awt.*;
import java.io.File;
import javax.swing.*;

/**
 * View: chỉ chứa code xây dựng giao diện Swing.
 * Toàn bộ logic AI đã được chuyển sang TeacherController.
 */
public class TeacherFrame extends JFrame {

    // ── UI Components ──
    private JTextArea problemInputArea;
    private JTextArea aiAnalysisLogArea;
    private JButton   btnAnalyzeAI;
    private JButton   btnSelectImage;
    private JTextArea generatorCodeArea;
    private JTextArea checkerCodeArea;
    private JTextArea sampleACArea;
    private JTextArea sampleWAArea;

    private File selectedImageFile = null;

    // ── Controllers ──
    private final TeacherController   controller;
    private final EvaluationController evalController;

    // ── Tab 4 UI ──
    private JTextArea    evalLogArea;
    private JProgressBar evalProgressBar;
    private JButton      btnRunEval;

    public TeacherFrame() {
        setTitle("Giao Diện Giáo Viên (Sinh Đề & Testcase bằng AI)");
        setSize(1100, 800);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // API key chỉ cần đặt ở 1 chỗ này
        controller     = new TeacherController("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");
        evalController = new EvaluationController();

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("1. Nhập liệu & Phân tích",       createTab1());
        tabbedPane.add("2. AI Sinh Testcase & Checker",   createTab2());
        tabbedPane.add("3. AI Sinh Mẫu (AC, WA, TLE)",   createTab3());
        tabbedPane.add("4. Đánh giá chất lượng Testcase", createTab4());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ── Tab 1: Nhập đề & Phân tích ──────────────────────────────────────────

    private JPanel createTab1() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        problemInputArea = new JTextArea("Paste đề thi IOI / ICPC bằng chữ vào đây...");
        problemInputArea.setLineWrap(true);
        problemInputArea.setWrapStyleWord(true);
        JScrollPane scroll1 = new JScrollPane(problemInputArea);
        scroll1.setBorder(BorderFactory.createTitledBorder("Nhập Đề thi (Text)"));

        JPanel bottomInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnSelectImage = new JButton("Chọn Ảnh Đề Thi");
        JLabel lblImageStatus = new JLabel("Chưa chọn ảnh");
        btnSelectImage.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedImageFile = chooser.getSelectedFile();
                lblImageStatus.setText(selectedImageFile.getName());
            }
        });

        btnAnalyzeAI = new JButton("Phân tích đề bằng AI");
        btnAnalyzeAI.setBackground(new Color(60, 150, 255));
        btnAnalyzeAI.setForeground(Color.WHITE);
        btnAnalyzeAI.addActionListener(e ->
            controller.analyzeProblem(problemInputArea.getText(), selectedImageFile,
                new TeacherController.AnalysisListener() {
                    @Override public void onStart() {
                        aiAnalysisLogArea.setText("Đang phân tích cấu trúc đề bài...\n\n");
                        btnAnalyzeAI.setEnabled(false);
                    }
                    @Override public void onComplete(Problem p) {
                        aiAnalysisLogArea.append("Kết quả từ AI:\n" + p.getContent());
                        aiAnalysisLogArea.append("\n\n... Hoàn thành phân tích.");
                        btnAnalyzeAI.setEnabled(true);
                    }
                    @Override public void onError(String msg) {
                        aiAnalysisLogArea.append("\nLỗi kết nối tới AI: " + msg);
                        btnAnalyzeAI.setEnabled(true);
                    }
                })
        );

        bottomInputPanel.add(btnSelectImage);
        bottomInputPanel.add(lblImageStatus);
        bottomInputPanel.add(new JLabel("   |   "));
        bottomInputPanel.add(btnAnalyzeAI);

        aiAnalysisLogArea = new JTextArea();
        aiAnalysisLogArea.setEditable(false);
        JScrollPane scroll2 = new JScrollPane(aiAnalysisLogArea);
        scroll2.setBorder(BorderFactory.createTitledBorder("Kết quả AI phân tích cấu trúc bài"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll1, scroll2);
        splitPane.setResizeWeight(0.5);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(bottomInputPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ── Tab 2: Sinh Testcase & Checker ──────────────────────────────────────

    private JPanel createTab2() {
        JPanel panel = new JPanel(new BorderLayout());

        generatorCodeArea = new JTextArea();
        checkerCodeArea   = new JTextArea();
        generatorCodeArea.setBorder(BorderFactory.createTitledBorder("Generator Code (Sinh Testcase - C++)"));
        checkerCodeArea.setBorder(BorderFactory.createTitledBorder("Checker Code (So khớp đáp án - C++)"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(generatorCodeArea),
            new JScrollPane(checkerCodeArea));
        splitPane.setResizeWeight(0.5);

        JButton btnGen = new JButton("Yêu cầu AI sinh Testcase & Checker");
        btnGen.addActionListener(e ->
            controller.generateTestcaseAndChecker(problemInputArea.getText(),
                new TeacherController.GenerationListener() {
                    @Override public void onStart() {
                        generatorCodeArea.setText("// Đang gọi AI API sinh Generator...\n");
                        checkerCodeArea.setText("// Đang gọi AI API sinh Checker...\n");
                    }
                    @Override public void onComplete(String gen, String check) {
                        generatorCodeArea.setText(gen);
                        checkerCodeArea.setText(check);
                    }
                    @Override public void onError(String msg) {
                        generatorCodeArea.setText("Lỗi kết nối AI API: " + msg);
                    }
                })
        );

        JPanel top = new JPanel();
        top.add(btnGen);
        panel.add(top, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // ── Tab 3: Sinh Code Mẫu AC / WA ────────────────────────────────────────

    private JPanel createTab3() {
        sampleACArea = new JTextArea();
        sampleACArea.setBorder(BorderFactory.createTitledBorder("Mã Mẫu AC (Đúng chuẩn)"));

        sampleWAArea = new JTextArea();
        sampleWAArea.setBorder(BorderFactory.createTitledBorder("Mã Mẫu WA (Code sai cố ý để bẫy Testcase)"));

        JPanel codePanel = new JPanel(new GridLayout(1, 2, 10, 10));
        codePanel.add(new JScrollPane(sampleACArea));
        codePanel.add(new JScrollPane(sampleWAArea));

        JButton btnGen = new JButton("Yêu cầu AI tự động sinh Code mẫu Tốt / Xấu");
        btnGen.addActionListener(e ->
            controller.generateSampleCodes(problemInputArea.getText(),
                new TeacherController.SampleCodeListener() {
                    @Override public void onStart() {
                        sampleACArea.setText("Đang gọi AI suy nghĩ code tối ưu nhất (AC)...");
                        sampleWAArea.setText("Đang tìm lỗi sai logic để lừa bằng mã WA...");
                    }
                    @Override public void onComplete(String ac, String wa) {
                        sampleACArea.setText(ac);
                        sampleWAArea.setText(wa);
                    }
                    @Override public void onError(String msg) {
                        sampleACArea.setText("Lỗi: " + msg);
                    }
                })
        );

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(btnGen, BorderLayout.NORTH);
        pnl.add(codePanel, BorderLayout.CENTER);
        return pnl;
    }

    // ── Tab 4: Đánh giá chất lượng Testcase ────────────────────────────────

    private JPanel createTab4() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: nút + progress bar
        btnRunEval = new JButton("Khởi chạy kiểm thử");
        btnRunEval.setFont(new Font("Arial", Font.BOLD, 13));
        btnRunEval.addActionListener(e -> runEvaluation());

        evalProgressBar = new JProgressBar(0, 100);
        evalProgressBar.setStringPainted(true);
        evalProgressBar.setPreferredSize(new Dimension(350, 26));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        topPanel.add(btnRunEval);
        topPanel.add(evalProgressBar);

        // Log area
        evalLogArea = new JTextArea();
        evalLogArea.setEditable(false);
        evalLogArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        evalLogArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(evalLogArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Báo cáo đánh giá"));

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scroll,   BorderLayout.CENTER);
        return panel;
    }

    private void runEvaluation() {
        evalController.runEvaluation(new EvaluationController.EvaluationListener() {
            @Override public void onStart() {
                btnRunEval.setEnabled(false);
                evalProgressBar.setValue(0);
                evalProgressBar.setString("Đang khởi động...");
                evalLogArea.setText("=== KHỞI CHẠY KIỂM THỬ ===\n\n");
                evalLog("Đang nạp dữ liệu mock...\n");
            }
            @Override public void onProgress(int cur, int tot, String status) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setValue(cur);
                    evalProgressBar.setString(status);
                });
            }
            @Override public void onComplete(EvaluationReport report) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setValue(100);
                    evalProgressBar.setString("Hoàn thành");
                    btnRunEval.setEnabled(true);
                    evalLog("\n=== KẾT QUẢ CHI TIẾT ===\n");
                    for (EvaluationResult res : report.getDetailedResults()) {
                        evalLog(String.format("Code #%d | Testcase #%d | %s | %dms%n",
                            res.getSubmissionId(), res.getTestcaseId(),
                            res.getStatus(), res.getExecutionTimeMs()));
                        if (!"AC".equals(res.getStatus()))
                            evalLog("  → Output: \"" + res.getActualOutput().trim() + "\"\n");
                    }
                    evalLog("\n" + report.generateSummary() + "\n");
                });
            }
            @Override public void onError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setString("Lỗi");
                    btnRunEval.setEnabled(true);
                    evalLog("\n❌ LỖI: " + e.getMessage() + "\n");
                });
            }
        });
    }

    private void evalLog(String text) {
        evalLogArea.append(text);
        evalLogArea.setCaretPosition(evalLogArea.getDocument().getLength());
    }
}