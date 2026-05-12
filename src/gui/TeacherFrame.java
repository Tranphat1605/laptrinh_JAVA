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

    // ── Navigation & Status ──
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private int currentStep = 0;
    private final String[] stepNames = {"1. Phân tích đề", "2. Sinh Testcase", "3. Mã chuẩn", "4. Kiểm thử"};
    private JLabel[] stepLabels;
    private JButton btnBack;
    private JButton btnNext;
    private JLabel lblStatusBar;

    // ── Bước 4 UI ──
    private JTextArea    evalLogArea;
    private JProgressBar evalProgressBar;
    private JButton      btnRunEval;
    private JLabel       lblEvalMode;

    // ── Dữ liệu phân tích bài tập ──
    private Problem currentProblem = null;

    public TeacherFrame() {
        setTitle("Giao Diện Giáo Viên (Sinh Đề & Testcase bằng AI)");
        setSize(1100, 800);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        controller     = new TeacherController("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");
        evalController = new EvaluationController();

        // Top: Stepper
        add(createStepperPanel(), BorderLayout.NORTH);

        // Center: Cards
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(createTab1(), "STEP_0");
        cardPanel.add(createTab2(), "STEP_1");
        cardPanel.add(createTab3(), "STEP_2");
        cardPanel.add(createTab4(), "STEP_3");
        add(cardPanel, BorderLayout.CENTER);

        // Bottom: Navigation & Status Bar
        add(createBottomPanel(), BorderLayout.SOUTH);

        updateWizardUI();
    }

    private JPanel createStepperPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        stepLabels = new JLabel[stepNames.length];
        for (int i = 0; i < stepNames.length; i++) {
            stepLabels[i] = new JLabel(stepNames[i]);
            stepLabels[i].setFont(new Font("Arial", Font.BOLD, 14));
            panel.add(stepLabels[i]);
            if (i < stepNames.length - 1) {
                JLabel arrow = new JLabel(" ➔ ");
                arrow.setForeground(Color.LIGHT_GRAY);
                panel.add(arrow);
            }
        }
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        panel.setBackground(new Color(245, 245, 245));

        lblStatusBar = new JLabel("AI Service: Sẵn sàng | Sức mạnh: Groq Llama-3.3-70b");
        lblStatusBar.setFont(new Font("Arial", Font.PLAIN, 12));
        lblStatusBar.setForeground(Color.DARK_GRAY);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        navPanel.setOpaque(false);

        btnBack = new JButton("< Quay lại");
        btnBack.setFont(new Font("Arial", Font.BOLD, 13));
        btnBack.addActionListener(e -> {
            if (currentStep > 0) {
                currentStep--;
                cardLayout.show(cardPanel, "STEP_" + currentStep);
                updateWizardUI();
            }
        });

        btnNext = new JButton("Tiếp theo >");
        btnNext.setFont(new Font("Arial", Font.BOLD, 13));
        btnNext.setBackground(new Color(40, 120, 255));
        btnNext.setForeground(Color.WHITE);
        btnNext.addActionListener(e -> {
            if (currentStep < stepNames.length - 1) {
                currentStep++;
                cardLayout.show(cardPanel, "STEP_" + currentStep);
                updateWizardUI();
            }
        });

        navPanel.add(btnBack);
        navPanel.add(btnNext);

        panel.add(lblStatusBar, BorderLayout.WEST);
        panel.add(navPanel, BorderLayout.EAST);
        return panel;
    }

    private void updateWizardUI() {
        btnBack.setEnabled(currentStep > 0);
        btnNext.setEnabled(currentStep < stepNames.length - 1);

        for (int i = 0; i < stepLabels.length; i++) {
            if (i == currentStep) {
                stepLabels[i].setForeground(new Color(40, 120, 255)); // Blue active
                stepLabels[i].setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(40, 120, 255)));
            } else if (i < currentStep) {
                stepLabels[i].setForeground(new Color(34, 139, 34)); // Green passed
                stepLabels[i].setBorder(null);
            } else {
                stepLabels[i].setForeground(Color.LIGHT_GRAY); // Gray pending
                stepLabels[i].setBorder(null);
            }
        }
    }

    public void updateStatus(String st) {
        if (lblStatusBar != null) {
            SwingUtilities.invokeLater(() -> lblStatusBar.setText(st));
        }
    }

    // ── Bước 1: Nhập đề & Phân tích ──────────────────────────────────────────

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
                        currentProblem = p;
                        aiAnalysisLogArea.append("✅ Kết quả từ AI:\n");
                        aiAnalysisLogArea.append("📌 Tên bài: " + p.getTitle() + "\n");
                        aiAnalysisLogArea.append("⏱ Thời gian: " + p.getTimeLimitMs() + "ms | 💾 Bộ nhớ: " + p.getMemoryLimitMb() + "MB\n");
                        aiAnalysisLogArea.append("--------------------------------------------------\n");
                        aiAnalysisLogArea.append(p.getContent());
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

    // ── Bước 2: Sinh Testcase & Checker ──────────────────────────────────────

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
            if (currentProblem == null) {
                 JOptionPane.showMessageDialog(this, "⚠ Vui lòng phân tích đề bài ở Bước 1 trước khi tự động sinh!", "Chưa có đề", JOptionPane.WARNING_MESSAGE);
                 return;
            }
            controller.generateTestcaseAndChecker(currentProblem,
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

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER));
        top.add(btnGen);
        panel.add(top, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // ── Bước 3: Sinh Code Mẫu AC / WA ────────────────────────────────────────

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
            if (currentProblem == null) {
                 JOptionPane.showMessageDialog(this, "⚠ Vui lòng hoàn thành phân tích đề ở Bước 1!", "Chưa có đề", JOptionPane.WARNING_MESSAGE);
                 return;
            }
            controller.generateSampleCodes(currentProblem,
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

        JButton btnRunCpp = new JButton("> Chạy Code C++ để Sinh 20 Testcase vào Database");
        btnRunCpp.setBackground(new Color(255, 140, 0));
        btnRunCpp.setForeground(Color.WHITE);
        btnRunCpp.addActionListener(e -> {
             String genCode = generatorCodeArea.getText();
             String acCode = (sampleACArea != null) ? sampleACArea.getText() : "";
             if (genCode.isEmpty() || genCode.startsWith("//") || genCode.startsWith("Đang")) {
                 JOptionPane.showMessageDialog(this, "⚠ Cần phải có Code Generator (Bước 2) để sinh Testcase.\nVui lòng quay lại Bước 2 bấm 'Yêu cầu AI sinh Testcase & Checker' trước!", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                 return;
             }
             if (acCode.isEmpty() || acCode.startsWith("Đang")) {
                 JOptionPane.showMessageDialog(this, "⚠ Cần phải có Mã Mẫu AC để xuất ra đáp án đúng.\nVui lòng bấm 'Yêu cầu AI tự động sinh Code mẫu Tốt / Xấu' ở Bước 3 này trước!", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                 return;
             }
             
             // --- TẠO DIALOG HIỂN THỊ TIẾN TRÌNH SINH TESTCASE ---
             JDialog progressDialog = new JDialog(TeacherFrame.this, "Tiến trình Biên dịch & Sinh Testcase", true);
             progressDialog.setSize(600, 400);
             progressDialog.setLocationRelativeTo(TeacherFrame.this);
             progressDialog.setLayout(new BorderLayout(10, 10));
             
             JProgressBar pb = new JProgressBar(0, 100);
             pb.setStringPainted(true);

             JTextArea logArea = new JTextArea();
             logArea.setEditable(false);
             logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
             logArea.setMargin(new Insets(8, 8, 8, 8));
             logArea.setBackground(new Color(245, 245, 245));
             
             JButton btnClose = new JButton("Khóa luồng / Đang chạy...");
             btnClose.setEnabled(false);
             
             JPanel topP = new JPanel(new BorderLayout(5, 5));
             topP.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
             topP.add(new JLabel("Đang kích hoạt môi trường C++ và chuẩn bị sinh Testcases (vào Database)..."), BorderLayout.NORTH);
             topP.add(pb, BorderLayout.CENTER);
             
             progressDialog.add(topP, BorderLayout.NORTH);
             progressDialog.add(new JScrollPane(logArea), BorderLayout.CENTER);
             
             JPanel botP = new JPanel(new FlowLayout(FlowLayout.RIGHT));
             botP.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
             botP.add(btnClose);
             progressDialog.add(botP, BorderLayout.SOUTH);

             evalController.compileAndGenerateTestcases(genCode, acCode, currentProblem, 20, new EvaluationController.EvaluationListener() {
                 @Override public void onStart() {
                     SwingUtilities.invokeLater(() -> {
                         updateStatus("🔄 Đang thao tác CSDL...");
                         logArea.append("=== BẮT ĐẦU QUÁ TRÌNH SINH TESTCASE ===\n\n");
                     });
                 }
                 @Override public void onProgress(int cur, int tot, String status) {
                     SwingUtilities.invokeLater(() -> {
                         updateStatus("⚙ Tiến độ sinh: " + status);
                         pb.setValue((int)((cur / (double)tot) * 100));
                         pb.setString(status);
                         logArea.append(status + "\n");
                         logArea.setCaretPosition(logArea.getDocument().getLength());
                     });
                 }
                 @Override public void onComplete(EvaluationReport report) {
                     SwingUtilities.invokeLater(() -> {
                         updateStatus("✅ AI Service: Sẵn sàng | Hoàn tất lưu Testcases!");
                         logArea.append("\n✅ HOÀN TẤT: Toàn bộ Testcases đã được lưu thành công vào Database!\n");
                         pb.setValue(100);
                         pb.setString("Hoàn thành quá trình sinh!");
                         
                         btnClose.setText("Đóng & Chuyển sang Bước 4");
                         btnClose.setEnabled(true);
                         btnClose.setBackground(new Color(34, 139, 34));
                         btnClose.setForeground(Color.WHITE);
                         
                         // Cài listener để đổi trang
                         for (java.awt.event.ActionListener al : btnClose.getActionListeners()) {
                             btnClose.removeActionListener(al);
                         }
                         btnClose.addActionListener(ev -> {
                             progressDialog.dispose();
                             // Tự động chuyển qua Bước 4
                             if (currentStep < stepNames.length - 1) {
                                 currentStep++;
                                 cardLayout.show(cardPanel, "STEP_" + currentStep);
                                 updateWizardUI();
                             }
                         });
                     });
                 }
                 @Override public void onError(Exception e) {
                     SwingUtilities.invokeLater(() -> {
                         updateStatus("❌ Có lỗi xảy ra trong quá trình sinh.");
                         logArea.append("\n❌ LỖI HỆ THỐNG / BIÊN DỊCH: " + e.getMessage() + "\n");
                         pb.setString("Phát hiện lỗi");
                         
                         btnClose.setText("Đóng");
                         btnClose.setEnabled(true);
                         for (java.awt.event.ActionListener al : btnClose.getActionListeners()) {
                             btnClose.removeActionListener(al);
                         }
                         btnClose.addActionListener(ev -> progressDialog.dispose());
                     });
                 }
             });
             
             progressDialog.setVisible(true); // Hiển thị Modal Dialog sẽ block tại đây cho tới khi đóng
        });

        JPanel topPnl = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPnl.add(btnGen);
        topPnl.add(btnRunCpp);

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(topPnl, BorderLayout.NORTH);
        pnl.add(codePanel, BorderLayout.CENTER);
        return pnl;
    }

    // ── Bước 4: Đánh giá chất lượng Testcase ────────────────────────────────

    private JPanel createTab4() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: nút + progress bar + nhãn chế độ
        btnRunEval = new JButton("Khởi chạy kiểm thử");
        btnRunEval.setFont(new Font("Arial", Font.BOLD, 13));
        btnRunEval.setBackground(new Color(34, 139, 34));
        btnRunEval.setForeground(Color.WHITE);
        btnRunEval.addActionListener(e -> {
            boolean usingReal = !isBlank(sampleACArea.getText()) || !isBlank(sampleWAArea.getText());
            if (!usingReal) {
                JOptionPane.showMessageDialog(this, "⚠ Không tìm thấy mã nguồn!\nVui lòng quay lại Bước 2 và 3 để sinh Checker và Code mẫu trước khi khởi chạy kiểm thử.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                return;
            }
            runEvaluation();
        });

        evalProgressBar = new JProgressBar(0, 100);
        evalProgressBar.setStringPainted(true);
        evalProgressBar.setPreferredSize(new Dimension(350, 26));

        lblEvalMode = new JLabel("Chế độ: Đợi thực thi (cần code từ Bước 2 & 3)");
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

    // Helper method để check blank
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
        if (!usingReal) {
            JOptionPane.showMessageDialog(this, "⚠ Không tìm thấy mã nguồn!\nVui lòng quay lại Bước 2 và 3 để sinh Checker và Code mẫu trước khi khởi chạy kiểm thử.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        lblEvalMode.setText("✅ Chế độ: Dữ liệu thực từ Bước 2 & 3");
        lblEvalMode.setForeground(new Color(0, 120, 0));

        final String fc = checkerCode, fa = acCode, fw = waCode;
        
        // --- TẠO DIALOG HIỂN THỊ TIẾN TRÌNH KIỂM THỬ ---
        JDialog progressDialog = new JDialog(TeacherFrame.this, "Đang Kiểm thử & Đánh giá Testcase", true);
        progressDialog.setSize(700, 500);
        progressDialog.setLocationRelativeTo(TeacherFrame.this);
        progressDialog.setLayout(new BorderLayout(10, 10));
        
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setStringPainted(true);
        
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        logArea.setBackground(new Color(245, 245, 245));
        
        JButton btnClose = new JButton("Đang chạy (Khóa luồng)...");
        btnClose.setEnabled(false);
        
        JPanel topP = new JPanel(new BorderLayout(5, 5));
        topP.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topP.add(new JLabel("Hệ thống đang chạy các Code mẫu (AC, WA) qua từng Testcase để đánh giá chất lượng..."), BorderLayout.NORTH);
        topP.add(pb, BorderLayout.CENTER);
        
        progressDialog.add(topP, BorderLayout.NORTH);
        progressDialog.add(new JScrollPane(logArea), BorderLayout.CENTER);
        
        JPanel botP = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botP.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        botP.add(btnClose);
        progressDialog.add(botP, BorderLayout.SOUTH);

        evalController.runEvaluation(currentProblem, fc, fa, fw, new EvaluationController.EvaluationListener() {
            @Override public void onStart() {
                btnRunEval.setEnabled(false);
                evalProgressBar.setValue(0);
                evalProgressBar.setString("Đang khởi động...");
                evalLogArea.setText("=== KHỞI CHẠY KIỂM THỬ ===\nSử dụng code thực từ Bước 2 (Checker) & Bước 3 (AC/WA)...\n\n");
                SwingUtilities.invokeLater(() -> {
                    updateStatus("🔄 Đang khởi động Sandbox...");
                    logArea.append("=== BẮT ĐẦU CHẠY SANDBOX ===\nSử dụng code thực từ Bước 2 (Checker) & Bước 3 (AC/WA)...\n");
                });
            }
            @Override public void onProgress(int cur, int tot, String status) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setValue(cur);
                    evalProgressBar.setString(status);
                    pb.setValue(cur);
                    pb.setString(status);
                    logArea.append("⚡ " + status + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
            @Override public void onComplete(EvaluationReport report) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setValue(100);
                    evalProgressBar.setString("Hoàn thành");
                    btnRunEval.setEnabled(true);
                    pb.setValue(100);
                    pb.setString("Hoàn thành kiểm thử!");

                    // Cập nhật lên UI gốc của Bước 4 để thầy cô tiện theo dõi
                    evalLog("\n─────────── KẾT QUẢ THỰC THI ───────────\n");
                    logArea.append("\n─────────── KẾT QUẢ THỰC THI ───────────\n");
                    for (EvaluationResult res : report.getDetailedResults()) {
                        String icon = "AC".equals(res.getStatus()) ? "✅" : "❌";
                        String msg = String.format("%s Code #%d | TC #%d | %-3s | %dms%n",
                            icon, res.getSubmissionId(), res.getTestcaseId(),
                            res.getStatus(), res.getExecutionTimeMs());
                        evalLog(msg);
                        logArea.append(msg);
                        if (!"AC".equals(res.getStatus()) && res.getActualOutput() != null) {
                            String outMsg = "   → Output: \"" + res.getActualOutput().trim() + "\"\n";
                            evalLog(outMsg);
                            logArea.append(outMsg);
                        }
                    }

                    // Tổng kết + cảnh báo yếu testcase từ EvaluationService
                    evalLog("\n═══════════════════════════════════════\n");
                    logArea.append("\n═══════════════════════════════════════\n");
                    evalLog(report.generateSummary());
                    logArea.append(report.generateSummary());

                    // Highlight nếu không có cảnh báo
                    if (report.getWarnings().isEmpty()) {
                        evalLog("\n🏆 Bộ testcase ĐỦ MẠNH — AC pass, WA/TLE bị bắt đúng!\n");
                        logArea.append("\n🏆 Bộ testcase ĐỦ MẠNH — AC pass, WA/TLE bị bắt đúng!\n");
                    }
                    
                    btnClose.setText("Đóng Báo Cáo");
                    btnClose.setEnabled(true);
                    btnClose.setBackground(new Color(34, 139, 34));
                    btnClose.setForeground(Color.WHITE);
                    for (java.awt.event.ActionListener al : btnClose.getActionListeners()) btnClose.removeActionListener(al);
                    btnClose.addActionListener(ev -> progressDialog.dispose());
                });
            }
            @Override public void onError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    evalProgressBar.setString("Lỗi");
                    btnRunEval.setEnabled(true);
                    evalLog("\n❌ LỖI HỆ THỐNG: " + e.getMessage() + "\n");
                    pb.setString("Phát hiện lỗi!");
                    logArea.append("\n❌ LỖI HỆ THỐNG: " + e.getMessage() + "\n");
                    
                    btnClose.setText("Đóng");
                    btnClose.setEnabled(true);
                    for (java.awt.event.ActionListener al : btnClose.getActionListeners()) btnClose.removeActionListener(al);
                    btnClose.addActionListener(ev -> progressDialog.dispose());
                });
            }
        });
        
        progressDialog.setVisible(true); // Hiển thị Modal Dialog
    }

    private void evalLog(String text) {
        evalLogArea.append(text);
        evalLogArea.setCaretPosition(evalLogArea.getDocument().getLength());
    }
}