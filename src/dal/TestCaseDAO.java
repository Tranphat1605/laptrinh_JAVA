package dal;


import entity.TestCase;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TestCaseDAO {

    public boolean addTestCase(TestCase tc) {
        String sql = "INSERT INTO TestCase (problemId, inputData, expectedOutput, isHidden, strengthStatus) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, tc.getProblemId());
            pstmt.setString(2, tc.getInputData());
            pstmt.setString(3, tc.getExpectedOutput());
            pstmt.setBoolean(4, tc.isHidden()); // JDBC tự map boolean sang kiểu BIT của SQL Server
            pstmt.setString(5, tc.getStrengthStatus());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateTestCaseStrength(int testCaseId, String newStrength) {
        String sql = "UPDATE TestCase SET strengthStatus = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newStrength);
            pstmt.setInt(2, testCaseId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<TestCase> getTestCasesByProblemId(int problemId) {
        List<TestCase> list = new ArrayList<>();
        String sql = "SELECT * FROM TestCase WHERE problemId = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, problemId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                TestCase tc = new TestCase(
                        rs.getInt("id"),
                        rs.getInt("problemId"),
                        rs.getString("inputData"),
                        rs.getString("expectedOutput"),
                        rs.getBoolean("isHidden"),
                        rs.getString("strengthStatus")
                );
                list.add(tc);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean deleteTestCasesByProblemId(int problemId) {
        String sql = "DELETE FROM TestCase WHERE problemId = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, problemId);
            return pstmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}