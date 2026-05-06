package dal;

import entity.Submission;
import java.sql.*;

public class SubmissionDAO {

    // Trả về ID của Submission vừa được tạo (để dùng cho EvaluationResult)
    public int addSubmission(Submission sub) {
        String sql = "INSERT INTO Submission (problemId, sourceCode, language, finalStatus, isReferenceCode) VALUES (?, ?, ?, ?, ?)";
        int generatedId = -1;

        // Cần thêm Statement.RETURN_GENERATED_KEYS để lấy ID tự tăng
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, sub.getProblemId());
            pstmt.setString(2, sub.getSourceCode());
            pstmt.setString(3, sub.getLanguage());
            pstmt.setString(4, sub.getFinalStatus());
            pstmt.setBoolean(5, sub.isReferenceCode());

            pstmt.executeUpdate();

            // Lấy ID tự động tăng từ SQL Server
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                generatedId = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return generatedId;
    }
}
