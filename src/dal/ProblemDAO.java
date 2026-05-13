package dal;

import entity.Problem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProblemDAO {

    public boolean addProblem(Problem problem) {
        String sql = "INSERT INTO Problem (title, content, timeLimitMs, memoryLimitMb, source) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, problem.getTitle());
            
            // Ưu tiên lưu văn bản gốc chưa qua xử lý của AI vào CSDL, nếu không có mới dùng content của AI
            String contentToSave = (problem.getOriginalRawText() != null && !problem.getOriginalRawText().isBlank()) 
                                    ? problem.getOriginalRawText() 
                                    : problem.getContent();
            pstmt.setString(2, contentToSave);
            
            pstmt.setInt(3, problem.getTimeLimitMs());
            pstmt.setInt(4, problem.getMemoryLimitMb());
            pstmt.setString(5, problem.getSource());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        problem.setId(rs.getInt(1));
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Problem> getAllProblems() {
        List<Problem> list = new ArrayList<>();
        String sql = "SELECT * FROM Problem";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Problem p = new Problem(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getInt("timeLimitMs"),
                        rs.getInt("memoryLimitMb"),
                        rs.getString("source")
                );
                list.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}