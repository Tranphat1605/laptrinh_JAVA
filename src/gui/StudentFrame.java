package gui;

import bll.SandboxService;
import entity.ExecutionResult;

import javax.swing.*;
import java.awt.*;

public class StudentFrame extends JFrame {
    private JComboBox<String> problemComboBox;
    private JComboBox<String> languageComboBox;
    private JTextArea problemDescriptionArea;
    private JTextArea codeTextArea;
    private JTextArea resultTextArea;
    private JButton submitButton;
    private SandboxService sandboxService;

    public StudentFrame() {
        setTitle("Giao Diện Học Sinh - Nộp Bài");
        setSize(950, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        
        sandboxService = new SandboxService();

        // Top panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Chọn bài tập:"));
        problemComboBox = new JComboBox<>(new String[]{"Bài 1: A+B Cơ bản", "Bài 2: Số nguyên tố"});
        topPanel.add(problemComboBox);

        topPanel.add(new JLabel(" Ngôn ngữ:"));
        languageComboBox = new JComboBox<>(new String[]{"java", "cpp", "python"});
        topPanel.add(languageComboBox);

        submitButton = new JButton("Nộp bài / Chấm code");
        topPanel.add(submitButton);

        add(topPanel, BorderLayout.NORTH);

        // Center split pane
        problemDescriptionArea = new JTextArea();
        problemDescriptionArea.setEditable(false);
        problemDescriptionArea.setFont(new Font("Arial", Font.PLAIN, 14));
        problemDescriptionArea.setLineWrap(true);
        problemDescriptionArea.setWrapStyleWord(true);
        problemDescriptionArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane problemScrollPane = new JScrollPane(problemDescriptionArea);
        problemScrollPane.setBorder(BorderFactory.createTitledBorder("Đề bài"));

        codeTextArea = new JTextArea("public class Main {\n    public static void main(String[] args) {\n        // Your code here\n    }\n}");
        codeTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane codeScrollPane = new JScrollPane(codeTextArea);
        codeScrollPane.setBorder(BorderFactory.createTitledBorder("Mã nguồn"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, problemScrollPane, codeScrollPane);
        splitPane.setResizeWeight(0.4);
        add(splitPane, BorderLayout.CENTER);

        // Bottom log
        resultTextArea = new JTextArea(10, 50);
        resultTextArea.setEditable(false);
        resultTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultTextArea.setForeground(new Color(0, 100, 0));
        JScrollPane resultScrollPane = new JScrollPane(resultTextArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder("Kết quả chấm (Evaluation Result)"));
        add(resultScrollPane, BorderLayout.SOUTH);

        // Events
        problemComboBox.addActionListener(e -> updateDescription());
        submitButton.addActionListener(e -> handleSubmit());

        updateDescription();
    }

    private void updateDescription() {
        if (problemComboBox.getSelectedIndex() == 0) {
            problemDescriptionArea.setText("Mô tả:\nNhập vào 2 số nguyên A và B cách nhau bởi khoảng trắng.\nIn ra tổng của A và B.\n\nGiới hạn:\n-10^9 <= A, B <= 10^9\n\nVí dụ Input:\n1 2\nVí dụ Output:\n3");
        } else {
            problemDescriptionArea.setText("Mô tả:\nNhập vào số nguyên dương N. Kiểm tra N có phải là số nguyên tố không.\nIn ra YES hoặc NO.\n\nVí dụ Input:\n7\nVí dụ Output:\nYES");
        }
        problemDescriptionArea.setCaretPosition(0);
    }

    private void handleSubmit() {
        String code = codeTextArea.getText();
        String lang = (String) languageComboBox.getSelectedItem();
        
        resultTextArea.setText("Đang biên dịch và thực thi trên Sandbox...\n\n");
        submitButton.setEnabled(false);

        // Chạy ngầm tránh lag giao diện
        new Thread(() -> {
            try {
                // Mock testcase thử nghiệm từ đề số 1
                String mockInput = "1 2\n";
                ExecutionResult result = sandboxService.executeCode(code, lang, mockInput, 2000L);
                
                SwingUtilities.invokeLater(() -> {
                    resultTextArea.append("--- CHI TIẾT THỰC THI ---\n");
                    resultTextArea.append("Trạng thái (Status): " + result.getStatus() + "\n");
                    resultTextArea.append("Thời gian (Time): " + result.getExecutionTime() + " ms\n");
                    if (result.getError() != null && !result.getError().isEmpty()) {
                        resultTextArea.append("Lỗi (Error):\n" + result.getError() + "\n");
                    } else {
                        resultTextArea.append("Đầu ra (Output):\n" + result.getOutput() + "\n");
                    }
                    submitButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    resultTextArea.append("Lỗi hệ thống: " + ex.getMessage());
                    submitButton.setEnabled(true);
                });
            }
        }).start();
    }
}