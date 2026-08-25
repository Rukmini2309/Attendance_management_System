import java.sql.*;
import java.time.LocalDate;

public class CreateDatabase {

    // =========================
    // AIVEN CONFIGURATION
    // =========================


    // PUT YOUR CURRENT AIVEN PASSWORD HERE




    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection con = DatabaseConfig.getConnection()) {
                ensureSchema(con);
                seedDemoUsersAndStudents(con);
                seedDemoHistory(con);
                initializeToday(con);

                System.out.println("==============================================");
                System.out.println("DATABASE READY");
                System.out.println("==============================================");
                System.out.println("Teacher : teacher1 / teacher123");
                System.out.println("Teacher : teacher2 / teacher456");
                System.out.println("CR      : cr / cr123");
                System.out.println("Students: riya / riya123");
                System.out.println("          rahul / rahul123");
                System.out.println("          aman / aman123");
                System.out.println("          priya / priya123");
                System.out.println("          neha / neha123");
                System.out.println("==============================================");
            }
        } catch (Exception e) {
            System.out.println("Database setup failed:");
            e.printStackTrace();
        }
    }

    public static void ensureSchema(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS students (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(150) NOT NULL
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL
                )
            """);

            addColumnIfMissing(con, "users", "student_id",
                    "INT NULL UNIQUE");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS attendance (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    student_id INT NOT NULL,
                    date DATE NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    marked_by VARCHAR(100),
                    UNIQUE KEY unique_student_date (student_id, date)
                )
            """);

            addColumnIfMissing(con, "attendance", "marked_by",
                    "VARCHAR(100)");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cr_attendance_permissions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    cr_username VARCHAR(100) NOT NULL,
                    teacher_username VARCHAR(100) NOT NULL,
                    permission_date DATE NOT NULL,
                    reason VARCHAR(500),
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    granted_by VARCHAR(100),
                    granted_at TIMESTAMP NULL,
                    rejected_at TIMESTAMP NULL
                )
            """);

            addColumnIfMissing(con, "cr_attendance_permissions",
                    "teacher_username", "VARCHAR(100) NOT NULL DEFAULT ''");

            addColumnIfMissing(con, "cr_attendance_permissions",
                    "reason", "VARCHAR(500)");

            addColumnIfMissing(con, "cr_attendance_permissions",
                    "rejected_at", "TIMESTAMP NULL");

            // Helpful index. Ignore failure if an old database already has it.
            try {
                st.executeUpdate("""
                    CREATE INDEX idx_permission_teacher_date
                    ON cr_attendance_permissions(teacher_username, permission_date)
                """);
            } catch (SQLException ignored) {
            }
        }
    }

    private static void addColumnIfMissing(
            Connection con, String table, String column, String definition)
            throws SQLException {

        DatabaseMetaData md = con.getMetaData();

        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            if (rs.next()) return;
        }

        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN "
                    + column + " " + definition);
        }
    }

    private static void seedDemoUsersAndStudents(Connection con) throws SQLException {

        // If the project already has students, keep them.
        // Otherwise create a clean demo set.
        int count = 0;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            rs.next();
            count = rs.getInt(1);
        }

        if (count == 0) {
            String[] names = {
                    "Aarav Sharma", "Priya Singh", "Rahul Kumar",
                    "Ananya Gupta", "Aman Verma", "Riya Sharma",
                    "Neha Gupta", "Arjun Mehta", "Simran Kaur",
                    "Rohit Kumar", "Anjali Singh", "Vikas Sharma"
            };

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 "INSERT INTO students(name) VALUES (?)")) {
                for (String name : names) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
            }
        }

        // Teachers and CR.
        upsertUser(con, "teacher1", "teacher123", "Teacher", null);
        upsertUser(con, "teacher2", "teacher456", "Teacher", null);
        upsertUser(con, "cr", "cr123", "CR", null);

        // IMPORTANT:
        // Link student usernames to the correct student by NAME.
        // This also fixes old databases where student_id was NULL.
        linkStudentUser(con, "riya", "riya123", "Riya Sharma");
        linkStudentUser(con, "rahul", "rahul123", "Rahul Kumar");
        linkStudentUser(con, "aman", "aman123", "Aman Verma");
        linkStudentUser(con, "priya", "priya123", "Priya Singh");
        linkStudentUser(con, "neha", "neha123", "Neha Gupta");
        linkStudentUser(con, "arjun", "arjun123", "Arjun Mehta");
        linkStudentUser(con, "simran", "simran123", "Simran Kaur");
        linkStudentUser(con, "rohit", "rohit123", "Rohit Kumar");
        linkStudentUser(con, "student9", "student123", "Anjali Singh");
        linkStudentUser(con, "student10", "student123", "Vikas Sharma");
    }

    private static void linkStudentUser(
            Connection con,
            String username,
            String password,
            String studentName) throws SQLException {

        Integer studentId = null;

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM students WHERE name=? LIMIT 1")) {

            ps.setString(1, studentName);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    studentId = rs.getInt(1);
                }
            }
        }

        if (studentId == null) return;

        upsertUser(
                con,
                username,
                password,
                "Student",
                studentId
        );
    }

    private static void upsertUser(
            Connection con, String username, String password,
            String role, Integer studentId) throws SQLException {

        String sql = """
            INSERT INTO users(username,password,role,student_id)
            VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE
              password=VALUES(password),
              role=VALUES(role),
              student_id=VALUES(student_id)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);

            if (studentId == null) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, studentId);

            ps.executeUpdate();
        }
    }

    public static void initializeToday(Connection con) throws SQLException {
        String sql = """
            INSERT INTO attendance(student_id,date,status,marked_by)
            SELECT s.id, CURDATE(), 'PRESENT', 'SYSTEM'
            FROM students s
            WHERE NOT EXISTS (
                SELECT 1
                FROM attendance a
                WHERE a.student_id=s.id
                  AND a.date=CURDATE()
            )
        """;

        try (Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void seedDemoHistory(Connection con) throws SQLException {
        // Add seven previous days only when a record is missing.
        // Existing real attendance is never overwritten.
        LocalDate today = LocalDate.now();

        String[] pattern = {
                "PRESENT", "PRESENT", "ABSENT", "PRESENT",
                "PRESENT", "ABSENT", "PRESENT", "PRESENT",
                "ABSENT", "PRESENT", "PRESENT", "PRESENT"
        };

        String sql = """
            INSERT INTO attendance(student_id,date,status,marked_by)
            VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE
              status=status
        """;

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM students ORDER BY id");
             PreparedStatement ps = con.prepareStatement(sql)) {

            java.util.List<Integer> ids = new java.util.ArrayList<>();
            while (rs.next()) ids.add(rs.getInt(1));

            for (int daysAgo = 7; daysAgo >= 1; daysAgo--) {
                LocalDate date = today.minusDays(daysAgo);

                for (int i = 0; i < ids.size(); i++) {
                    ps.setInt(1, ids.get(i));
                    ps.setDate(2, Date.valueOf(date));
                    ps.setString(3, pattern[i % pattern.length]);
                    ps.setString(4, "SYSTEM");
                    ps.executeUpdate();
                }
            }
        }
    }
}
