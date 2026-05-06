package dal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static Connection connection = null;

    // TODO: Thay đổi mật khẩu và port cho khớp với SQL Server trên máy bạn/máy Chiến
    private static final String SERVER_NAME = "localhost";
    private static final String PORT = "1433";
    private static final String DATABASE_NAME = "DCPNDB";
    private static final String USERNAME = "SA";
    private static final String PASSWORD = "MyPass@2024";

    private static final String URL = "jdbc:sqlserver://" + SERVER_NAME + ":" + PORT +
            ";databaseName=" + DATABASE_NAME +
            ";encrypt=true;trustServerCertificate=true;";

    private DBConnection() {}

    public static Connection getConnection() {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                System.out.println("Kết nối SQL Server thành công!");
            } catch (SQLException e) {
                System.out.println("Lỗi kết nối CSDL!");
                e.printStackTrace();
            }
        }
        return connection;
    }

    // Hàm main ĐÃ ĐƯỢC ĐƯA VÀO BÊN TRONG class
    public static void main(String[] args) {
        Connection conn = DBConnection.getConnection();
        if (conn != null) {
            System.out.println("Sẵn sàng! Hãy bắt tay vào code Judge logic thôi.");
        }
    }
}