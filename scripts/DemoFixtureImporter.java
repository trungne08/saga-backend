import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One-shot JDBC importer for docs/fixtures/contribution-demo.json.
 *
 * <p>Default mode is dry-run. Pass {@code --apply} only after a dry-run has
 * passed. It is deliberately provider-offline: every fixture Jira/Git row is
 * written as DISCONNECTED with no cloud identity, token, installation or
 * webhook. Placeholder Cognito subjects are never persisted.</p>
 */
public final class DemoFixtureImporter {
    private static final Path FIXTURE = Paths.get("docs/fixtures/contribution-demo.json");
    private static final LocalDateTime IMPORTED_AT = LocalDateTime.of(2026, 8, 17, 6, 0);
    private static final int BATCH_SIZE = 500;

    private final Connection connection;
    private final JsonNode fixture;
    private final Map<String, String> semesterIds = new HashMap<>();
    private final Map<String, String> subjectIds = new HashMap<>();
    private final Map<String, String> classIds = new HashMap<>();
    private final Map<String, String> studentIds = new HashMap<>();
    private final Map<String, String> courseIds = new HashMap<>();
    private final Map<String, String> teamIds = new HashMap<>();
    private final Map<String, String> projectIds = new HashMap<>();
    private final Map<String, String> projectTypeIds = new HashMap<>();
    private final List<String> lecturerIds = new ArrayList<>();

    private DemoFixtureImporter(Connection connection, JsonNode fixture) {
        this.connection = connection;
        this.fixture = fixture;
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        boolean verify = args.length == 1 && "--verify".equals(args[0]);
        if (args.length > 1 || (args.length == 1 && !apply && !verify)) {
            throw new IllegalArgumentException("Usage: DemoFixtureImporter [--apply|--verify]");
        }
        JsonNode fixture = new ObjectMapper().readTree(new String(Files.readAllBytes(FIXTURE), StandardCharsets.UTF_8));
        if (!"saga-contribution-fixture-v5".equals(text(fixture, "_schema"))) {
            throw new IllegalStateException("Unexpected fixture schema");
        }
        Properties env = loadEnv(Paths.get(".env"));
        String url = first(env, "DATABASE_JDBC_URL", "AIVEN_JDBC_URL");
        String username = first(env, "DATABASE_USERNAME", "AIVEN_DB_USERNAME");
        String password = first(env, "DATABASE_PASSWORD", "AIVEN_DB_PASSWORD");
        if (url == null || username == null || password == null) {
            throw new IllegalStateException("Database settings are missing from .env");
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        if (!url.contains("rewriteBatchedStatements=")) {
            url += (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
        }
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            DemoFixtureImporter importer = new DemoFixtureImporter(connection, fixture);
            if (verify) {
                importer.verify();
                return;
            }
            importer.preflight();
            System.out.printf("PRECHECK PASS: lecturers=%d, courses=%d, teams=%d, leaders=%d, projects=%d, tasks=%d, commits=%d%n",
                    importer.lecturerIds.size(), size(fixture, "courses"), size(fixture, "teams"), size(fixture, "teams"),
                    size(fixture, "projects"), size(fixture, "tasks"), size(fixture, "commits"));
            if (!apply) {
                System.out.println("DRY RUN ONLY: no database rows were written. Re-run with --apply to import.");
                return;
            }
            connection.setAutoCommit(false);
            try {
                importer.apply();
                connection.commit();
                System.out.println("IMPORT PASS: committed offline demo fixture.");
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private void preflight() throws SQLException {
        requireEmptyFixtureTargets();
        loadEligibleLecturers();
        if (lecturerIds.isEmpty()) {
            throw new IllegalStateException("No ACTIVE (or legacy null-status) Lecturer exists for Course assignment");
        }
        requireNoExistingStudentsOrCourses();
        loadOrPlanSemesters();
        loadOrPlanSubjects();
        loadOrPlanClasses();
        loadProjectTypes();
        validateFixtureReferences();
    }

    private void apply() throws SQLException {
        insertSemesters();
        insertSubjects();
        insertClasses();
        insertStudents();
        insertCourses();
        insertTeams();
        insertTeamMembers();
        insertProjects();
        linkTeamsToProjects();
        insertBoards();
        insertRepos();
        insertSprints();
        insertTasks();
        insertAttachments();
        insertCommits();
        insertPeerReviews();
        insertProjectGroupWeights();
    }

    private void verify() throws SQLException {
        System.out.printf("VERIFY counts: semesters=%d subjects=%d classes=%d students=%d courses=%d teams=%d projects=%d sprints=%d tasks=%d commits=%d attachments=%d peerReviews=%d groupWeights=%d%n",
                countIds("semester", fixture.path("semesters")), countIds("subject", canonicalSubjects()), countIds("`class`", fixture.path("classes")),
                countIds("student", fixture.path("students")), countIds("course", fixture.path("courses")), countIds("team", fixture.path("teams")),
                countIds("project", fixture.path("projects")), countIds("sprint", fixture.path("sprints")), countIds("task", fixture.path("tasks")),
                countIds("commit_data", fixture.path("commits")), countByFixtureForeignKey("task_attachment", "task_id", fixture.path("tasks")),
                countByFixtureForeignKey("peer_review", "sprint_id", fixture.path("sprints")), countByFixtureForeignKey("project_group_weight_config", "project_id", fixture.path("projects")));
        System.out.printf("VERIFY roles: leaders=%d members=%d; studentStatus: pending=%d; projectCreatedByNull=%d%n",
                scalar("select count(*) from team_member tm join team t on t.id=tm.team_id where t.id like '00000000-0000-0000-0000-a006%' and tm.role_in_team='LEADER'"),
                scalar("select count(*) from team_member tm join team t on t.id=tm.team_id where t.id like '00000000-0000-0000-0000-a006%' and tm.role_in_team='MEMBER'"),
                scalar("select count(*) from student where id like '00000000-0000-0000-0000-a009%' and account_status='PENDING'"),
                scalar("select count(*) from project where id like '00000000-0000-0000-0000-a007%' and created_by_cognito_sub is null"));
        System.out.printf("VERIFY providerOffline: boards=%d repos=%d; coursesWithLecturer=%d distinctLecturers=%d%n",
                scalar("select count(*) from jira_board where id like '00000000-0000-0000-0000-a008%' and connection_status='DISCONNECTED'"),
                scalar("select count(*) from git_repo where id like '00000000-0000-0000-0000-a013%' and connection_status='DISCONNECTED'"),
                scalar("select count(*) from course where id like '00000000-0000-0000-0000-a005%' and instructor_id is not null"),
                scalar("select count(distinct instructor_id) from course where id like '00000000-0000-0000-0000-a005%'"));
    }

    private int countIds(String table, JsonNode rows) throws SQLException { return countFixtureIds(table, rows); }
    private int countByFixtureForeignKey(String table, String column, JsonNode rows) throws SQLException {
        List<String> ids = new ArrayList<>(); for (JsonNode row : rows) ids.add(text(row, "id"));
        int total = 0;
        for (int start=0; start<ids.size(); start += BATCH_SIZE) {
            List<String> batch=ids.subList(start, Math.min(ids.size(), start+BATCH_SIZE));
            try (PreparedStatement ps=connection.prepareStatement("select count(*) from " + table + " where " + column + " in (" + placeholders(batch.size()) + ")")) {
                bindStrings(ps,batch); try(ResultSet rs=ps.executeQuery()){rs.next(); total += rs.getInt(1);}
            }
        }
        return total;
    }
    private int scalar(String sql) throws SQLException { try (PreparedStatement ps=connection.prepareStatement(sql); ResultSet rs=ps.executeQuery()) { rs.next(); return rs.getInt(1); } }

    private void requireEmptyFixtureTargets() throws SQLException {
        Map<String, JsonNode> tables = new LinkedHashMap<>();
        tables.put("team", fixture.path("teams"));
        tables.put("project", fixture.path("projects"));
        tables.put("jira_board", fixture.path("boards"));
        tables.put("git_repo", fixture.path("gitRepos"));
        tables.put("sprint", fixture.path("sprints"));
        tables.put("task", fixture.path("tasks"));
        tables.put("commit_data", fixture.path("commits"));
        for (Map.Entry<String, JsonNode> entry : tables.entrySet()) {
            int found = countFixtureIds(entry.getKey(), entry.getValue());
            if (found > 0) {
                throw new IllegalStateException("Fixture already appears imported or has an ID collision in " + entry.getKey() + " (" + found + " rows)");
            }
        }
    }

    private int countFixtureIds(String table, JsonNode rows) throws SQLException {
        int found = 0;
        List<String> ids = new ArrayList<>();
        for (JsonNode row : rows) ids.add(text(row, "id"));
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + BATCH_SIZE));
            String sql = "select count(*) from " + table + " where id in (" + placeholders(batch.size()) + ")";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bindStrings(ps, batch);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); found += rs.getInt(1); }
            }
        }
        return found;
    }

    private void loadEligibleLecturers() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "select id from lecturer where account_status is null or account_status = 'ACTIVE' order by lower(email), id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lecturerIds.add(rs.getString(1));
        }
        Collections.shuffle(lecturerIds, new Random(20260817L));
    }

    private void requireNoExistingStudentsOrCourses() throws SQLException {
        requireNoNaturalKey("student", "student_code", fixture.path("students"), "studentCode");
        requireNoNaturalKey("student", "email", fixture.path("students"), "email");
        requireNoNaturalKey("course", "course_code", fixture.path("courses"), "courseCode");
    }

    private void requireNoNaturalKey(String table, String column, JsonNode rows, String property) throws SQLException {
        List<String> values = new ArrayList<>();
        for (JsonNode row : rows) values.add(text(row, property));
        for (int start = 0; start < values.size(); start += BATCH_SIZE) {
            List<String> batch = values.subList(start, Math.min(values.size(), start + BATCH_SIZE));
            String expression = "email".equals(column) ? "lower(email)" : column;
            String sql = "select count(*) from " + table + " where " + expression + " in (" + placeholders(batch.size()) + ")";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) ps.setString(i + 1, "email".equals(column) ? batch.get(i).toLowerCase() : batch.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) throw new IllegalStateException("Existing " + table + " conflicts with fixture " + column);
                }
            }
        }
    }

    private void loadOrPlanSemesters() throws SQLException { loadOrPlan("semester", "code", fixture.path("semesters"), "code", semesterIds); }
    private void loadOrPlanSubjects() throws SQLException { loadOrPlan("subject", "subject_code", canonicalSubjects(), "subjectCode", subjectIds); }
    private void loadOrPlanClasses() throws SQLException { loadOrPlan("`class`", "class_code", fixture.path("classes"), "classCode", classIds); }

    private void loadOrPlan(String table, String column, JsonNode rows, String property, Map<String, String> ids) throws SQLException {
        for (JsonNode row : rows) {
            String key = text(row, property);
            String id = selectOne("select id from " + table + " where " + column + " = ? and deleted_at is null", key);
            ids.put(key, id == null ? text(row, "id") : id);
        }
    }

    private JsonNode canonicalSubjects() {
        Map<String, JsonNode> canonical = new HashMap<>();
        for (JsonNode row : fixture.path("subjects")) canonical.putIfAbsent(text(row, "subjectCode"), row);
        return new ObjectMapper().valueToTree(canonical.values());
    }

    private void loadProjectTypes() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select id, code from project_type"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) projectTypeIds.put(rs.getString(2), rs.getString(1));
        }
        for (JsonNode project : fixture.path("projects")) {
            String code = text(project, "projectTypeCode");
            if (!projectTypeIds.containsKey(code)) throw new IllegalStateException("Missing canonical ProjectType: " + code);
        }
    }

    private void validateFixtureReferences() {
        Set<String> studentFixtureIds = ids(fixture.path("students"));
        Set<String> courseFixtureIds = ids(fixture.path("courses"));
        for (JsonNode team : fixture.path("teams")) {
            if (!courseFixtureIds.contains(text(team, "courseId"))) throw new IllegalStateException("Team has missing Course");
            for (JsonNode member : team.path("memberIds")) if (!studentFixtureIds.contains(member.asText())) throw new IllegalStateException("Team has missing Student");
        }
    }

    private void insertSemesters() throws SQLException {
        for (JsonNode row : fixture.path("semesters")) {
            if (semesterIds.get(text(row, "code")).equals(text(row, "id"))) {
                update("insert into semester (id,created_at,updated_at,code,name,start_date,end_date,deleted_at) values (?,?,?,?,?,?,?,null)",
                        text(row,"id"), IMPORTED_AT, IMPORTED_AT, text(row,"code"), text(row,"name"), date(row,"start"), date(row,"end"));
            }
        }
    }

    private void insertSubjects() throws SQLException {
        Set<String> written = new HashSet<>();
        for (JsonNode row : fixture.path("subjects")) {
            String code = text(row, "subjectCode");
            if (written.add(code) && subjectIds.get(code).equals(text(row, "id"))) {
                update("insert into subject (id,created_at,updated_at,subject_code,name,deleted_at) values (?,?,?,?,?,null)", text(row,"id"), IMPORTED_AT, IMPORTED_AT, code, text(row,"name"));
            }
        }
    }

    private void insertClasses() throws SQLException {
        for (JsonNode row : fixture.path("classes")) if (classIds.get(text(row,"classCode")).equals(text(row,"id"))) {
            update("insert into `class` (id,created_at,updated_at,class_code,name,deleted_at) values (?,?,?,?,?,null)", text(row,"id"), IMPORTED_AT, IMPORTED_AT, text(row,"classCode"), text(row,"name"));
        }
    }

    private void insertStudents() throws SQLException {
        for (JsonNode row : fixture.path("students")) {
            String id = text(row,"id"); studentIds.put(id, id);
            update("insert into student (id,created_at,updated_at,version,cognito_sub,student_code,email,full_name,avatar_url,account_status,approved_by,approved_at) values (?,?,?,0,null,?,?,?,?,?,?,?)",
                    id, IMPORTED_AT, IMPORTED_AT, text(row,"studentCode"), text(row,"email"), text(row,"fullName"), null, "PENDING", null, null);
        }
    }

    private void insertCourses() throws SQLException {
        int index = 0;
        for (JsonNode row : fixture.path("courses")) {
            String id = text(row,"id"); courseIds.put(id, id);
            String subjectCode = subjectCodeForFixtureId(text(row,"subjectId"));
            String classCode = classCodeForFixtureId(text(row,"classId"));
            update("insert into course (id,created_at,updated_at,subject_id,class_id,semester_id,instructor_id,course_code,name,code_contribution_weight,test_contribution_weight,document_contribution_weight,research_contribution_weight,design_contribution_weight,contribution_config_mode,deleted_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,null)",
                    id, IMPORTED_AT, IMPORTED_AT, subjectIds.get(subjectCode), classIds.get(classCode), semesterIds.get(semesterCodeForFixtureId(text(row,"semesterId"))), lecturerIds.get(index++ % lecturerIds.size()), text(row,"courseCode"), text(row,"name"), 25d, 25d, 25d, 25d, 0d, text(row,"contributionConfigMode"));
        }
    }

    private void insertTeams() throws SQLException {
        for (JsonNode row : fixture.path("teams")) { teamIds.put(text(row,"id"), text(row,"id")); update("insert into team (id,created_at,updated_at,course_id,project_id,name) values (?,?,?,?,?,?)", text(row,"id"), IMPORTED_AT, IMPORTED_AT, courseIds.get(text(row,"courseId")), null, text(row,"name")); }
    }

    private void insertTeamMembers() throws SQLException {
        for (JsonNode team : fixture.path("teams")) {
            List<JsonNode> members = new ArrayList<>(); team.path("memberIds").forEach(members::add);
            int leaderIndex = new Random(UUID.fromString(text(team,"id")).getMostSignificantBits()).nextInt(members.size());
            for (int i = 0; i < members.size(); i++) update("insert into team_member (id,created_at,updated_at,team_id,student_id,role_in_team) values (?,?,?,?,?,?)", UUID.randomUUID().toString(), IMPORTED_AT, IMPORTED_AT, teamIds.get(text(team,"id")), studentIds.get(members.get(i).asText()), i == leaderIndex ? "LEADER" : "MEMBER");
        }
    }

    private void insertProjects() throws SQLException {
        for (JsonNode row : fixture.path("projects")) {
            String id = text(row,"id"); projectIds.put(id, id);
            String teamId = teamIds.get(text(row,"teamId"));
            String courseId = courseIds.get(teamCourseFixtureId(text(row,"teamId")));
            update("insert into project (id,created_at,updated_at,course_id,project_type_id,name,description,repository_url,jira_project_key,created_by_cognito_sub) values (?,?,?,?,?,?,?,?,?,null)", id, IMPORTED_AT, IMPORTED_AT, courseId, projectTypeIds.get(text(row,"projectTypeCode")), text(row,"name"), text(row,"description"), null, null);
            if (teamId == null) throw new IllegalStateException("Project has no Team");
        }
    }

    private void linkTeamsToProjects() throws SQLException {
        for (JsonNode row : fixture.path("projects")) update("update team set project_id = ? where id = ?", projectIds.get(text(row,"id")), teamIds.get(text(row,"teamId")));
    }

    private void insertBoards() throws SQLException {
        for (JsonNode row : fixture.path("boards")) update("insert into jira_board (id,created_at,updated_at,project_id,name,type,jira_board_id,cloud_id,site_url,jira_project_id,project_key,encrypted_access_token,encrypted_refresh_token,token_expires_at,granted_scopes,connection_status,connected_by_cognito_sub,connected_by_student_id,webhook_id,webhook_expires_at,webhook_secret_hash,sync_cursor,consecutive_failures,last_synced_at,version) values (?,?,?,?,?,?,null,null,null,null,?,null,null,null,null,?,null,null,null,null,null,null,0,null,0)", text(row,"id"), IMPORTED_AT, IMPORTED_AT, projectIds.get(text(row,"projectId")), text(row,"name"), text(row,"type"), text(row,"projectKey"), "DISCONNECTED");
    }

    private void insertRepos() throws SQLException {
        for (JsonNode row : fixture.path("gitRepos")) update("insert into git_repo (id,created_at,updated_at,project_id,name,url,provider,repository_id,owner_login,full_name,default_branch,installation_id,connection_status,sync_cursor,consecutive_failures,last_synced_at,review_cutover_at,version) values (?,?,?,?,?,?,?,?,?,?,?,null,?,null,0,null,null,0)", text(row,"id"), IMPORTED_AT, IMPORTED_AT, projectIds.get(text(row,"projectId")), text(row,"name"), text(row,"url"), text(row,"provider"), row.path("repositoryId").asLong(), text(row,"ownerLogin"), text(row,"fullName"), text(row,"defaultBranch"), "DISCONNECTED");
    }

    private void insertSprints() throws SQLException {
        for (JsonNode row : fixture.path("sprints")) update("insert into sprint (id,created_at,updated_at,board_id,name,external_sprint_id,start_date,end_date,goal,state,complete_date,deleted_at) values (?,?,?,?,?,?,?,?,?,?,?,null)", text(row,"id"), IMPORTED_AT, IMPORTED_AT, text(row,"boardId"), text(row,"name"), text(row,"id"), date(row,"startDate"), date(row,"endDate"), text(row,"goal"), text(row,"state"), date(row,"completeDate"));
    }

    private void insertTasks() throws SQLException {
        String sql = "insert into task (id,created_at,updated_at,project_id,sprint_id,assignee_id,reporter_id,assignee_external_id,reporter_external_id,blocks_task_id,external_key,external_id,title,type,status,priority,story_point,due_date,external_updated_at,resolved_at,resolution,deleted_at,description,labels_json,components_json) values (?,?,?,?,?,?,?,null,null,null,?,?,?,?,?,?,?,null,?,null,null,null,null,?,?)";
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode row : fixture.path("tasks")) rows.add(new Object[] {text(row,"id"), IMPORTED_AT, IMPORTED_AT, projectIds.get(text(row,"projectId")), text(row,"sprintId"), studentIds.get(text(row,"assigneeId")), studentIds.get(text(row,"reporterId")), text(row,"externalKey"), text(row,"id"), text(row,"title"), text(row,"type"), text(row,"status"), text(row,"priority"), row.path("storyPoint").asInt(), null, labelsJson(row.path("labels")), "[]"});
        batchUpdate(sql, rows);
    }

    private void insertAttachments() throws SQLException {
        String sql = "insert into task_attachment (id,task_id,external_id,filename,mime_type,size_bytes,author_external_id,created_at,updated_at) values (?,?,?,?,?,?,null,?,?)";
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode row : fixture.path("taskEvidence")) rows.add(new Object[] {deterministicId("attachment:" + text(row,"taskId") + ":" + text(row,"externalId")), text(row,"taskId"), text(row,"externalId"), text(row,"filename"), text(row,"mimeType"), row.path("sizeBytes").asLong(), IMPORTED_AT, IMPORTED_AT});
        batchUpdate(sql, rows);
    }

    private void insertCommits() throws SQLException {
        String sql = "insert into commit_data (id,created_at,updated_at,repo_id,task_id,git_issue_id,pr_id,author_id,sha_hash,github_commit_id,author_external_id,message,timestamp,additions,deletions,files_changed,external_updated_at) values (?,?,?,?,?,null,null,?,?,?,?,?,?,?,?,?,?)";
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode row : fixture.path("commits")) rows.add(new Object[] {text(row,"id"), IMPORTED_AT, IMPORTED_AT, text(row,"repoId"), text(row,"taskId"), studentIds.get(text(row,"authorId")), text(row,"shaHash"), text(row,"githubCommitId"), text(row,"authorExternalId"), text(row,"message"), date(row,"timestamp"), row.path("additions").asInt(), row.path("deletions").asInt(), row.path("filesChanged").asInt(), date(row,"timestamp")});
        batchUpdate(sql, rows);
    }

    private void insertPeerReviews() throws SQLException {
        String sql = "insert into peer_review (id,created_at,updated_at,sprint_id,reviewer_id,reviewee_id,star_rating,comment) values (?,?,?,?,?,?,?,?)";
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode row : fixture.path("peerReviews")) rows.add(new Object[] {deterministicId("peer:" + text(row,"sprintId") + ":" + text(row,"reviewerId") + ":" + text(row,"revieweeId")), IMPORTED_AT, IMPORTED_AT, text(row,"sprintId"), studentIds.get(text(row,"reviewerId")), studentIds.get(text(row,"revieweeId")), row.path("starRating").asInt(), text(row,"comment")});
        batchUpdate(sql, rows);
    }

    private void insertProjectGroupWeights() throws SQLException {
        for (JsonNode row : fixture.path("projectGroupWeights")) {
            JsonNode weights = row.path("weights");
            update("insert into project_group_weight_config (id,created_at,updated_at,project_id,team_id,code_weight,test_weight,document_weight,research_weight,design_weight,note,updated_by_profile_id) values (?,?,?,?,?,?,?,?,?,?,?,null)", deterministicId("weight:" + text(row,"projectId")), IMPORTED_AT, IMPORTED_AT, projectIds.get(text(row,"projectId")), teamIds.get(text(row,"teamId")), fraction(weights,"codeWeight"), fraction(weights,"testWeight"), fraction(weights,"documentWeight"), fraction(weights,"researchWeight"), BigDecimal.ZERO, "Imported fixture; no profile authority attributed");
        }
    }

    private String semesterCodeForFixtureId(String id) { return find(fixture.path("semesters"), "id", id, "code"); }
    private String subjectCodeForFixtureId(String id) { return find(fixture.path("subjects"), "id", id, "subjectCode"); }
    private String classCodeForFixtureId(String id) { return find(fixture.path("classes"), "id", id, "classCode"); }
    private String teamCourseFixtureId(String teamId) { return find(fixture.path("teams"), "id", teamId, "courseId"); }
    private String find(JsonNode rows, String key, String value, String result) { for (JsonNode row : rows) if (value.equals(text(row,key))) return text(row,result); throw new IllegalStateException("Missing fixture reference: " + value); }
    private String selectOne(String sql, String value) throws SQLException { try (PreparedStatement ps = connection.prepareStatement(sql)) { ps.setString(1,value); try(ResultSet rs=ps.executeQuery()){ return rs.next()?rs.getString(1):null; } } }
    private void update(String sql, Object... values) throws SQLException { try (PreparedStatement ps = connection.prepareStatement(sql)) { for (int i=0;i<values.length;i++) bind(ps,i+1,values[i]); ps.executeUpdate(); } }
    private void batchUpdate(String sql, List<Object[]> rows) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int pending = 0;
            for (Object[] values : rows) {
                for (int i = 0; i < values.length; i++) bind(ps, i + 1, values[i]);
                ps.addBatch();
                if (++pending == BATCH_SIZE) { ps.executeBatch(); pending = 0; }
            }
            if (pending > 0) ps.executeBatch();
        }
    }
    private static void bind(PreparedStatement ps, int index, Object value) throws SQLException { if (value == null) ps.setObject(index,null); else if (value instanceof LocalDateTime) ps.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value)); else ps.setObject(index,value); }
    private static String text(JsonNode node, String field) { JsonNode value=node.path(field); return value.isMissingNode() || value.isNull() ? null : value.asText(); }
    private static LocalDateTime date(JsonNode node, String field) { String value=text(node,field); return value == null ? null : LocalDateTime.parse(value); }
    private static BigDecimal fraction(JsonNode node, String field) { return node.path(field).decimalValue().movePointLeft(2); }
    private static String labelsJson(JsonNode labels) { return labels.isArray() ? labels.toString() : "[]"; }
    private static int size(JsonNode root, String field) { return root.path(field).size(); }
    private static Set<String> ids(JsonNode rows) { Set<String> ids=new HashSet<>(); for(JsonNode row:rows) ids.add(text(row,"id")); return ids; }
    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count,"?")); }
    private static void bindStrings(PreparedStatement ps, List<String> values) throws SQLException { for(int i=0;i<values.size();i++) ps.setString(i+1,values.get(i)); }
    private static String deterministicId(String source) { return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString(); }
    private static Properties loadEnv(Path path) throws Exception { Properties properties=new Properties(); try(InputStream input=Files.newInputStream(path)){properties.load(input);} return properties; }
    private static String first(Properties properties, String... keys) { for(String key:keys){String value=properties.getProperty(key); if(value!=null&&!value.trim().isEmpty())return value;} return null; }
}
