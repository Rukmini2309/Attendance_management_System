import java.io.*;
import java.net.*;
import java.sql.*;
import java.time.LocalDate;

public class SServer {

    private static final int PORT = 2400;

    // =========================
    // AIVEN CONFIGURATION
    // =========================

    private static Connection db;

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            db = DatabaseConfig.getConnection();

            System.out.println("Database connection established.");

            CreateDatabase.ensureSchema(db);
            CreateDatabase.initializeToday(db);

            try (ServerSocket server = new ServerSocket(PORT)) {
                System.out.println("Server started on port " + PORT);

                while (true) {
                    Socket socket = server.accept();
                    System.out.println(
                            "Client connected: " + socket.getRemoteSocketAddress()
                    );
                    new ClientHandler(socket).start();
                }
            }

        } catch (Exception e) {
            System.out.println("Server startup failed:");
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {

        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        private String username;
        private String role;
        private Integer studentId;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                );
                out = new PrintWriter(socket.getOutputStream(), true);

                String line;

                while ((line = in.readLine()) != null) {
                    if ("LOGOUT".equals(line)) {
                        out.println("BYE");
                        break;
                    }

                    String response = handle(line);
                    out.println(response);
                }

            } catch (Exception e) {
                System.out.println(
                        "Client disconnected: " + e.getMessage()
                );
            } finally {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        private String handle(String line) throws Exception {
            String[] p = line.split("\\|", -1);

            if (p.length == 0) return "ERROR|Invalid command";

            return switch (p[0]) {
                case "LOGIN" ->
                        p.length >= 3
                                ? login(p[1], p[2])
                                : "ERROR|Invalid login request";

                case "ATTEND" ->
                        attendance(p.length > 1 ? p[1] : LocalDate.now().toString());

                case "MARK" ->
                        p.length >= 4
                                ? mark(p[1], Integer.parseInt(p[2]), p[3])
                                : "ERROR|Invalid attendance request";

                case "ADD_STUDENT" ->
                        p.length >= 4
                                ? addStudent(p[1], p[2], p[3])
                                : "ERROR|Invalid student request";

                case "STUDENT_DETAILS" ->
                        p.length >= 2
                                ? studentDetails(Integer.parseInt(p[1]))
                                : "ERROR|Invalid student";

                case "TEACHERS" ->
                        teachers();

                case "REQUEST" ->
                        p.length >= 4
                                ? requestPermission(p[1], p[2], p[3])
                                : "ERROR|Select a teacher and enter a reason";

                case "MY_PERMISSION" ->
                        myPermission();

                case "PERMISSIONS" ->
                        permissions();

                case "PENDING_REQUESTS" ->
                        pendingRequests();

                case "GRANT" ->
                        p.length >= 2
                                ? permissionAction(Integer.parseInt(p[1]), true)
                                : "ERROR|Invalid request";

                case "REJECT" ->
                        p.length >= 2
                                ? permissionAction(Integer.parseInt(p[1]), false)
                                : "ERROR|Invalid request";

                default ->
                        "ERROR|Unknown command";
            };
        }

        private String login(String u, String pass) throws SQLException {
            String sql = """
                SELECT username,role,student_id
                FROM users
                WHERE username=? AND password=?
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, u);
                ps.setString(2, pass);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "LOGIN_FAIL|Invalid username or password";
                    }

                    username = rs.getString("username");
                    role = rs.getString("role");

                    int sid = rs.getInt("student_id");
                    studentId = rs.wasNull() ? null : sid;

                    return "LOGIN_OK|" + role + "|" + username + "|"
                            + (studentId == null ? "" : studentId);
                }
            }
        }

        private String attendance(String requestedDate) throws SQLException {
            LocalDate date;

            try {
                date = LocalDate.parse(requestedDate);
            } catch (Exception e) {
                return "ERROR|Invalid date. Use YYYY-MM-DD.";
            }

            // If today's date is requested, ensure every student has
            // an automatic PRESENT record.
            if (date.equals(LocalDate.now())) {
                CreateDatabase.initializeToday(db);
            }

            StringBuilder b = new StringBuilder("ATTEND_OK|").append(date);

            String sql = """
                SELECT
                    s.id,
                    s.name,
                    COALESCE(a.status,'NOT_SET') AS status,
                    COALESCE(a.marked_by,'-') AS marked_by,
                    COALESCE((
                        SELECT ROUND(
                            100.0 * SUM(
                                CASE WHEN aa.status='PRESENT' THEN 1 ELSE 0 END
                            ) / NULLIF(COUNT(*),0), 1
                        )
                        FROM attendance aa
                        WHERE aa.student_id=s.id
                          AND aa.date <= CURDATE()
                          AND aa.status IN ('PRESENT','ABSENT')
                    ),0) AS percent
                FROM students s
                LEFT JOIN attendance a
                  ON a.student_id=s.id
                 AND a.date=?
                WHERE (? <> 'Student') OR s.id=?
                ORDER BY s.id
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(date));
                ps.setString(2, role);
                ps.setInt(3, studentId == null ? -1 : studentId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        b.append("|")
                                .append(rs.getInt("id")).append(",")
                                .append(clean(rs.getString("name"))).append(",")
                                .append(rs.getString("status")).append(",")
                                .append(clean(rs.getString("marked_by"))).append(",")
                                .append(rs.getString("percent")).append("%");
                    }
                }
            }

            return b.toString();
        }

        private String mark(String dateText, int sid, String status)
                throws SQLException {

            LocalDate date;

            try {
                date = LocalDate.parse(dateText);
            } catch (Exception e) {
                return "ERROR|Invalid date";
            }

            if ("Student".equalsIgnoreCase(role)) {
                return "ERROR|Students cannot edit attendance";
            }

            if ("CR".equalsIgnoreCase(role)) {
                if (!date.equals(LocalDate.now())) {
                    return "ERROR|CR can edit only today's attendance.";
                }

                if (!hasCrPermission()) {
                    return "ERROR|Teacher permission is required first.";
                }
            } else if (!"Teacher".equalsIgnoreCase(role)) {
                return "ERROR|Not authorized";
            }

            if (!status.equals("PRESENT") && !status.equals("ABSENT")) {
                return "ERROR|Status must be PRESENT or ABSENT";
            }

            String sql = """
                INSERT INTO attendance(student_id,date,status,marked_by)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    status=VALUES(status),
                    marked_by=VALUES(marked_by)
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setInt(1, sid);
                ps.setDate(2, Date.valueOf(date));
                ps.setString(3, status);
                ps.setString(4, username);
                ps.executeUpdate();
            }

            return "OK|Attendance updated successfully";
        }

        private String addStudent(
                String name, String studentUsername, String studentPassword)
                throws SQLException {

            if (!"Teacher".equalsIgnoreCase(role)) {
                return "ERROR|Only a teacher can add students";
            }

            name = cleanInput(name);
            studentUsername = cleanInput(studentUsername);
            studentPassword = cleanInput(studentPassword);

            if (name.isBlank() || studentUsername.isBlank()
                    || studentPassword.isBlank()) {
                return "ERROR|Name, username and password are required";
            }

            db.setAutoCommit(false);

            try {
                int sid;

                try (PreparedStatement ps = db.prepareStatement(
                        "SELECT id FROM students WHERE name=? LIMIT 1")) {
                    ps.setString(1, name);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            db.rollback();
                            return "ERROR|A student with this name already exists";
                        }
                    }
                }

                try (PreparedStatement ps = db.prepareStatement(
                        "SELECT id FROM users WHERE username=? LIMIT 1")) {
                    ps.setString(1, studentUsername);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            db.rollback();
                            return "ERROR|This username already exists";
                        }
                    }
                }

                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO students(name) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, name);
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            db.rollback();
                            return "ERROR|Could not create student";
                        }
                        sid = rs.getInt(1);
                    }
                }

                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO users(username,password,role,student_id) "
                                + "VALUES (?,?, 'Student',?)")) {

                    ps.setString(1, studentUsername);
                    ps.setString(2, studentPassword);
                    ps.setInt(3, sid);
                    ps.executeUpdate();
                }

                db.commit();

                // New students are also automatically PRESENT today.
                CreateDatabase.initializeToday(db);

                return "OK|Student added successfully";

            } catch (Exception e) {
                try {
                    db.rollback();
                } catch (Exception ignored) {
                }

                return "ERROR|Could not add student: " + clean(e.getMessage());

            } finally {
                db.setAutoCommit(true);
            }
        }

        private String studentDetails(int requestedStudentId)
                throws SQLException {

            if ("Student".equalsIgnoreCase(role)
                    && !Integer.valueOf(requestedStudentId).equals(studentId)) {
                return "ERROR|Students can view only their own attendance";
            }

            StringBuilder b = new StringBuilder("DETAILS_OK");

            String summarySql = """
                SELECT
                    s.id,
                    s.name,
                    COALESCE(SUM(CASE WHEN a.status='PRESENT' THEN 1 ELSE 0 END),0) present_count,
                    COALESCE(SUM(CASE WHEN a.status='ABSENT' THEN 1 ELSE 0 END),0) absent_count,
                    COALESCE(SUM(
                        CASE WHEN a.status IN ('PRESENT','ABSENT') THEN 1 ELSE 0 END
                    ),0) marked_count,
                    COALESCE(ROUND(
                        100.0 * SUM(CASE WHEN a.status='PRESENT' THEN 1 ELSE 0 END)
                        / NULLIF(SUM(
                            CASE WHEN a.status IN ('PRESENT','ABSENT') THEN 1 ELSE 0 END
                        ),0),1
                    ),0) percent
                FROM students s
                LEFT JOIN attendance a
                  ON a.student_id=s.id
                 AND a.date <= CURDATE()
                WHERE s.id=?
                GROUP BY s.id,s.name
            """;

            try (PreparedStatement ps = db.prepareStatement(summarySql)) {
                ps.setInt(1, requestedStudentId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "ERROR|Student not found";
                    }

                    b.append("|")
                            .append(rs.getInt("id")).append(",")
                            .append(clean(rs.getString("name"))).append(",")
                            .append(rs.getInt("present_count")).append(",")
                            .append(rs.getInt("absent_count")).append(",")
                            .append(rs.getInt("marked_count")).append(",")
                            .append(rs.getString("percent")).append("%");
                }
            }

            String historySql = """
                SELECT date,status,COALESCE(marked_by,'-')
                FROM attendance
                WHERE student_id=?
                  AND date <= CURDATE()
                  AND status IN ('PRESENT','ABSENT')
                ORDER BY date DESC
            """;

            try (PreparedStatement ps = db.prepareStatement(historySql)) {
                ps.setInt(1, requestedStudentId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        b.append("|H,")
                                .append(rs.getDate(1)).append(",")
                                .append(rs.getString(2)).append(",")
                                .append(clean(rs.getString(3)));
                    }
                }
            }

            return b.toString();
        }

        private String teachers() throws SQLException {
            if (!"CR".equalsIgnoreCase(role)) {
                return "ERROR|Only CR can select a teacher";
            }

            StringBuilder b = new StringBuilder("TEACHERS_OK");

            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT username FROM users WHERE role='Teacher' ORDER BY username");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    b.append("|").append(clean(rs.getString(1)));
                }
            }

            return b.toString();
        }

        private String requestPermission(
                String dateText, String teacher, String reason)
                throws SQLException {

            if (!"CR".equalsIgnoreCase(role)) {
                return "ERROR|Only CR can request edit permission";
            }

            LocalDate date;

            try {
                date = LocalDate.parse(dateText);
            } catch (Exception e) {
                return "ERROR|Invalid date";
            }

            if (!date.equals(LocalDate.now())) {
                return "ERROR|CR can request permission only for today.";
            }

            teacher = cleanInput(teacher);
            reason = cleanInput(reason);

            if (teacher.isBlank()) {
                return "ERROR|Select a teacher";
            }

            if (reason.isBlank()) {
                return "ERROR|Enter a reason";
            }

            String check = """
                SELECT username FROM users
                WHERE username=? AND role='Teacher'
            """;

            try (PreparedStatement ps = db.prepareStatement(check)) {
                ps.setString(1, teacher);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "ERROR|Selected teacher does not exist";
                    }
                }
            }

            // Do not allow duplicate active requests for the same CR/date/teacher.
            String duplicateCheck = """
                SELECT COUNT(*) FROM cr_attendance_permissions
                WHERE cr_username=? AND teacher_username=?
                  AND permission_date=?
                  AND status IN ('PENDING','GRANTED')
            """;
            try (PreparedStatement ps = db.prepareStatement(duplicateCheck)) {
                ps.setString(1, username);
                ps.setString(2, teacher);
                ps.setDate(3, Date.valueOf(date));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return "ERROR|You already have an active request or permission for this teacher today.";
                    }
                }
            }

            String sql = """
                INSERT INTO cr_attendance_permissions
                (cr_username,teacher_username,permission_date,reason,status)
                VALUES (?,?,?,?, 'PENDING')
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, teacher);
                ps.setDate(3, Date.valueOf(date));
                ps.setString(4, reason);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException e) {
                return "ERROR|You already have a request for this teacher today.";
            }

            return "OK|Edit request sent to " + teacher;
        }

        private String myPermission() throws SQLException {
            if (!"CR".equalsIgnoreCase(role)) {
                return "ERROR|Only CR can check permission";
            }

            StringBuilder b = new StringBuilder("MY_PERMISSION");

            String sql = """
                SELECT id,teacher_username,permission_date,reason,status,
                       requested_at,granted_by,granted_at
                FROM cr_attendance_permissions
                WHERE cr_username=?
                  AND permission_date=CURDATE()
                ORDER BY id DESC
                LIMIT 1
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "MY_PERMISSION|NONE";
                    }

                    b.append("|")
                            .append(rs.getInt("id")).append(",")
                            .append(clean(rs.getString("teacher_username"))).append(",")
                            .append(rs.getDate("permission_date")).append(",")
                            .append(clean(rs.getString("reason"))).append(",")
                            .append(rs.getString("status")).append(",")
                            .append(rs.getTimestamp("requested_at")).append(",")
                            .append(clean(rs.getString("granted_by"))).append(",")
                            .append(rs.getTimestamp("granted_at"));
                }
            }

            return b.toString();
        }

        private String permissions() throws SQLException {
            if (!"Teacher".equalsIgnoreCase(role)) {
                return "ERROR|Only teacher can view permission requests";
            }

            StringBuilder b = new StringBuilder("PERMISSIONS_OK");

            String sql = """
                SELECT id,cr_username,permission_date,reason,status,
                       requested_at
                FROM cr_attendance_permissions
                WHERE teacher_username=?
                  AND permission_date=CURDATE()
                ORDER BY requested_at DESC
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        b.append("|")
                                .append(rs.getInt("id")).append(",")
                                .append(clean(rs.getString("cr_username"))).append(",")
                                .append(rs.getDate("permission_date")).append(",")
                                .append(clean(rs.getString("reason"))).append(",")
                                .append(rs.getString("status")).append(",")
                                .append(rs.getTimestamp("requested_at"));
                    }
                }
            }

            return b.toString();
        }

        private String pendingRequests() throws SQLException {
            if (!"Teacher".equalsIgnoreCase(role)) {
                return "ERROR|Only teacher can view pending requests";
            }

            StringBuilder b = new StringBuilder("PENDING_OK");

            String sql = """
                SELECT id,cr_username,requested_at
                FROM cr_attendance_permissions
                WHERE teacher_username=?
                  AND permission_date=CURDATE()
                  AND status='PENDING'
                ORDER BY requested_at DESC
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        b.append("|")
                                .append(rs.getInt(1)).append(",")
                                .append(clean(rs.getString(2))).append(",")
                                .append(rs.getTimestamp(3));
                    }
                }
            }

            return b.toString();
        }

        private String permissionAction(int requestId, boolean grant)
                throws SQLException {

            if (!"Teacher".equalsIgnoreCase(role)) {
                return "ERROR|Only teacher can approve/reject requests";
            }

            String sql;

            if (grant) {
                sql = """
                    UPDATE cr_attendance_permissions
                    SET status='GRANTED',
                        granted_by=?,
                        granted_at=CURRENT_TIMESTAMP,
                        rejected_at=NULL
                    WHERE id=?
                      AND teacher_username=?
                      AND status='PENDING'
                """;
            } else {
                sql = """
                    UPDATE cr_attendance_permissions
                    SET status='REJECTED',
                        granted_by=?,
                        rejected_at=CURRENT_TIMESTAMP
                    WHERE id=?
                      AND teacher_username=?
                      AND status='PENDING'
                """;
            }

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, requestId);
                ps.setString(3, username);

                int n = ps.executeUpdate();

                if (n == 0) {
                    return "ERROR|Request not found or already processed";
                }
            }

            return grant
                    ? "OK|CR edit permission granted for today"
                    : "OK|CR request rejected";
        }

        private boolean hasCrPermission() throws SQLException {
            String sql = """
                SELECT COUNT(*)
                FROM cr_attendance_permissions
                WHERE cr_username=?
                  AND teacher_username IN (
                      SELECT username FROM users
                      WHERE username=?
                        AND role='Teacher'
                  )
                  AND permission_date=CURDATE()
                  AND status='GRANTED'
            """;

            // The logged-in CR is allowed if ANY teacher granted
            // the request for today's date.
            sql = """
                SELECT COUNT(*)
                FROM cr_attendance_permissions
                WHERE cr_username=?
                  AND permission_date=CURDATE()
                  AND status='GRANTED'
            """;

            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1) > 0;
                }
            }
        }

        private String cleanInput(String s) {
            if (s == null) return "";
            return s.replace("|", "/")
                    .replace(",", " ")
                    .replace("\n", " ")
                    .trim();
        }

        private String clean(String s) {
            if (s == null) return "-";
            return s.replace("|", "/")
                    .replace(",", " ")
                    .replace("\n", " ");
        }
    }
}
