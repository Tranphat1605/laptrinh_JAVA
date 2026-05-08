package gui;

import controller.StudentController;
import entity.ExecutionResult;

import java.awt.*;
import javax.swing.*;

/**
 * View: chỉ chứa code xây dựng giao diện Swing cho Học Sinh.
 * Logic mô tả đề bài và chấm code đã được chuyển sang StudentController.
 */
public class StudentFrame extends JFrame {

    // ── UI Components ──
    private JComboBox<String> problemComboBox;
    private JComboBox<String> languageComboBox;
    private JTextArea problemDescriptionArea;
    private JTextArea codeTextArea;
    private JTextArea resultTextArea;
    private JButton   submitButton;

    // ── Controller ──
    private final StudentController controller;

    public StudentFrame() {
        setTitle("Giao Diện Học Sinh - Nộp Bài");
        setSize(950, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        controller = new StudentController();

        buildUI();
        loadDescription(); // hiển thị đề đầu tiên
    }

    private void buildUI() {
        // ── Top panel ──
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Chọn bài tập:"));
        problemComboBox = new JComboBox<>(new String[]{"Bài 1: A+B Cơ bản", "Bài 2: Số nguyên tố"});
        topPanel.add(problemComboBox);

        topPanel.add(new JLabel("  Ngôn ngữ:"));
        languageComboBox = new JComboBox<>(new String[]{"java", "cpp", "python"});
        topPanel.add(languageComboBox);

        submitButton = new JButton("Nộp bài / Chấm code");
        topPanel.add(submitButton);
        add(topPanel, BorderLayout.NORTH);

        // ── Center split pane ──
        problemDescriptionArea = new JTextArea();
        problemDescriptionArea.setEditable(false);
        problemDescriptionArea.setFont(new Font("Arial", Font.PLAIN, 14));
        problemDescriptionArea.setLineWrap(true);
        problemDescriptionArea.setWrapStyleWord(true);
        problemDescriptionArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane problemScrollPane = new JScrollPane(problemDescriptionArea);
        problemScrollPane.setBorder(BorderFactory.createTitledBorder("Đề bài"));

        codeTextArea = new JTextArea(
            "public class Main {\n    public static void main(String[] args) {\n        // Your code here\n    }\n}");
        codeTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane codeScrollPane = new JScrollPane(codeTextArea);
        codeScrollPane.setBorder(BorderFactory.createTitledBorder("Mã nguồn"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, problemScrollPane, codeScrollPane);
        splitPane.setResizeWeight(0.4);
        add(splitPane, BorderLayout.CENTER);

        // ── Bottom result log ──
        resultTextArea = new JTextArea(10, 50);
        resultTextArea.setEditable(false);
        resultTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultTextArea.setForeground(new Color(0, 100, 0));
        JScrollPane resultScrollPane = new JScrollPane(resultTextArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder("Kết quả chấm (Evaluation Result)"));
        add(resultScrollPane, BorderLayout.SOUTH);

        // ── Events ──
        problemComboBox.addActionListener(e -> loadDescription());
        submitButton.addActionListener(e -> handleSubmit());
    }

    /** Cập nhật mô tả đề — lấy từ controller, không có logic ở đây. */
    private void loadDescription() {
        int idx = problemComboBox.getSelectedIndex();
        problemDescriptionArea.setText(controller.getDescription(idx));
        problemDescriptionArea.setCaretPosition(0);
    }

    /** Nộp code — uỷ thác hoàn toàn cho controller. */
    private void handleSubmit() {
        String code = codeTextArea.getText();
        String lang = (String) languageComboBox.getSelectedItem();
        int idx     = problemComboBox.getSelectedIndex();

        controller.submitCode(code, lang, idx, new StudentController.SubmitListener() {
            @Override public void onStart() {
                resultTextArea.setText("Đang biên dịch và thực thi trên Sandbox...\n\n");
                submitButton.setEnabled(false);
            }
            @Override public void onComplete(ExecutionResult result) {
                resultTextArea.append("--- CHI TIẾT THỰC THI ---\n");
                resultTextArea.append("Trạng thái: " + result.getStatus() + "\n");
                resultTextArea.append("Thời gian:  " + result.getExecutionTime() + " ms\n");
                if (result.getError() != null && !result.getError().isEmpty()) {
                    resultTextArea.append("Lỗi:\n" + result.getError() + "\n");
                } else {
                    resultTextArea.append("Đầu ra:\n" + result.getOutput() + "\n");
                }
                submitButton.setEnabled(true);
            }
            @Override public void onError(String message) {
                resultTextArea.append("Lỗi hệ thống: " + message);
                submitButton.setEnabled(true);
            }
        });
    }
}