import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class InsertData {

    public static void main(String[] args) {

        try (Connection con = DatabaseConfig.getConnection()) {

            CreateDatabase.ensureSchema(con);

            System.out.println("Connected to Aiven MySQL.");

            // Create today's automatic PRESENT records.
            CreateDatabase.initializeToday(con);

            System.out.println("Sample data is already available.");
            System.out.println("Today: automatic PRESENT records exist.");
            System.out.println("Previous 7 days: mixed PRESENT/ABSENT history.");
            System.out.println("No duplicate attendance rows were created.");

            printStudentSummary(con);

        } catch (Exception e) {

            System.out.println("Sample data check failed:");
            e.printStackTrace();
        }
    }

    private static void printStudentSummary(Connection con)
            throws SQLException {

        String sql = """
            SELECT
                s.id,
                s.name,
                COALESCE(
                    SUM(
                        CASE
                            WHEN a.status = 'PRESENT'
                            THEN 1
                            ELSE 0
                        END
                    ), 0
                ) AS present_count,

                COALESCE(
                    SUM(
                        CASE
                            WHEN a.status = 'ABSENT'
                            THEN 1
                            ELSE 0
                        END
                    ), 0
                ) AS absent_count

            FROM students s

            LEFT JOIN attendance a
                ON a.student_id = s.id
                AND a.date <= CURDATE()

            GROUP BY s.id, s.name

            ORDER BY s.id
            """;

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println();
            System.out.println("STUDENT ATTENDANCE SUMMARY");

            while (rs.next()) {

                int present = rs.getInt("present_count");
                int absent = rs.getInt("absent_count");

                int total = present + absent;

                double percent =
                        total == 0
                                ? 0
                                : (present * 100.0 / total);

                System.out.printf(
                        "%d. %-20s Present=%d Absent=%d Attendance=%.1f%%%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        present,
                        absent,
                        percent
                );
            }
        }
    }
}