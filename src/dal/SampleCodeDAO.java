package dal;

import entity.SampleCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SampleCodeDAO {

    public int addSampleCode(SampleCode sampleCode) {
        String sql = "INSERT INTO SampleCode (problemId, code, language, expectedVerdict) VALUES (?, ?, ?, ?)";
        int generatedId = -1;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, sampleCode.getProblemId());
            pstmt.setString(2, sampleCode.getCode());
            pstmt.setString(3, sampleCode.getLanguage());
            pstmt.setString(4, sampleCode.getExpectedVerdict());

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

    public List<SampleCode> getSampleCodesByProblemId(int problemId) {
        List<SampleCode> list = new ArrayList<>();
        String sql = "SELECT * FROM SampleCode WHERE problemId = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, problemId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SampleCode sampleCode = new SampleCode(
                            rs.getInt("id"),
                            rs.getInt("problemId"),
                            rs.getString("code"),
                            rs.getString("language"),
                            rs.getString("expectedVerdict")
                    );
                    list.add(sampleCode);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }
}