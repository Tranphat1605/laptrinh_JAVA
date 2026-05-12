package gui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class DashboardFrame extends JFrame {

    public DashboardFrame() {
        // Thiết lập phong cách giao diện hệ thống (Flat UI style)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        setTitle("Dashboard - Hệ Thống Đánh Giá Code");
        setSize(700, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(245, 247, 250));

        // Tiêu đề
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(41, 128, 185));
        JLabel titleLabel = new JLabel("HỆ THỐNG LẬP TRÌNH & ĐÁNH GIÁ TỰ ĐỘNG", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(new EmptyBorder(25, 10, 25, 10));
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Panel chứa các nút chức năng
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // Nút học sinh
        JButton btnStudent = createDashboardButton(
                "Dành cho Học Sinh",
                "Đọc đề bài, viết code và nộp bài với môi trường Sandbox an toàn.",
                "👨‍💻"
        );
        btnStudent.addActionListener(e -> new StudentFrame().setVisible(true));

        // Nút giáo viên
        JButton btnTeacher = createDashboardButton(
                "Giáo Viên / Admin",
                "Tạo đề bài bằng AI, quản lý Testcase và đánh giá chất lượng.",
                "🏫"
        );
        btnTeacher.addActionListener(e -> new TeacherFrame().setVisible(true));

        btnPanel.add(btnStudent);
        btnPanel.add(btnTeacher);

        add(btnPanel, BorderLayout.CENTER);
        
        // Footer
        JLabel footerLabel = new JLabel("Phiên bản 1.0 - Phát triển bởi DCPN", SwingConstants.CENTER);
        footerLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        footerLabel.setForeground(Color.GRAY);
        footerLabel.setBorder(new EmptyBorder(10, 10, 20, 10));
        add(footerLabel, BorderLayout.SOUTH);
    }

    private JButton createDashboardButton(String title, String desc, String iconStr) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(new Color(220, 225, 230));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(235, 240, 245));
                } else {
                    g2.setColor(Color.WHITE);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setLayout(new BorderLayout());
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Thêm Icon (Emoji cho nhanh)
        JLabel icon = new JLabel(iconStr, SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        icon.setBorder(new EmptyBorder(20, 0, 10, 0));
        
        // Thêm Tiêu đề
        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(44, 62, 80));
        
        // Thêm mô tả
        JTextArea lblDesc = new JTextArea(desc);
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDesc.setForeground(Color.DARK_GRAY);
        lblDesc.setWrapStyleWord(true);
        lblDesc.setLineWrap(true);
        lblDesc.setOpaque(false);
        lblDesc.setEditable(false);
        lblDesc.setFocusable(false);
        lblDesc.setBorder(new EmptyBorder(10, 20, 20, 20));
        
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setOpaque(false);
        textPanel.add(lblTitle, BorderLayout.NORTH);
        textPanel.add(lblDesc, BorderLayout.CENTER);

        btn.add(icon, BorderLayout.NORTH);
        btn.add(textPanel, BorderLayout.CENTER);
        
        // Thêm viền bo góc khi vẽ
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
            new EmptyBorder(10, 10, 10, 10)
        ));

        return btn;
    }
}