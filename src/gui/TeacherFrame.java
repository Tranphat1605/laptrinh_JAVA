package gui;

import bll.AIService;
import entity.Problem;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class TeacherFrame extends JFrame {
    private JTextArea problemInputArea;
    private JTextArea aiAnalysisLogArea;
    private JButton btnAnalyzeAI;
    private JButton btnSelectImage;
    
    private File selectedImageFile = null;
    private AIService aiService;

    // Các thành phần kết quả sinh bằng AI
    private JTextArea generatorCodeArea;
    private JTextArea checkerCodeArea;
    private JTextArea sampleACArea;
    private JTextArea sampleWAArea;

    public TeacherFrame() {
        setTitle("Giao Diện Giáo Viên (Sinh Đề & Testcase bằng AI)");
        setSize(1100, 800);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Khởi tạo AI với 1 Key rỗng (Thay thế key gốc vào đây nếu bạn dùng OpenAI/Gemini thật)
        aiService = new AIService("AIzaSyBkO9X6wjwcREkpA-imSfqsJYSFzVirKhY");

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        
        tabbedPane.add("1. Nhập liệu & Phân tích", createTab1());
        tabbedPane.add("2. AI Sinh Testcase & Checker", createTab2());
        tabbedPane.add("3. AI Sinh Mẫu (AC, WA, TLE)", createTab3());
        // Tích hợp Đánh giá độ mạnh Testcase vào Tab 4
        tabbedPane.add("4. Đánh giá chất lượng Testcase", new TestcaseEvaluationFrame().getContentPane());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createTab1() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Trái: Nhập Nội dung chữ
        problemInputArea = new JTextArea("Paste đề thi IOI / ICPC bằng chữ vào đây...");
        problemInputArea.setLineWrap(true);
        problemInputArea.setWrapStyleWord(true);
        JScrollPane scroll1 = new JScrollPane(problemInputArea);
        scroll1.setBorder(BorderFactory.createTitledBorder("Nhập Đề thi (Text)"));

        // Dưới: Chọn ảnh + Action
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
        btnAnalyzeAI.addActionListener(e -> analyzeProblemWithAI());

        bottomInputPanel.add(btnSelectImage);
        bottomInputPanel.add(lblImageStatus);
        bottomInputPanel.add(new JLabel("   |   "));
        bottomInputPanel.add(btnAnalyzeAI);

        // Phải: Log kết quả
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

    private JPanel createTab2() {
        JPanel panel = new JPanel(new BorderLayout());
        generatorCodeArea = new JTextArea();
        checkerCodeArea = new JTextArea();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, 
            new JScrollPane(generatorCodeArea), 
            new JScrollPane(checkerCodeArea));
        splitPane.setResizeWeight(0.5);

        generatorCodeArea.setBorder(BorderFactory.createTitledBorder("Generator Code (Sinh Testcase - C++)"));
        checkerCodeArea.setBorder(BorderFactory.createTitledBorder("Checker Code (So khớp đáp án - C++)"));

        JPanel top = new JPanel();
        JButton btnGenMock = new JButton("Yêu cầu AI sinh Testcase & Checker");
        btnGenMock.addActionListener(e -> {
            generatorCodeArea.setText("// Đang gọi AI API sinh Generator...\n");
            checkerCodeArea.setText("// Đang gọi AI API sinh Checker...\n");
            new Thread(() -> {
                try {
                    Problem mockProblem = new Problem(1, "A+B", problemInputArea.getText(), 1000, 256, "Mock");
                    String genCode = aiService.generateGeneratorCode(mockProblem);
                    String checkCode = aiService.generateChecker(mockProblem);
                    SwingUtilities.invokeLater(() -> {
                        generatorCodeArea.setText(genCode);
                        checkerCodeArea.setText(checkCode);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> generatorCodeArea.setText("Lỗi kết nối AI API: " + ex.getMessage()));
                }
            }).start();
        });
        top.add(btnGenMock);

        panel.add(top, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTab3() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 10));
        
        sampleACArea = new JTextArea();
        sampleACArea.setBorder(BorderFactory.createTitledBorder("Mã Mẫu AC (Đúng chuẩn)"));
        
        sampleWAArea = new JTextArea();
        sampleWAArea.setBorder(BorderFactory.createTitledBorder("Mã Mẫu WA (Code sai cố ý để bẫy Testcase)"));

        JPanel pnl = new JPanel(new BorderLayout());
        JButton btnGen = new JButton("Yêu cầu AI tự động sinh Code mẫu Tốt / Xấu");
        btnGen.addActionListener(e -> {
            sampleACArea.setText("Đang gọi AI suy nghĩ code tối ưu nhất (AC)...");
            sampleWAArea.setText("Đang tìm lỗi sai logic để lừa bằng mã WA...");
            new Thread(() -> {
                try {
                    Problem mockProblem = new Problem(1, "Mock", problemInputArea.getText(), 1000, 256, "Mock");
                    String ac = aiService.generateSampleCode(mockProblem, "AC");
                    String wa = aiService.generateSampleCode(mockProblem, "WA");
                    SwingUtilities.invokeLater(() -> {
                        sampleACArea.setText(ac);
                        sampleWAArea.setText(wa);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> sampleACArea.setText("Chưa cấu hình API Key thật."));
                }
            }).start();
        });

        pnl.add(btnGen, BorderLayout.NORTH);
        panel.add(new JScrollPane(sampleACArea));
        panel.add(new JScrollPane(sampleWAArea));
        pnl.add(panel, BorderLayout.CENTER);

        return pnl;
    }

    private void analyzeProblemWithAI() {
        aiAnalysisLogArea.setText("Calling OpenAI/Gemini API...\nĐang phân tích cấu trúc đề bài...\n\n");
        btnAnalyzeAI.setEnabled(false);

        new Thread(() -> {
            try {
                // Call API Service
                Problem p = aiService.analyzeProblem(problemInputArea.getText(), selectedImageFile);
                
                SwingUtilities.invokeLater(() -> {
                    // Trong thực tế, p.getTitle(), p.getContent() sẽ được parse từ JSON
                    // Ở đây do AIService trả về string raw json, tạm gọi getString
                    aiAnalysisLogArea.append("Kết quả từ AI:\n" + p.getContent());
                    aiAnalysisLogArea.append("\n\n... Hoàn thành phân tích.");
                    btnAnalyzeAI.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    aiAnalysisLogArea.append("\nLỗi kết nối tới AI: " + e.getMessage());
                    btnAnalyzeAI.setEnabled(true);
                });
            }
        }).start();
    }
}