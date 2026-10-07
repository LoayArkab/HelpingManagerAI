package footballai;

import com.github.slugify.Slugify;
import java.sql.*;

public class DataCleaner {
    private static final Slugify slg = Slugify.builder().build();

    public static void cleanAllTables() {
        // Documentation in English: Complete list of all player-related tables found in your database
        String[] tables = {
                "premier_player_23_24", "player_tackles_won", "player_top_assists",
                "player_expected_goals", "player_fouls_committed", "player_total_scoring_attempts",
                "player_top_scorers", "player_yellow_cards", "player_red_cards", "player_saves_made",
                "player_accurate_passes", "player_accurate_long_balls", "player_big_chances_created",
                "player_big_chances_missed", "player_interceptions", "player_effective_clearances",
                "player_clean_sheets", "player_contests_won", "player_expected_assists",
                "player_expected_assists_per_90", "player_expected_goals_on_target",
                "player_expected_goals_per_90", "player_goals_conceded", "player_goals_per_90",
                "player_of_the_match_awards", "player_on_target_scoring_attempts",
                "player_outfielder_blocks", "player_penalties_conceded", "player_penalties_won",
                "player_player_ratings", "player_possessions_won_attacking_third", "managers"
        };

        try (Connection conn = DatabaseManager.connect()) {
            for (String tableName : tables) {
                System.out.println("Processing table: " + tableName);

                // 1. Add the slug column if it doesn't exist
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE " + tableName + " ADD COLUMN player_slug VARCHAR(255)");
                    System.out.println("Added 'player_slug' column to " + tableName);
                } catch (SQLException e) {
                    // Column already exists, which is fine
                }

                // 2. Identify the correct player name column dynamically
                String nameColumn = "Player_name";
                try (ResultSet rsCol = conn.getMetaData().getColumns(null, null, tableName, "Player_name")) {
                    if (!rsCol.next()) {
                        // Fallback to 'player' (lowercase as seen in some of your tables like player_top_scorers)
                        nameColumn = "player";
                    }
                }

                // 3. Update slugs to ensure data consistency for Deep Learning
                String selectSql = "SELECT " + nameColumn + " FROM " + tableName;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(selectSql)) {

                    PreparedStatement pstmt = conn.prepareStatement(
                            "UPDATE " + tableName + " SET player_slug = ? WHERE " + nameColumn + " = ?"
                    );

                    int count = 0;
                    while (rs.next()) {
                        String originalName = rs.getString(1);
                        if (originalName != null && !originalName.trim().isEmpty()) {
                            // Slugify: "Julián Álvarez" -> "julian-alvarez"
                            String slug = slg.slugify(originalName);
                            pstmt.setString(1, slug);
                            pstmt.setString(2, originalName);
                            pstmt.addBatch();
                            count++;
                        }
                    }
                    pstmt.executeBatch();
                    System.out.println("Successfully updated " + count + " slugs in " + tableName);
                }
            }
            System.out.println(">>> DATABASE CLEANING COMPLETE: ALL TABLES SYNCED <<<");
        } catch (SQLException e) {
            System.err.println("Database error during cleaning: " + e.getMessage());
        }
    }

    public static void cleanManagersTable() {
        // Documentation in English: Using 'manager_slug' to align with the new database structure
        String tableName = "managers";
        String nameColumn = "Manager";

        try (Connection conn = DatabaseManager.connect()) {
            System.out.println("Updating manager slugs in table: " + tableName);

            // Fetching manager names and updating the manager_slug column
            String selectSql = "SELECT " + nameColumn + " FROM " + tableName;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(selectSql)) {

                PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE " + tableName + " SET manager_slug = ? WHERE " + nameColumn + " = ?"
                );

                int count = 0;
                while (rs.next()) {
                    String originalName = rs.getString(1);
                    if (originalName != null && !originalName.trim().isEmpty()) {
                        // Creating a clean slug: "Pep Guardiola" -> "pep-guardiola"
                        String slug = slg.slugify(originalName);
                        pstmt.setString(1, slug);
                        pstmt.setString(2, originalName);
                        pstmt.addBatch();
                        count++;
                    }
                }
                pstmt.executeBatch();
                System.out.println("Successfully updated " + count + " manager_slugs.");
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // cleanAllTables();
        cleanManagersTable();

    }
}