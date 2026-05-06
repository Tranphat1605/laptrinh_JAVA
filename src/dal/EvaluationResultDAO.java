package dal;


import entity.EvaluationResult;
import java.sql.*;

public class EvaluationResultDAO {

    public boolean addResult(EvaluationResult result) {
        String sql = "INSERT INTO EvaluationResult (submissionId, testcaseId, status, actualOutput, executionTimeMs) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, result.getSubmissionId());
            pstmt.setInt(2, result.getTestcaseId());
            pstmt.setString(3, result.getStatus());
            pstmt.setString(4, result.getActualOutput());
            pstmt.setLong(5, result.getExecutionTimeMs());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}