package footballai;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    // הגדרת כתובת ה-DB עם תמיכה ב-UTF-8 כדי שהשמות ב-Java יופיעו נכון.
    private static final String URL = "jdbc:mysql://localhost:3306/football_ai?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
    // שם המשתמש למסד הנתונים.
    private static final String USER = "root";
    // הסיסמה למסד הנתונים.
    private static final String PASS = System.getenv().getOrDefault("DB_PASS", "");

    // פונקציה שיוצרת את החיבור הפיזי (Connection) מול שרת ה-MySQL.
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // מערך המכיל את כל 63 הטבלאות שבנינו ב-DB שמרכיבות את מרחב הנתונים.
    private static final String[] ALL_TABLES = {
            "accurate_cross_team", "accurate_long_balls_team_2", "accurate_pass_team", "big_chance_missed_team", "big_chance_team", "clean_sheet_team", "corner_taken_team", "effective_clearance_team", "expected_goals_conceded_team", "expected_goals_team", "fk_foul_lost_team", "goals_conceded_team_match", "interception_team", "ontarget_scoring_att_team", "penalty_conceded_team", "penalty_won_team", "player_accurate_long_balls", "player_accurate_passes", "player_big_chances_created", "player_big_chances_missed", "player_clean_sheets", "player_contests_won", "player_effective_clearances", "player_expected_assists", "player_expected_assists_per_90", "player_expected_goals", "player_expected_goals_on_target", "player_expected_goals_per_90", "player_fouls_committed", "player_goals_conceded", "player_goals_per_90", "player_interceptions", "player_of_the_match_awards", "player_on_target_scoring_attempts", "player_outfielder_blocks", "player_penalties_conceded", "player_penalties_won", "player_player_ratings", "player_possessions_won_attacking_third", "player_red_cards", "player_saves_made", "player_tackles_won", "player_top_assists", "player_top_scorers", "player_total_assists_in_attack", "player_total_scoring_attempts", "player_yellow_cards", "possession_percentage_team", "possession_won_att_3rd_team", "saves_team", "team_goals_per_match", "team_ratings", "total_red_card_team", "total_yel_card_team", "touches_in_opp_box_team", "won_tackle_team", "matches_23_24", "pl_table_2023_24", "pl_table_away_2023_24", "pl_table_home_2023_24", "pl_table_xg_2023_24"
    };

    // פונקציה שמחזירה רשימה של 11 שחקנים אופטימליים לאחר מיון וסינון.
    public static List<Player> getOptimalLineup(String teamName, String opponentTeam, MultiLayerNetwork model) {
        List<Player> lineup = new ArrayList<>(); // מגדירים רשימה ריקה שתכיל את הנבחרים.
        Set<String> selectedSlugs = new HashSet<>(); // קבוצה למניעת כפילויות של אותו שחקן בהרכב.
        try (Connection conn = connect()) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM premier_player_23_24 WHERE Team = ?"); // שאילתה לשליפת המועמדים מהקבוצה שנבחרה.
            pstmt.setString(1, teamName); // הזרקת שם הקבוצה לתוך השאילתה בצורה בטוחה.
            ResultSet rs = pstmt.executeQuery(); // הרצת השאילתה וקבלת התוצאות לתוך rs.

            List<PlayerCandidate> candidates = new ArrayList<>(); // רשימה זמנית לשמירת שחקנים וציוני ה-AI שלהם.
            while (rs.next()) {
                String slug = rs.getString("player_slug"); // שליפת המזהה הייחודי של השחקן.
                double aiScore = predictPlayerScore(conn, slug, opponentTeam); // קריאה לפונקצית חישוב הציון מול היריבה הספציפית.
                candidates.add(new PlayerCandidate(rs.getString("Player_name"), rs.getString("Nation"), rs.getString("Pos"), aiScore, slug)); // הוספה לרשימת המועמדים.
            }
            // מיון רשימת המועמדים מהציון הגבוה ביותר לנמוך ביותר.
            candidates.sort((p1, p2) -> Double.compare(p2.score, p1.score));

            int def=0, mid=0, fwd=0, gk=0; // משתני עזר לספירת שחקנים בכל עמדה לשמירה על מערך 4-3-3.
            for (PlayerCandidate pc : candidates) {
                if (selectedSlugs.contains(pc.slug)) continue; // אם השחקן כבר נבחר, דלג עליו.
                String pos = pc.pos.toUpperCase(); // בדיקת העמדה בפורמט אחיד.
                boolean added = false; // דגל לבדיקת הוספה מוצלחת.
                if (pos.contains("GK") && gk < 1) { gk++; added = true; } // בחירת שוער ראשון.
                else if ((pos.contains("DF") || pos.contains("B")) && def < 4) { def++; added = true; } // בחירת שחקני הגנה עד 4.
                else if ((pos.contains("MF") || pos.contains("M")) && mid < 3) { mid++; added = true; } // בחירת שחקני קישור עד 3.
                else if ((pos.contains("FW") || pos.contains("S") || pos.contains("W")) && fwd < 3) { fwd++; added = true; } // בחירת חלוצים עד 3.

                if (added) {
                    lineup.add(new Player(pc.name, pc.nation, pc.pos, pc.score)); // הוספת השחקן שנבחר לרשימה הסופית.
                    selectedSlugs.add(pc.slug); // סימון המזהה שלו כדי שלא ייבחר שוב.
                }
                if (lineup.size() == 11) break; // ברגע שהגענו ל-11 שחקנים, אפשר להפסיק.
            }
        } catch (Exception e) { e.printStackTrace(); }
        return lineup;
    }

    private static double predictPlayerScore(Connection conn, String slug, String opponent) {
        try {
            double baseRating = getBaseRating(conn, slug);
            String pos = getPlayerPos(conn, slug).toUpperCase();

            double pAtt = scanGroup(conn, slug, true, "goals", "scoring", "assists", "chance") / 50.0;
            double pDef = scanGroup(conn, slug, true, "tackle", "interception", "clearance", "block") / 50.0;
            double pMid = scanGroup(conn, slug, true, "pass", "possession", "touches", "cross") / 50.0;

            double oDefWeakRaw = scanGroup(conn, opponent, false, "conceded", "missed");
            double oAttStrRaw  = scanGroup(conn, opponent, false, "goals_team", "ontarget", "ratings");

            // Normalized opponent factor: how weak/strong the opponent is, roughly in a -1..1 range.
            // This itself is still the same number for every player on the team — that's expected.
            double oppFactor = (oDefWeakRaw - oAttStrRaw) / 20.0;

            double bonus;

            // KEY CHANGE: multiply the opponent factor BY the player's own relevant stat,
            // instead of just adding it. This means the opponent's effect is now different
            // for every player depending on how strong THEY are — which is what actually
            // changes the within-position ranking when the opponent changes.
            if (pos.contains("FW") || pos.contains("S") || pos.contains("W")) {
                bonus = (pAtt * 0.5) + (pAtt * oppFactor * 1.5);
            } else if (pos.contains("DF") || pos.contains("B")) {
                bonus = (pDef * 0.5) - (pDef * oppFactor * 1.5);
            } else {
                bonus = (pMid * 0.5) + (pMid * oppFactor * 1.5);
            }

            double finalBonus = Math.max(-2.5, Math.min(3.5, bonus));
            double finalScore = baseRating + finalBonus;

            System.out.println(String.format("AI LOGIC: [%s vs %s] | Base: %.2f | Bonus: %.2f | Final: %.2f",
                    slug, opponent, baseRating, finalBonus, finalScore));

            return finalScore;
        } catch (Exception e) { return 6.5; }
    }

    // פונקציית עזר לסריקת קבוצת טבלאות לפי מילות מפתח רלוונטיות.
    private static double scanGroup(Connection conn, String identifier, boolean isPlayer, String... keywords) {
        double sum = 0; // משתנה לצבירת הסכום הכולל.
        int count = 0;
        String col = isPlayer ? "player_slug" : "Team"; // קביעת עמודת הסינון (מזהה שחקן או שם קבוצה).
        for (String table : ALL_TABLES) {
            boolean match = false;
            for (String k : keywords) if (table.contains(k)) match = true; // בדיקה אם שם הטבלה מכיל את מילת המפתח.
            if (match) {
                sum += scanAllColumns(conn, table, col, identifier); // אם יש התאמה, סרוק את כל העמודות בטבלה וסכום אותן.
                count++;
            }
        }
        return count == 0 ? 0 : (sum / count); // מחזיר ממוצע בין הטבלאות למניעת הצטברות אינסופית שמנעלת את המערך.
    }

    // שליפת הדירוג של השחקן מהטבלה המתאימה.
    private static double getBaseRating(Connection conn, String slug) throws SQLException {
        // שימוש בחיפוש גמיש (LIKE) למניעת בעיות של שמות קטומים או אותיות מיוחדות שמחזירות 6.5 קבוע
        String sql = "SELECT FotMob_Rating FROM player_player_ratings WHERE player_slug LIKE ? OR player LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + slug + "%");
            ps.setString(2, "%" + slug.replace("-", " ") + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 6.5;
            }
        }
    }

    // שליפת העמדה הרשומה של השחקן מהטבלה המרכזית.
    private static String getPlayerPos(Connection conn, String slug) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT Pos FROM premier_player_23_24 WHERE player_slug='"+slug+"'")) {
            return rs.next() ? rs.getString(1) : "MF";
        }
    }

    // סריקה דינמית של כל העמודות הנומריות בטבלה נתונה (מנגנון Reflection).
    private static double scanAllColumns(Connection conn, String table, String col, String val) {
        double s = 0; // משתנה לצבירת הערכים מהעמודות.
        int numericFields = 0;
        String actualCol = col;

        // פתרון בעיית אותיות גדולות/קטנות ב-SQL: בודקים דינמית איך קוראים לעמודה בטבלה הנוכחית (למשל team באות קטנה או Club)
        try (ResultSet rsCol = conn.getMetaData().getColumns(null, null, table, null)) {
            while (rsCol.next()) {
                String colNameInDB = rsCol.getString("COLUMN_NAME");
                if (colNameInDB.equalsIgnoreCase(col) || (col.equalsIgnoreCase("Team") && colNameInDB.equalsIgnoreCase("Club"))) {
                    actualCol = colNameInDB; // מעדכן לשם המדויק שנמצא בבסיס הנתונים בפועל
                    break;
                }
            }
        } catch (Exception e) {}

        // הרצת השאילתה עם עמודת הסינון המדויקת שזיהינו דינמית
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE " + actualCol + " LIKE ?")) {
            ps.setString(1, "%" + val + "%"); // חיפוש השחקן או הקבוצה בטבלה.
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ResultSetMetaData m = rs.getMetaData(); // שליפת המטא-דאטה כדי להבין את מבנה העמודות.
                for (int i = 1; i <= m.getColumnCount(); i++) {
                    String colName = m.getColumnName(i).toLowerCase();

                    // סינון קריטי: מתעלמים לחלוטין מעמודות של זמן, דקות וגיל שמנפחות את הערכים ללא קשר לאיכות הביצוע
                    if (colName.contains("minutes") || colName.contains("matches") || colName.contains("age")) {
                        continue;
                    }

                    // בדיקה אם העמודה היא מספרית ואם היא לא עמודת דירוג/מיקום (Rank).
                    if (isNumeric(m.getColumnType(i)) && !colName.contains("rank")) {
                        s += rs.getDouble(i); // סכימה של הערך המספרי מהעמודה.
                        numericFields++;
                    }
                }
            }
        } catch (Exception e) { }
        return numericFields == 0 ? 0 : (s / numericFields); // מחזיר את ממוצע העמודות במקום סכום עיוור כדי למנוע הצפה של נתונים.
    }

    // פונקציית עזר לבדיקה אם סוג הנתון בעמודה הוא מספרי (Double, Float, Int וכו').
    private static boolean isNumeric(int t) {
        return t == Types.DOUBLE || t == Types.FLOAT || t == Types.INTEGER || t == Types.DECIMAL;
    }

    public static String getCoachForTeam(String teamName) {
        if (teamName == null || teamName.isEmpty()) return "COACH";

        try (Connection conn = connect()) {
            // Try an EXACT match first — avoids "United" colliding across clubs.
            String exactSql = "SELECT Manager FROM managers WHERE Club = ?";
            try (PreparedStatement ps = conn.prepareStatement(exactSql)) {
                ps.setString(1, teamName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String manager = rs.getString("Manager");
                        return (manager != null) ? manager.toUpperCase() : teamName.toUpperCase() + " COACH";
                    }
                }
            }

            // Fallback: fuzzy match only if no exact match was found.
            String fuzzySql = "SELECT Manager FROM managers WHERE Club LIKE ?";
            try (PreparedStatement ps = conn.prepareStatement(fuzzySql)) {
                ps.setString(1, "%" + teamName + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String manager = rs.getString("Manager");
                        return (manager != null) ? manager.toUpperCase() : teamName.toUpperCase() + " COACH";
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return teamName.toUpperCase() + " COACH";
    }

    // שליפת רשימה של כל הקבוצות הייחודיות ב-DB לצורך מילוי ה-ComboBox.
    public static List<String> getAllTeams() {
        List<String> teams = new ArrayList<>();
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT DISTINCT Team FROM premier_player_23_24 ORDER BY Team")) {
            while (rs.next()) teams.add(rs.getString("Team"));
        } catch (Exception e) {e.printStackTrace();}
        return teams;
    }

    // מחלקת עזר פנימית לשמירת נתוני שחקן מועמד והציון שלו לצרכי מיון בזיכרון.
    private static class PlayerCandidate {
        String name, nation, pos, slug;
        double score;
        PlayerCandidate(String n, String nat, String p, double s, String sl) {
            this.name=n; this.nation=nat; this.pos=p; this.score=s; this.slug=sl;
        }
    }
}