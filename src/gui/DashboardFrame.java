package gui;

import java.awt.*;
import javax.swing.*;

public class DashboardFrame extends JFrame {

    public DashboardFrame() {
        setTitle("Dashboard - Hệ Thống Lập Trình & Đánh Giá Code");
        setSize(500, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JLabel titleLabel = new JLabel("LỰA CHỌN CHỨC NĂNG HỆ THỐNG", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        add(titleLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 15, 15));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 40, 50));

        JButton btnStudent = new JButton("1. Học Sinh: Đọc Đề & Chấm Code Sandbox");
        btnStudent.setFont(new Font("Arial", Font.PLAIN, 16));
        btnStudent.addActionListener(e -> new StudentFrame().setVisible(true));

        JButton btnTeacher = new JButton("2. Giáo Viên/Admin: AI Tự Sinh Đề & Đánh Giá Testcase");
        btnTeacher.setFont(new Font("Arial", Font.PLAIN, 16));
        btnTeacher.addActionListener(e -> new TeacherFrame().setVisible(true));

        btnPanel.add(btnStudent);
        btnPanel.add(btnTeacher);

        add(btnPanel, BorderLayout.CENTER);
    }

    // public static void main(String[] args) {
    //     SwingUtilities.invokeLater(() -> {
    //         new DashboardFrame().setVisible(true);
    //     });
    // }
}