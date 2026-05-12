import gui.DashboardFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Áp dụng Look & Feel hệ thống để giao diện trông tự nhiên trên Windows/macOS/Linux
        // try {
        //     UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        // } catch (Exception e) {
        //     // Bỏ qua nếu không thiết lập được, dùng Look & Feel mặc định của Java
        // }

        // Khởi động giao diện chính trên Event Dispatch Thread (EDT) - đúng chuẩn Swing
        SwingUtilities.invokeLater(() -> {
            new DashboardFrame().setVisible(true);
        });
    }
}
