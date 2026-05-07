package dal;

import entity.Checker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CheckerDAO {

    public int addChecker(Checker checker) {
        String sql = "INSERT INTO Checker (problemId, code, language) VALUES (?, ?, ?)";
        int generatedId = -1;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, checker.getProblemId());
            pstmt.setString(2, checker.getCode());
            pstmt.setString(3, checker.getLanguage());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return generatedId;
    }

    public Checker getCheckerByProblemId(int problemId) {
        String sql = "SELECT TOP 1 * FROM Checker WHERE problemId = ? ORDER BY id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, problemId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Checker(
                            rs.getInt("id"),
                            rs.getInt("problemId"),
                            rs.getString("code"),
                            rs.getString("language")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<Checker> getAllCheckers() {
        List<Checker> list = new ArrayList<>();
        String sql = "SELECT * FROM Checker";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Checker checker = new Checker(
                        rs.getInt("id"),
                        rs.getInt("problemId"),
                        rs.getString("code"),
                        rs.getString("language")
                );
                list.add(checker);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }
}