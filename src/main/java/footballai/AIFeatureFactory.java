package footballai;

import java.sql.*;
import java.io.FileWriter;

public class AIFeatureFactory {

    // מערך סטטי המכיל את שמות כל 63 הטבלאות בבסיס הנתונים שמהן נאסוף מידע.
    private static final String[] ALL_TABLES = {
            "accurate_cross_team", "accurate_long_balls_team_2", "accurate_pass_team", "big_chance_missed_team",
            "big_chance_team", "clean_sheet_team", "corner_taken_team", "effective_clearance_team",
            "expected_goals_conceded_team", "expected_goals_team", "fk_foul_lost_team", "goals_conceded_team_match",
            "interception_team", "ontarget_scoring_att_team", "penalty_conceded_team", "penalty_won_team",
            "player_accurate_long_balls", "player_accurate_passes", "player_big_chances_created",
            "player_big_chances_missed", "player_clean_sheets", "player_contests_won", "player_effective_clearances",
            "player_expected_assists", "player_expected_assists_per_90", "player_expected_goals",
            "player_expected_goals_on_target", "player_expected_goals_per_90", "player_fouls_committed",
            "player_goals_conceded", "player_goals_per_90", "player_interceptions", "player_of_the_match_awards",
            "player_on_target_scoring_attempts", "player_outfielder_blocks", "player_penalties_conceded",
            "player_penalties_won", "player_player_ratings", "player_possessions_won_attacking_third",
            "player_red_cards", "player_saves_made", "player_tackles_won", "player_top_assists",
            "player_top_scorers", "player_total_assists_in_attack", "player_total_scoring_attempts",
            "player_yellow_cards", "possession_percentage_team", "possession_won_att_3rd_team", "saves_team",
            "team_goals_per_match", "team_ratings", "total_red_card_team", "total_yel_card_team",
            "touches_in_opp_box_team", "won_tackle_team"
    };

    // מתודה ראשית לייצור קובץ נתוני האימון בפורמט CSV.
    public static void generateTrainingData() {
        System.out.println("Building feature vectors from " + ALL_TABLES.length + " tables...");
        // פתיחת חיבור למסד הנתונים ויצירת קובץ CSV חדש; ה-try מבטיח סגירה אוטומטית שלהם.
        try (Connection conn = DatabaseManager.connect();
             FileWriter writer = new FileWriter("training_data.csv")) {

            // Header - כתיבת שורת הכותרות של הקובץ (שמות העמודות).
            writer.append("player_slug,team,position,");
            // לולאה שמוסיפה לכותרת את השם של כל אחת מ-63 הטבלאות כעמודת נתונים.
            for (String table : ALL_TABLES) writer.append(table).append(",");
            // הוספת עמודת ה"מטרה" - הציון האמיתי שהשחקן קיבל, אותו ה-AI ינסה לנחש.
            writer.append("target_rating\n");

            // שאילתה לשליפת רשימת כל השחקנים, הקבוצות והעמדות מהטבלה הראשית.
            String query = "SELECT player_slug, Team, Pos FROM premier_player_23_24";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            // לולאה שעוברת שורה-שורה על כל השחקנים שחזרו מהשאילתה.
            while (rs.next()) {
                String slug = rs.getString("player_slug"); // שמירת מזהה השחקן.
                String team = rs.getString("Team");       // שמירת שם הקבוצה.
                // כתיבת פרטי השחקן הבסיסיים לתוך השורה הנוכחית בקובץ ה-CSV.
                writer.append(slug).append(",").append(team).append(",").append(rs.getString("Pos")).append(",");

                // לולאה פנימית שעוברת על כל 63 הטבלאות ושואבת נתון מספרי עבור השחקן הנוכחי.
                for (String table : ALL_TABLES) {
                    double val = getSmartStat(conn, table, slug, team); // שליפת הנתון בצורה חכמה.
                    writer.append(String.valueOf(val)).append(",");     // כתיבת הנתון לקובץ.
                }

                // שליפת הציון (Rating) האמיתי של השחקן מטבלת הרייטינג כדי שישמש כ"תשובה" ל-AI.
                double rating = getSmartStat(conn, "player_player_ratings", slug, team);
                writer.append(String.valueOf(rating)).append("\n"); // סיום השורה וירידה לשורה חדשה.
            }
            System.out.println("SUCCESS: training_data.csv generated.");
        } catch (Exception e) {
            // הדפסת שגיאה במקרה של תקלה בתהליך הכנת הנתונים.
            System.err.println("Preparation Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // מתודה עזר לשליפת נתון מטבלה, המנסה לחפש לפי שחקן ואם לא מוצאת - לפי קבוצה.
    private static double getSmartStat(Connection conn, String table, String slug, String teamName) {
        // ניסיון ראשון: חיפוש הנתון לפי המזהה האישי של השחקן (player_slug).
        String sql = "SELECT * FROM " + table + " WHERE player_slug = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, slug);
            try (ResultSet rs = pstmt.executeQuery()) {
                // אם נמצאה שורה לשחקן, נשלוף ממנה את הערך המספרי.
                if (rs.next()) return extractNumericFromRow(rs);
            }
        } catch (SQLException e) {
            // ניסיון שני (Fallback): אם אין עמודת שחקן, נחפש נתון כללי לפי שם הקבוצה.
            String teamSql = "SELECT * FROM " + table + " WHERE Team LIKE ? OR Club LIKE ?";
            try (PreparedStatement pstmt = conn.prepareStatement(teamSql)) {
                pstmt.setString(1, "%" + teamName + "%");
                pstmt.setString(2, "%" + teamName + "%");
                try (ResultSet rs = pstmt.executeQuery()) {
                    // אם נמצאה שורה לקבוצה, נשלוף ממנה את הערך המספרי.
                    if (rs.next()) return extractNumericFromRow(rs);
                }
            } catch (SQLException e2) { /* התעלמות משגיאות מבנה בטבלאות ספציפיות */ }
        }
        return 0.0; // אם לא נמצא נתון, נחזיר 0.0 (Zero Padding).
    }

    // מתודה הסורקת את השורה שנמצאה ושולפת ממנה את הערך המספרי הראשון שאינו טקסט או דירוג.
    private static double extractNumericFromRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData(); // שליפת המטא-דאטה כדי להבין את סוגי העמודות.
        // מעבר על כל העמודות בשורה שנמצאה.
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String colName = meta.getColumnName(i).toLowerCase();
            // סינון עמודות טקסטואליות או עמודות של "דירוג" שאינן רלוונטיות כפיצ'ר מספרי נקי.
            if (colName.contains("slug") || colName.contains("name") || colName.contains("team") ||
                    colName.contains("club") || colName.contains("nation") || colName.contains("pos") ||
                    colName.contains("rank") || colName.contains("country")) continue;

            int type = meta.getColumnType(i); // בדיקת סוג הנתון בעמודה (מספר, טקסט וכו').
            // אם סוג העמודה הוא מספרי (שלם, עשרוני וכו'), נחזיר את הערך שלה.
            if (type == Types.DOUBLE || type == Types.FLOAT || type == Types.INTEGER ||
                    type == Types.REAL || type == Types.DECIMAL || type == Types.TINYINT) {
                return rs.getDouble(i);
            }
        }
        return 0.0; // אם לא נמצאה עמודה מספרית מתאימה.
    }
}