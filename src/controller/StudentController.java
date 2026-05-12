package controller;

import bll.EvaluationService;
import bll.SandboxService;
import dal.CheckerDAO;
import dal.ProblemDAO;
import dal.TestCaseDAO;
import entity.Checker;
import entity.ExecutionResult;
import entity.Problem;
import entity.TestCase;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Controller điều phối logic cho StudentFrame.
 * Trích xuất đề bài từ CSDL và xử lý nộp code.
 */
public class StudentController {

    public interface SubmitListener {
        void onStart();
        void onComplete(ExecutionResult result);
        void onError(String message);
    }

    private final SandboxService sandboxService;
    private final EvaluationService evaluationService;
    private final ProblemDAO problemDAO;
    private final TestCaseDAO testCaseDAO;
    private final CheckerDAO checkerDAO;
    private List<Problem> problems;

    public StudentController() {
        this.sandboxService = new SandboxService();
        this.evaluationService = new EvaluationService();
        this.problemDAO = new ProblemDAO();
        this.testCaseDAO = new TestCaseDAO();
        this.checkerDAO = new CheckerDAO();
    }

    /** Lấy danh sách đề bài từ CSDL. */
    public List<Problem> fetchProblems() {
        this.problems = problemDAO.getAllProblems();
        return this.problems;
    }

    /** Trả về mô tả đề bài theo index của list. */
    public String getDescription(int problemIndex) {
        if (problems != null && problemIndex >= 0 && problemIndex < problems.size()) {
            return problems.get(problemIndex).getContent();
        }
        return "Không có nội dung mô tả cho đề bài này.";
    }

    /** Nộp code và chấm trên sandbox bằng input mẫu lấy từ DB. Callback gọi trên EDT. */
    public void submitCode(String code, String lang, int problemIndex, SubmitListener listener) {
        SwingUtilities.invokeLater(listener::onStart);
        new Thread(() -> {
            try {
                if (problems != null && problemIndex >= 0 && problemIndex < problems.size()) {
                    Problem p = problems.get(problemIndex);
                    List<TestCase> tcs = testCaseDAO.getTestCasesByProblemId(p.getId());
                    Checker checker = checkerDAO.getCheckerByProblemId(p.getId());
                    
                    if (tcs == null || tcs.isEmpty()) {
                        SwingUtilities.invokeLater(() -> listener.onError("Đề bài không có testcase nào!"));
                        return;
                    }

                    // Chấm bài đàng hoàng quét qua toàn bộ testcases
                    ExecutionResult result = evaluationService.evaluateStudentSubmission(code, lang, tcs, checker, 2000L);
                    SwingUtilities.invokeLater(() -> listener.onComplete(result));
                } else {
                    SwingUtilities.invokeLater(() -> listener.onError("Lỗi: Không tìm thấy đề bài"));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }
}
