package gui;

import controller.StudentController;
import entity.ExecutionResult;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * View: chỉ chứa code xây dựng giao diện Swing cho Học Sinh.
 * Logic mô tả đề bài và chấm code đã được chuyển sang StudentController.
 */
public class StudentFrame extends JFrame {

    // ── Controllers ──
    private final StudentController controller;

    // ── CardLayout for Screen Navigation ──
    private JPanel mainPanel;
    private CardLayout cardLayout;

    // ── UI Components for "Problem List" Screen ──
    private JTable problemTable;

    // ── UI Components for "Solve Problem" Screen ──
    private JLabel solveTitleLabel;
    private JComboBox<String> languageComboBox;
    private JEditorPane problemDescriptionArea;
    private JTextArea codeTextArea;
    private JTextArea resultTextArea;
    private JButton submitButton;
    private int currentProblemIndex = 0;

    public StudentFrame() {
        // Áp dụng giao diện hệ thống
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        setTitle("Giao Diện Học Sinh - Nộp Bài & Chấm Điểm");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        controller = new StudentController();

        // Cài đặt CardLayout để chuyển đổi màn hình (giống navigation web)
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(buildProblemListPanel(), "ProblemList");
        mainPanel.add(buildSolveProblemPanel(), "SolveProblem");

        add(mainPanel);
        
        // Show màn bài tập đầu tiên
        cardLayout.show(mainPanel, "ProblemList");
    }

    /** Màn hình 1: Danh sách bài tập (Giống Codeforces Problemset) */
    private JPanel buildProblemListPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(245, 247, 250));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Tiêu đề
        JLabel title = new JLabel("Danh sách Bài Tập (Problemset)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(41, 128, 185));
        panel.add(title, BorderLayout.NORTH);

        // Bảng danh sách bài tập
        String[] columns = {"ID", "Tên bài tập", "Độ khó", "Trạng thái", "Hành động"};
        Object[][] data = {
            {"1", "Bài 1: A+B Cơ bản", "Dễ", "Chưa làm", "Vào làm bài ->"},
            {"2", "Bài 2: Số nguyên tố", "Trung bình", "Chưa làm", "Vào làm bài ->"}
        };
        DefaultTableModel model = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Không cho phép sửa data trên bảng
            }
        };

        problemTable = new JTable(model);
        problemTable.setRowHeight(40);
        problemTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        problemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        problemTable.setShowVerticalLines(false);
        problemTable.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader header = problemTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(new Color(41, 128, 185));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));

        // Custom căn giữa cho vài cột
        // ... (Tuỳ chỉnh giao diện bảng nếu cần)

        JScrollPane scrollPane = new JScrollPane(problemTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        scrollPane.getViewport().setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Bắt sự kiện double-click hoặc click để mở bài
        problemTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openProblem(problemTable.getSelectedRow());
                }
            }
        });

        // Nút mở bài thủ công
        JButton btnOpen = new JButton("Mở bài đang chọn");
        btnOpen.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnOpen.setFocusPainted(false);
        // Loại bỏ màu custom để hiển thị đúng button mặc định của Windows nếu bị ẩn text
        btnOpen.addActionListener(e -> {
            int row = problemTable.getSelectedRow();
            if (row != -1) openProblem(row);
            else JOptionPane.showMessageDialog(panel, "Vui lòng chọn một bài tập trên bảng!");
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);
        bottomPanel.add(btnOpen);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    /** Màn hình 2: Màn hình Làm bài & Chấm Code */
    private JPanel buildSolveProblemPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(245, 247, 250));

        // ── Top panel (Thanh điều hướng quay lại & Nộp bài) ──
        JPanel topPanel = new JPanel(new BorderLayout(15, 10));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
            new EmptyBorder(10, 15, 10, 15)
        ));

        // Nút Back
        JButton btnBack = new JButton("<< Quay lại Problemset");
        btnBack.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnBack.setFocusPainted(false);
        btnBack.setContentAreaFilled(false);
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> {
            cardLayout.show(mainPanel, "ProblemList");
        });

        solveTitleLabel = new JLabel("Đang tải...");
        solveTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        solveTitleLabel.setForeground(new Color(41, 128, 185));

        JPanel topLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        topLeftPanel.setOpaque(false);
        topLeftPanel.add(btnBack);
        topLeftPanel.add(new JLabel("|"));
        topLeftPanel.add(solveTitleLabel);
        topPanel.add(topLeftPanel, BorderLayout.WEST);

        // Chọn ngôn ngữ & Nộp
        JPanel topRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topRightPanel.setOpaque(false);
        
        JLabel lblLang = new JLabel("Ngôn ngữ:");
        lblLang.setFont(new Font("Segoe UI", Font.BOLD, 14));
        topRightPanel.add(lblLang);

        languageComboBox = new JComboBox<>(new String[]{"java", "cpp", "python"});
        languageComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        topRightPanel.add(languageComboBox);

        submitButton = new JButton("Nộp bài / Submit");
        submitButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        submitButton.setFocusPainted(false);
        submitButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        submitButton.addActionListener(e -> handleSubmit());
        topRightPanel.add(submitButton);

        topPanel.add(topRightPanel, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

        // ── Center split pane (Chia đôi màn hình) ──
        problemDescriptionArea = new JEditorPane();
        problemDescriptionArea.setContentType("text/html");
        problemDescriptionArea.setEditable(false);
        problemDescriptionArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        problemDescriptionArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        problemDescriptionArea.setMargin(new Insets(15, 15, 15, 15));
        
        JScrollPane problemScrollPane = new JScrollPane(problemDescriptionArea);
        problemScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Mô tả Đề bài", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14), new Color(41, 128, 185)
        ));
        problemScrollPane.getViewport().setBackground(Color.WHITE);

        codeTextArea = new JTextArea(
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        // Viết mã nguồn của bạn tại đây...\n" +
            "    }\n" +
            "}");
        codeTextArea.setFont(new Font("Consolas", Font.PLAIN, 16));
        codeTextArea.setBackground(new Color(40, 42, 54)); // Dark theme code editor
        codeTextArea.setForeground(new Color(248, 248, 242));
        codeTextArea.setCaretColor(Color.WHITE);
        codeTextArea.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane codeScrollPane = new JScrollPane(codeTextArea);
        codeScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Trình soạn thảo (Code Editor)", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14), new Color(41, 128, 185)
        ));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, problemScrollPane, codeScrollPane);
        splitPane.setResizeWeight(0.4);
        splitPane.setDividerSize(5);
        splitPane.setBorder(new EmptyBorder(10, 10, 5, 10));
        splitPane.setBackground(new Color(245, 247, 250));
        panel.add(splitPane, BorderLayout.CENTER);

        // ── Bottom result log (Khung kết quả chấm) ──
        resultTextArea = new JTextArea(8, 50);
        resultTextArea.setEditable(false);
        resultTextArea.setFont(new Font("Consolas", Font.BOLD, 14));
        resultTextArea.setBackground(new Color(30, 30, 30));
        resultTextArea.setForeground(new Color(0, 255, 0)); // Màu Auto Xanh lá
        resultTextArea.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane resultScrollPane = new JScrollPane(resultTextArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Kết quả thực thi (Console Log)", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14), new Color(41, 128, 185)
        ));
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.add(resultScrollPane, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    /** Hàm xử lý khi ấn chọn một bài tập từ Danh sách */
    private void openProblem(int idx) {
        currentProblemIndex = idx;
        String problemName = problemTable.getValueAt(idx, 1).toString();
        solveTitleLabel.setText(problemName);
        
        // Lấy mô tả đề
        String desc = controller.getDescription(idx);
        String htmlDesc = "<html><body style='font-family: Arial; font-size: 14px; padding: 5px; color: #333; line-height: 1.5;'>" 
                        + desc.replace("\n", "<br>") 
                        + "</body></html>";
                        
        problemDescriptionArea.setText(htmlDesc);
        problemDescriptionArea.setCaretPosition(0);

        // Gọi màn hình Solve lật lên
        cardLayout.show(mainPanel, "SolveProblem");
    }

    /** Nộp code — uỷ thác hoàn toàn cho controller. */
    private void handleSubmit() {
        String code = codeTextArea.getText();
        String lang = (String) languageComboBox.getSelectedItem();

        controller.submitCode(code, lang, currentProblemIndex, new StudentController.SubmitListener() {
            @Override public void onStart() {
                resultTextArea.setForeground(Color.YELLOW);
                resultTextArea.setText("[...] Đang biên dịch và thực thi trên Sandbox (Vui lòng chờ)...\n\n");
                submitButton.setEnabled(false);
                submitButton.setText("Đang chấm...");
            }
            @Override public void onComplete(ExecutionResult result) {
                resultTextArea.setText("--- CHI TIẾT THỰC THI ---\n");
                
                if (result.getStatus().equals("SUCCESS") || result.getStatus().equals("AC")) {
                    resultTextArea.setForeground(new Color(46, 204, 113)); // Xanh lá
                    resultTextArea.append("Trạng thái: ACCEPTED (" + result.getStatus() + ")\n");
                    // Update bảng cho ảo diệu
                    problemTable.setValueAt("Đã giải", currentProblemIndex, 3);
                } else {
                    resultTextArea.setForeground(new Color(231, 76, 60)); // Đỏ
                    resultTextArea.append("Trạng thái: Lỗi / " + result.getStatus() + "\n");
                    problemTable.setValueAt("Sai (" + result.getStatus() + ")", currentProblemIndex, 3);
                }
                
                resultTextArea.append("Thời gian:  " + result.getExecutionTime() + " ms\n");
                
                if (result.getError() != null && !result.getError().isEmpty()) {
                    resultTextArea.setForeground(new Color(231, 76, 60));
                    resultTextArea.append("Lỗi:\n" + result.getError() + "\n");
                } else {
                    resultTextArea.append("Đầu ra:\n" + result.getOutput() + "\n");
                }
                
                submitButton.setEnabled(true);
                submitButton.setText("Nộp bài / Submit");
            }
            @Override public void onError(String message) {
                resultTextArea.setForeground(new Color(231, 76, 60)); // Đỏ
                resultTextArea.setText("Lỗi hệ thống Sandbox: \n" + message);
                submitButton.setEnabled(true);
                submitButton.setText("Nộp bài / Submit");
            }
        });
    }
}