package footballai;

import java.io.*;
import java.sql.*;

public class CSVImporter {

    // המתודה הראשית שמפעילה את הייבוא המרוכז של כל הקבצים.
    public static void main(String[] args) {
        // הגדרת נתיב התיקייה שבה נמצאים כל קובצי הנתונים (CSV).
        String baseDir = "C:\\Users\\User\\Desktop\\Football Project\\Data\\";

        // פתיחת חיבור למסד הנתונים.
        try (Connection conn = DatabaseManager.connect()) {
            conn.setAutoCommit(true); // הגדרה שכל פקודה תיכנס מיד לבסיס הנתונים.
            System.out.println("Starting Batch Import...");

            // קריאות לפונקציית הייבוא הגנרית עבור כל טבלת סטטיסטיקה (8 עמודות לכל אחת).
            importGenericCSV(conn, baseDir + "player_accurate_long_balls.csv", "player_accurate_long_balls", 8);
            importGenericCSV(conn, baseDir + "player_accurate_passes.csv", "player_accurate_passes", 8);
            importGenericCSV(conn, baseDir + "player_big_chances_created.csv", "player_big_chances_created", 8);
            importGenericCSV(conn, baseDir + "player_big_chances_missed.csv", "player_big_chances_missed", 8);
            importGenericCSV(conn, baseDir + "player_clean_sheets.csv", "player_clean_sheets", 8);
            importGenericCSV(conn, baseDir + "player_contests_won.csv", "player_contests_won", 8);
            importGenericCSV(conn, baseDir + "player_effective_clearances.csv", "player_effective_clearances", 8);
            importGenericCSV(conn, baseDir + "player_expected_assists.csv", "player_expected_assists", 8);
            importGenericCSV(conn, baseDir + "player_expected_assists_per_90.csv", "player_expected_assists_per_90", 8);
            importGenericCSV(conn, baseDir + "player_expected_goals.csv", "player_expected_goals", 8);
            importGenericCSV(conn, baseDir + "player_expected_goals_on_target.csv", "player_expected_goals_on_target", 8);
            importGenericCSV(conn, baseDir + "player_expected_goals_per_90.csv", "player_expected_goals_per_90", 8);
            importGenericCSV(conn, baseDir + "player_fouls_committed.csv", "player_fouls_committed", 8);
            importGenericCSV(conn, baseDir + "player_goals_conceded.csv", "player_goals_conceded", 8);
            importGenericCSV(conn, baseDir + "player_goals_per_90.csv", "player_goals_per_90", 8);
            importGenericCSV(conn, baseDir + "player_interceptions.csv", "player_interceptions", 8);
            importGenericCSV(conn, baseDir + "player_on_target_scoring_attempts.csv", "player_on_target_scoring_attempts", 8);
            importGenericCSV(conn, baseDir + "player_outfielder_blocks.csv", "player_outfielder_blocks", 8);
            importGenericCSV(conn, baseDir + "player_penalties_conceded.csv", "player_penalties_conceded", 8);
            importGenericCSV(conn, baseDir + "player_penalties_won.csv", "player_penalties_won", 8);
            importGenericCSV(conn, baseDir + "player_player_ratings.csv", "player_player_ratings", 8);
            importGenericCSV(conn, baseDir + "player_possessions_won_attacking_third.csv", "player_possessions_won_attacking_third", 8);
            importGenericCSV(conn, baseDir + "player_red_cards.csv", "player_red_cards", 8);
            importGenericCSV(conn, baseDir + "player_saves_made.csv", "player_saves_made", 8);
            importGenericCSV(conn, baseDir + "player_tackles_won.csv", "player_tackles_won", 8);
            importGenericCSV(conn, baseDir + "player_top_assists.csv", "player_top_assists", 8);
            importGenericCSV(conn, baseDir + "player_top_scorers.csv", "player_top_scorers", 8);
            importGenericCSV(conn, baseDir + "player_total_assists_in_attack.csv", "player_total_assists_in_attack", 8);
            importGenericCSV(conn, baseDir + "player_total_scoring_attempts.csv", "player_total_scoring_attempts", 8);
            importGenericCSV(conn, baseDir + "player_yellow_cards.csv", "player_yellow_cards", 8);

            // ייבוא הטבלה המרכזית של השחקנים (5 עמודות) באמצעות פונקציה ייעודית.
            importGenericCSV1(conn, baseDir + "premier_player_23_24.csv", "premier_player_23_24", 5);

            System.out.println("All selected tables imported!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // פונקציה גנרית לייבוא קובצי CSV פשוטים לתוך טבלאות תואמות ב-MySQL.
    public static void importGenericCSV(Connection conn, String csvPath, String tableName, int columnCount) {
        // בניית שאילתת SQL דינמית בהתאם למספר העמודות (למשל: INSERT INTO table VALUES (?,?,?)).
        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
        for (int i = 0; i < columnCount; i++) {
            sql.append(i == columnCount - 1 ? "?" : "?,");
        }
        sql.append(")");

        // קריאת הקובץ (שימי לב: כאן מופיע נתיב לתמונה football_pitch.jpg במקום לקובץ ה-CSV, כדאי לתקן ל-csvPath).
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(csvPath), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            br.readLine(); // דילוג על שורת הכותרת (Header) של הקובץ.

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",", -1); // פיצול השורה לפי פסיקים.
                if (cols.length < columnCount) continue; // דילוג על שורות לא תקינות.

                // הזרקת כל עמודה מה-CSV לתוך סימן השאלה המתאים בשאילתה.
                for (int i = 0; i < columnCount; i++) {
                    ps.setString(i + 1, cols[i].trim());
                }
                ps.executeUpdate(); // הרצת הפקודה והכנסת הנתונים ל-DB.
            }
            System.out.println("Finished importing: " + tableName);
        } catch (Exception e) {
            System.err.println("Error in " + tableName + ": " + e.getMessage());
        }
    }

    // פונקציית ייבוא מתקדמת יותר הכוללת מיפוי עמודות וניקוי נתונים מורכבים.
    public static void importGenericCSV1(Connection conn, String csvPath, String tableName, int columnCount) {

        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
        for (int i = 0; i < columnCount; i++) {
            sql.append(i == columnCount - 1 ? "?" : "?,");
        }
        sql.append(")");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(csvPath), java.nio.charset.StandardCharsets.UTF_8))) {

            // קריאת שורת הכותרת.
            String headerLine = br.readLine();
            if (headerLine == null) return;

            // ניקוי סימני קידוד (BOM) ופיצול הכותרות.
            String[] headers = headerLine.replace("\uFEFF", "").split(",", -1);

            // מיפוי: יצירת קשר בין שם העמודה למיקום שלה (Index) בקובץ.
            java.util.Map<String, Integer> colIndex = new java.util.HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim(), i);
            }

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            String line;

            while ((line = br.readLine()) != null) {
                // שימוש ב-Regex כדי לפצל לפי פסיקים אך להתעלם מפסיקים שנמצאים בתוך גרשיים.
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                // פונקציית עזר לניקוי גרשיים ורווחים מיותרים.
                java.util.function.Function<String, String> clean = s -> s.replace("\"", "").trim();

                try {
                    // הזרקת הנתונים לפי שמות העמודות שמיפינו קודם.
                    ps.setString(1, clean.apply(cols[colIndex.get("Player")]));
                    ps.setString(2, clean.apply(cols[colIndex.get("Nation")]));
                    ps.setString(3, clean.apply(cols[colIndex.get("Pos")]));

                    // טיפול בעמודת הגיל: ניקוי תווים שאינם מספרים והפיכה ל-Integer.
                    String ageStr = clean.apply(cols[colIndex.get("Age")]).replaceAll("[^0-9]", "");
                    if (ageStr.isEmpty()) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(4, Integer.parseInt(ageStr));
                    }

                    ps.setString(5, clean.apply(cols[colIndex.get("Team")]));
                    ps.executeUpdate();
                } catch (Exception rowEx) {
                    System.err.println("Skipping row due to error: " + rowEx.getMessage());
                }
            }

            System.out.println("Finished importing: " + tableName);

        } catch (Exception e) {
            System.err.println("Error in " + tableName + ": " + e.getMessage());
        }
    }
}