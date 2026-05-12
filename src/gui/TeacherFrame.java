package gui;

import controller.EvaluationController;
import controller.TeacherController;
import entity.EvaluationReport;
import entity.EvaluationResult;
import entity.Problem;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.SwingUtilities;

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
    private JLabel       lblEvalMode;

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

    private static final String PLACEHOLDER_TEXT = "Paste đề thi IOI / ICPC bằng chữ vào đây...";

    /** Kiểm tra đề bài đã nhập chưa, trả về true nếu hợp lệ. */
    private boolean validateProblemInput() {
        String text = problemInputArea.getText().trim();
        if (text.isEmpty() || text.equals(PLACEHOLDER_TEXT)) {
            JOptionPane.showMessageDialog(this,
                "⚠ Vui lòng nhập nội dung đề bài vào ô bên trái trước khi gọi AI!",
                "Chưa có đề bài", JOptionPane.WARNING_MESSAGE);
            problemInputArea.requestFocus();
            return false;
        }
        if (text.length() < 20) {
            JOptionPane.showMessageDialog(this,
                "⚠ Nội dung đề bài quá ngắn. Hãy nhập đầy đủ đề bài.",
                "Đề bài không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private JPanel createTab1() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        problemInputArea = new JTextArea(PLACEHOLDER_TEXT);
        problemInputArea.setForeground(Color.GRAY);
        problemInputArea.setLineWrap(true);
        problemInputArea.setWrapStyleWord(true);
        // Xóa placeholder khi người dùng bắt đầu nhập
        problemInputArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                if (problemInputArea.getText().equals(PLACEHOLDER_TEXT)) {
                    problemInputArea.setText("");
                    problemInputArea.setForeground(Color.BLACK);
                }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (problemInputArea.getText().trim().isEmpty()) {
                    problemInputArea.setText(PLACEHOLDER_TEXT);
                    problemInputArea.setForeground(Color.GRAY);
                }
            }
        });
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
        btnAnalyzeAI.addActionListener(e -> {
            if (!validateProblemInput()) return;  // Chặn nếu chưa nhập đề
            controller.analyzeProblem(problemInputArea.getText(), selectedImageFile,
                new TeacherController.AnalysisListener() {
                    @Override public void onStart() {
                        aiAnalysisLogArea.setText("⏳ Đang phân tích cấu trúc đề bài...\n\n");
                        btnAnalyzeAI.setEnabled(false);
                    }
                    @Override public void onComplete(Problem p) {
                        aiAnalysisLogArea.append("✅ Kết quả từ AI:\n" + p.getContent());
                        aiAnalysisLogArea.append("\n\n─── Hoàn thành phân tích. ───");
                        btnAnalyzeAI.setEnabled(true);
                    }
                    @Override public void onError(String msg) {
                        aiAnalysisLogArea.append("\n❌ Lỗi: " + msg);
                        btnAnalyzeAI.setEnabled(true);
                    }
                });
        });

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
        btnGen.addActionListener(e -> {
            if (!validateProblemInput()) return;
            controller.generateTestcaseAndChecker(problemInputArea.getText(),
                new TeacherController.GenerationListener() {
                    @Override public void onStart() {
                        generatorCodeArea.setText("// ⏳ Đang gọi AI sinh Generator...\n");
                        checkerCodeArea.setText("// ⏳ Đang gọi AI sinh Checker...\n");
                    }
                    @Override public void onComplete(String gen, String check) {
                        generatorCodeArea.setText(gen);
                        checkerCodeArea.setText(check);
                    }
                    @Override public void onError(String msg) {
                        generatorCodeArea.setText("// ❌ Lỗi: " + msg);
                        checkerCodeArea.setText("");
                    }
                });
        });

        JButton btnRunCpp = new JButton("▶ Chạy Code C++ để Sinh 20 Testcase vào Database");
        btnRunCpp.setBackground(new Color(255, 140, 0));
        btnRunCpp.setForeground(Color.WHITE);
        btnRunCpp.addActionListener(e -> {
             String genCode = generatorCodeArea.getText();
             String acCode = (sampleACArea != null) ? sampleACArea.getText() : "";
             if (genCode.isEmpty() || acCode.isEmpty() || genCode.startsWith("//") || acCode.startsWith("Đang")) {
                 JOptionPane.showMessageDialog(this, "⚠ Cần phải có Code Generator (Tab 2) và Mã Mẫu AC (Tab 3) để sinh Testcase.\nVui lòng bấm 'Yêu cầu AI' ở cả 2 Tab trước!", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                 return;
             }
             
             evalController.compileAndGenerateTestcases(genCode, acCode, 1, 20, new EvaluationController.EvaluationListener() {
                 @Override public void onStart() {
                     JOptionPane.showMessageDialog(null, "Bắt đầu tiến trình sinh Testcase ở chạy nền. Vui lòng xem Log ở Tab 4.");
                     if(evalLogArea != null) evalLogArea.append("=== BẮT ĐẦU SINH TESTCASE BẰNG C++ ===\n");
                 }
                 @Override public void onProgress(int cur, int tot, String status) {
                     SwingUtilities.invokeLater(() -> {
                         if(evalProgressBar != null) {
                             evalProgressBar.setValue((int)((cur / (double)tot) * 100));
                             evalProgressBar.setString(status);
                         }
                         if(evalLogArea != null) evalLogArea.append(status + "\n");
                     });
                 }
                 @Override public void onComplete(EvaluationReport report) {
                     SwingUtilities.invokeLater(() -> {
                         if(evalLogArea != null) evalLogArea.append("✅ Hoàn tất sinh và lưu bộ số liệu vào Database!\n");
                         JOptionPane.showMessageDialog(null, "Sinh Testcase thành công! CSDl đã được cập nhật.");
                     });
                 }
                 @Override public void onError(Exception e) {
                     SwingUtilities.invokeLater(() -> {
                         if(evalLogArea != null) evalLogArea.append("❌ LỖI KHI SINH: " + e.getMessage() + "\n");
                         JOptionPane.showMessageDialog(null, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                     });
                 }
             });
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER));
        top.add(btnGen);
        top.add(btnRunCpp);
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
        btnGen.addActionListener(e -> {
            if (!validateProblemInput()) return;
            controller.generateSampleCodes(problemInputArea.getText(),
                new TeacherController.SampleCodeListener() {
                    @Override public void onStart() {
                        sampleACArea.setText("⏳ Đang gọi AI sinh code tối ưu nhất (AC)...");
                        sampleWAArea.setText("⏳ Đang tìm lỗi sai logic để tạo mã WA...");
                    }
                    @Override public void onComplete(String ac, String wa) {
                        sampleACArea.setText(ac);
                        sampleWAArea.setText(wa);
                    }
                    @Override public void onError(String msg) {
                        sampleACArea.setText("❌ Lỗi: " + msg);
                        sampleWAArea.setText("");
                    }
                });
        });

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(btnGen, BorderLayout.NORTH);
        pnl.add(codePanel, BorderLayout.CENTER);
        return pnl;
    }

    // ── Tab 4: Đánh giá chất lượng Testcase ────────────────────────────────

    private JPanel createTab4() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: nút + progress bar + nhãn chế độ
        btnRunEval = new JButton("▶ Khởi chạy kiểm thử");
        btnRunEval.setFont(new Font("Arial", Font.BOLD, 13));
        btnRunEval.setBackground(new Color(34, 139, 34));
        btnRunEval.setForeground(Color.WHITE);
        btnRunEval.addActionListener(e -> runEvaluation());

        evalProgressBar = new JProgressBar(0, 100);
        evalProgressBar.setStringPainted(true);
        evalProgressBar.setPreferredSize(new Dimension(350, 26));

        lblEvalMode = new JLabel("Chế độ: Mock (chưa có code từ Tab 2/3)");
        lblEvalMode.setForeground(new Color(150, 80, 0));
        lblEvalMode.setFont(new Font("Arial", Font.ITALIC, 12));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        topPanel.add(btnRunEval);
        topPanel.add(evalProgressBar);
        topPanel.add(lblEvalMode);

        // Log area
        evalLogArea = new JTextArea();
        evalLogArea.setEditable(false);
        evalLogArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        evalLogArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(evalLogArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Báo cáo đánh giá Chất lượng Testcase"));

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scroll,   BorderLayout.CENTER);
        return panel;
    }

    private void runEvaluation() {
        // Lấy code thực từ Tab 2 (Checker) và Tab 3 (AC, WA)
        String checkerCode = (checkerCodeArea != null) ? checkerCodeArea.getText().trim() : "";
        String acCode      = (sampleACArea   != null) ? sampleACArea.getText().trim()   : "";
        String waCode      = (sampleWAArea   != null) ? sampleWAArea.getText().trim()   : "";

        // Bỏ qua nếu còn là placeholder mặc định (chưa sinh)
        if (checkerCode.startsWith("// Đang")) checkerCode = "";
        if (acCode.startsWith("Đang gọi"))    acCode      = "";
        if (waCode.startsWith("Đang tìm"))    waCode      = "";

        // Cập nhật nhãn chế độ
        boolean usingReal = !acCode.isEmpty() || !waCode.isEmpty();
        lblEvalMode.setText(usingReal
            ? "✅ Chế độ: Dữ liệu thực từ Tab 2 & 3"
            : "⚠ Chế độ: Mock (Tab 2/3 chưa sinh code)");
        lblEvalMode.setForeground(usingReal ? new Color(0, 120, 0) : new Color(150, 80, 0));

        final String fc = checkerCode, fa = acCode, fw = waCode;
        evalController.runEvaluation(fc, fa, fw, new EvaluationController.EvaluationListener() {
            @Override public void onStart() {
                btnRunEval.setEnabled(false);
                evalProgressBar.setValue(0);
                evalProgressBar.setString("Đang khởi động...");
                evalLogArea.setText("=== KHỞI CHẠY KIỂM THỬ ===\n");
                evalLog(usingReal
                    ? "Sử dụng code thực từ Tab 2 (Checker) & Tab 3 (AC/WA)...\n\n"
                    : "[MOCK] Chưa có code từ Tab 2/3, dùng dữ liệu mẫu...\n\n");
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

                    // Kết quả chi tiết từng testcase
                    evalLog("\n─────────── KẾT QUẢ THỰC THI ───────────\n");
                    for (EvaluationResult res : report.getDetailedResults()) {
                        String icon = "AC".equals(res.getStatus()) ? "✅" : "❌";
                        evalLog(String.format("%s Code #%d | TC #%d | %-3s | %dms%n",
                            icon, res.getSubmissionId(), res.getTestcaseId(),
                            res.getStatus(), res.getExecutionTimeMs()));
                        if (!"AC".equals(res.getStatus()) && res.getActualOutput() != null)
                            evalLog("   → Output: \"" + res.getActualOutput().trim() + "\"\n");
                    }

                    // Tổng kết + cảnh báo yếu testcase từ EvaluationService
                    evalLog("\n═══════════════════════════════════════\n");
                    evalLog(report.generateSummary());

                    // Highlight nếu không có cảnh báo
                    if (report.getWarnings().isEmpty()) {
                        evalLog("\n🏆 Bộ testcase ĐỦ MẠNH — AC pass, WA/TLE bị bắt đúng!\n");
                    }
                });
            }
            @Override public void onError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setString("Lỗi");
                    btnRunEval.setEnabled(true);
                    evalLog("\n❌ LỖI HỆ THỐNG: " + e.getMessage() + "\n");
                });
            }
        });
    }

    private void evalLog(String text) {
        evalLogArea.append(text);
        evalLogArea.setCaretPosition(evalLogArea.getDocument().getLength());
    }
}