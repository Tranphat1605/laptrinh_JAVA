package dal;

import entity.Problem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProblemDAO {

    public boolean addProblem(Problem problem) {
        String sql = "INSERT INTO Problem (title, content, timeLimitMs, memoryLimitMb, source) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, problem.getTitle());
            pstmt.setString(2, problem.getContent());
            pstmt.setInt(3, problem.getTimeLimitMs());
            pstmt.setInt(4, problem.getMemoryLimitMb());
            pstmt.setString(5, problem.getSource());

            return pstmt.executeUpdate() > 0;
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

    public Problem getProblemById(int id) {
        String sql = "SELECT * FROM Problem WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Problem(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getInt("timeLimitMs"),
                            rs.getInt("memoryLimitMb"),
                            rs.getString("source")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}