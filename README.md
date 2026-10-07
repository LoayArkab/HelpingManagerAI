                               # Helping Manager AI

A Java / JavaFX desktop application that recommends a **4-3-3 starting XI** for a Premier League (2023/24) team and **adapts that recommendation to the opponent it is about to face**.

The user selects "Your team" and "Opponent team" and presses **GENERATE LINEUP**. The app scores every player of both squads against the other team, picks the best eleven for each side, draws both lineups on a football pitch, and shows each team's manager.

> **In one sentence:** every player gets a score = *base rating* + *an opponent-dependent bonus*, and the lineup is made of the highest-scoring players in each position group. The key idea is that the bonus is scaled by the player's own ability, so the opponent can change *who* is selected, not just the numbers.

---

## Table of contents

1. [Quick start](#1-quick-start)
2. [System overview](#2-system-overview)
3. [The data layer](#3-the-data-layer)
4. [How a player's score is computed](#4-how-a-players-score-is-computed)
5. [How the lineup is selected](#5-how-the-lineup-is-selected)
6. [The user interface](#6-the-user-interface)
7. [Manager lookup](#7-manager-lookup)
8. [Neural network and feature export](#8-neural-network-and-feature-export)
9. [Design decisions and bugs found along the way](#9-design-decisions-and-bugs-found-along-the-way)
10. [How the system was verified](#10-how-the-system-was-verified)
11. [Detailed setup](#11-detailed-setup)
12. [Known limitations](#12-known-limitations)
13. [Future work](#13-future-work)
14. [Project structure](#14-project-structure)

---

## 1. Quick start

**What you need**
- **MySQL 8.0 or newer**, running on `localhost:3306` with the user `root`. (MariaDB and MySQL 5.7 are *not* supported by the dump.)
- **IntelliJ IDEA** (Community or Ultimate) with **JDK 21 or newer**. IntelliJ can download one for you: *File -> Project Structure -> Project -> SDK -> Add SDK -> Download JDK*.
- **Internet access for the first import.** Maven downloads the libraries (JavaFX, DeepLearning4J / ND4J). The ND4J native libraries are large (roughly 1-2 GB), so the first import can take several minutes.
- Optional: MySQL Workbench for the import step.

**Step 1 - Get the project.** Clone the repository or download it as a ZIP and unzip it. The folder that contains `pom.xml` is the project folder.

**Step 2 - Create the database and import the data.**

*Option A - MySQL Workbench:*
1. Connect to your MySQL server.
2. Create an empty schema named `football_ai` (run `CREATE DATABASE football_ai;`).
3. Choose *Server -> Data Import -> Import from Self-Contained File*, select `database/football_ai.sql`, set *Default Target Schema* to `football_ai`, and click *Start Import*.

*Option B - command line:*
```
mysql -u root -p -e "CREATE DATABASE football_ai"
mysql -u root -p football_ai < database/football_ai.sql
```

*Check:* `USE football_ai; SHOW TABLES;` should list 63 tables.

**Step 3 - Open the project in IntelliJ.** Choose *File -> Open* and select the folder that contains `pom.xml`. Confirm "Trust Project" and wait until the progress bar at the bottom right is finished. If IntelliJ asks for an SDK, choose JDK 21 or newer.

**Step 4 - Set your MySQL password.** The password is not stored in the code. Open `AppLauncher.java`, click the green arrow next to `main`, choose *Modify Run Configuration...* (or *Run -> Edit Configurations*), then *Modify options -> Environment variables*, and enter:
```
DB_PASS=<your MySQL root password>
```
(Leave it as `DB_PASS=` if your root user has no password.) Click OK.

**Step 5 - Run.** Run `footballai.AppLauncher`. A window opens and both drop-downs list the Premier League clubs. Select your team and an opponent and press **GENERATE LINEUP**. Calculating the two lineups takes several seconds, and the console prints many `AI LOGIC` lines - this is normal.

If something does not work, see the [troubleshooting table](#troubleshooting). More detail is in [section 11](#11-detailed-setup).

---

## 2. System overview

```
 MySQL database "football_ai"
   - 61 statistics tables (player-level, team-level, league/match tables)
   - premier_player_23_24 (squad roster)       - managers
        |
        v
 MainGUI (JavaFX) --> DatabaseManager.getOptimalLineup(team, opponent)
                           |
                           |- for every player of the team: predictPlayerScore(...)
                           |      |- base rating
                           |      |- player strength (pAtt / pDef / pMid)
                           |      |- opponent strength (oDefWeakRaw / oAttStrRaw)
                           |      '- position-specific bonus -> final score
                           |
                           '- sort by score, fill quotas: 1 GK / 4 DF / 3 MF / 3 FW
```

Startup sequence (`AppLauncher`):
1. If `training_data.csv` is missing, `AIFeatureFactory` generates it from the database (slow; the file is included in the repository, so this is normally skipped).
2. A small neural network is built and given a placeholder training step (see [section 8](#8-neural-network-and-feature-export)).
3. The JavaFX window (`MainGUI`) opens.

---

## 3. The data layer

### 3.1 Tables

The database `football_ai` contains:

- **61 statistics tables** (listed in `DatabaseManager.ALL_TABLES`), in three families:
    - *Player tables* (`player_*`): season statistics per player, such as goals, assists, expected goals, tackles won, interceptions, clearances, accurate passes, big chances, cards, saves, and the rating table `player_player_ratings`.
    - *Team tables* (`*_team`, `team_*`): season statistics per club, such as goals conceded, expected goals, possession, touches in the opposition box, team ratings.
    - *League / match tables*: `matches_23_24` and `pl_table_*` (overall, home, away and xG league tables).
- **`premier_player_23_24`**: the squad roster (`Player_name`, `Nation`, `Pos`, `Age`, `Team`, `player_slug`). The lineup is chosen from this table.
- **`managers`**: `Club`, `Manager`, `Nationality`, `manager_slug`.

Key columns in `player_player_ratings`: `rank_val`, `player`, `team`, `FotMob_Rating`, `Player_of_the_Match_Awards`, `Minutes`, `Matches`.

### 3.2 How the data was built (documentation only)

These classes document how the database was created. **They are not needed to run the application** - the SQL dump contains the finished data.

- `CSVImporter` loads 30 player statistics tables and the roster from CSV files into already-existing tables. The team and league tables were loaded separately and are not covered by this class. The original CSV files are not included in the repository. The importer does not clear tables before inserting, so it must not be run against the imported dump (it would duplicate rows).
- `DataCleaner` adds a `player_slug` column to the player tables using the Slugify library (for example `"Julian Alvarez"` becomes `julian-alvarez`) and fills `manager_slug` for managers. The slug is the join key between tables, because names are written differently in different sources. Its `main` currently runs only the managers step (the player tables were processed earlier).
- Some names in the source data had corrupted accents. Slugify drops such characters, which produced slugs such as `raoben-dias` and `joako-gvardiol` (see limitations).

### 3.3 Column name differences

Team columns are not named consistently (`Team`, `team`, `Club`). `scanAllColumns` inspects the table metadata at runtime and uses whichever name exists, so a single generic query routine works for all tables.

---

## 4. How a player's score is computed

All of this happens in `DatabaseManager.predictPlayerScore(conn, slug, opponent)`.

```
finalScore = baseRating + finalBonus
```

### 4.1 Base rating

- **Rated players:** the `FotMob_Rating` from `player_player_ratings` (matched by slug or by name).
- **Unrated players:** only about 1,136 of roughly 2,900 players appear in that table. Everyone else receives the neutral value **6.5**. They are still told apart from each other by their bonus, which depends on their own statistics (4.4).

### 4.2 Player strength: `pAtt`, `pDef`, `pMid`

Three blended numbers describe what a player contributes. Each is built from every table (out of the 61) whose *name* contains one of a set of keywords:

| Value | Meaning | Table-name keywords |
|---|---|---|
| `pAtt` | attacking output | goals, scoring, assists, chance |
| `pDef` | defensive output | tackle, interception, clearance, block |
| `pMid` | build-up / possession | pass, possession, touches, cross |

The computation, done by `scanGroup` and `scanAllColumns`:

1. `scanGroup` loops over `ALL_TABLES` and keeps the tables whose name matches a keyword.
2. For each such table, `scanAllColumns` runs `SELECT * FROM <table> WHERE player_slug LIKE '%<slug>%'`, takes the first matching row, and **averages every numeric column** in it. Columns whose name contains `minutes`, `matches`, `age` or `rank` are skipped, because playing time and rank are not measures of quality and would otherwise dominate the average.
3. `scanGroup` averages those per-table values across all matching tables. A table that does not contain the player (for example a team table, which has no `player_slug` column) contributes 0, which dilutes the average.
4. The result is divided by 50 to bring it into a small, comparable range.

In total **38 of the 61 tables** match at least one keyword group and therefore feed the score. The others (cards, penalties won, saves, clean sheets, awards, league tables, raw match logs, and so on) are available in the database but are not used by the score.

Averaging (instead of summing) was a deliberate decision: summing columns with different units (minutes in the thousands next to per-game values in single digits) gives meaningless totals and pushed every player to the maximum bonus.

### 4.3 Opponent strength: `oDefWeakRaw`, `oAttStrRaw`

The same mechanism is applied to the *opponent club* (matching on the `Team` / `team` / `Club` column):

| Value | Meaning | Table-name keywords |
|---|---|---|
| `oDefWeakRaw` | how much the opponent concedes / misses | conceded, missed |
| `oAttStrRaw` | how strong the opponent's attack and ratings are | goals_team, ontarget, ratings |

They are combined into one number:

```
oppFactor = (oDefWeakRaw - oAttStrRaw) / 20
```

- **Positive** -> the opponent is weak defensively relative to its attack: an opportunity to exploit.
- **Negative** -> the opponent is strong: be cautious.

Real values observed in debug output:

| Opponent | oDefWeakRaw | oAttStrRaw | oppFactor |
|---|---|---|---|
| Nottingham Forest | 24.35 | 17.25 | 0.36 |
| West Ham United | 31.21 | 19.43 | 0.59 |
| Luton Town | 32.19 | 16.73 | 0.77 |

### 4.4 Position-specific bonus

| Position group | Bonus |
|---|---|
| Forward (`FW`, `S`, `W`) | `pAtt * 0.5 + pAtt * oppFactor * 1.5` |
| Defender (`DF`, `B`) | `pDef * 0.5 - pDef * oppFactor * 1.5` |
| Everything else (midfielders, goalkeepers) | `pMid * 0.5 + pMid * oppFactor * 1.5` |

- The first term (`* 0.5`) is the player's own contribution.
- The second term is the opponent effect. For forwards and midfielders a weak opponent (positive `oppFactor`) *raises* the bonus; for defenders the sign is reversed, because defenders matter more against a strong attack.
- Position groups are recognised from substrings of the roster `Pos` field.

### 4.5 Why the opponent effect is multiplied by the player's own stat

The lineup is chosen *per position group* (best four defenders, three midfielders, three forwards). If the opponent bonus were the same constant for every forward, it would shift all forwards equally and **their order would never change**, so the lineup would never react to the opponent. This was the original bug of the project.

By multiplying the opponent effect with each player's own strength, a strong player gains (or loses) more than a weaker one, and the order inside a group can change.

**Illustrative example** (made-up numbers, real formula). Two forwards with `pAtt` = 0.30 and 0.10 and base ratings 7.00 and 7.20:

| | Player A (base 7.00, pAtt 0.30) | Player B (base 7.20, pAtt 0.10) | Selected first |
|---|---|---|---|
| vs weak team (oppFactor 0.77) | bonus 0.15 + 0.35 = 0.50 -> **7.50** | bonus 0.05 + 0.12 = 0.17 -> **7.37** | A |
| vs strong team (oppFactor -0.20) | bonus 0.15 - 0.09 = 0.06 -> **7.06** | bonus 0.05 - 0.03 = 0.02 -> **7.22** | B |

The same two players swap places depending on the opponent.

### 4.6 Final clamp

```java
double finalBonus = Math.max(-2.5, Math.min(3.5, bonus));
double finalScore = baseRating + finalBonus;
```

The bonus is limited to the range -2.5 ... +3.5. This is a safety net: whatever extreme values the data produces, a final score can never become unrealistic. In observed runs the bonus typically lies between about -0.5 and +0.4, so the clamp rarely activates.

---

## 5. How the lineup is selected

`DatabaseManager.getOptimalLineup(team, opponent, model)`:

1. Load all players of the chosen team from `premier_player_23_24`.
2. Compute `predictPlayerScore` for each one against the opponent.
3. Sort all players by score, highest first.
4. Walk down the sorted list and fill fixed quotas: **1 goalkeeper, 4 defenders, 3 midfielders, 3 forwards** (a 4-3-3). A player is skipped when his group is already full or he has already been selected.
5. Stop at 11 players.

The GUI calls this function twice, once for each team, with the roles of "team" and "opponent" swapped. (The `model` parameter - the neural network - is passed in but not used; see section 8.)

Because each position group is filled independently, a group only changes when the opponent effect is large enough to reorder the players *inside that group*. This is why some groups change more than others on a given matchup (see limitations).

---

## 6. The user interface

`MainGUI` (JavaFX):

- Left sidebar: two drop-downs (filled from `DatabaseManager.getAllTeams()`) and the **GENERATE LINEUP** button.
- Right side: a football pitch image (`football_pitch.jpg`, loaded from the project root; a plain green field is used if it is missing) with player cards positioned by **percentage of the pitch size**, using JavaFX property binding, so the layout stays correct when the window is resized.
- The home side is drawn on the left and the away side on the right. Each card shows the player name and nationality; manager cards are shown at the top.
- Player names coming from the database may contain corrupted characters, so `cleanPlayerName` repairs common artefacts and strips everything that is not a plain Latin letter, space or hyphen before display.

---

## 7. Manager lookup

`DatabaseManager.getCoachForTeam(teamName)` finds the manager of a club:

1. First an **exact** match on the club name.
2. Only if that fails, a looser `LIKE` search.

The exact match matters: an earlier version searched only for the last word of the club name, so every club ending in "United" (West Ham United, Sheffield United) was matched to Manchester United's manager.

---

## 8. Neural network and feature export

- `AIFeatureFactory` builds `training_data.csv`: one row per player with one numeric feature per statistics table and the player's rating as the target. It exports 56 of the tables (not all 61).
- `NeuralNetworkTrainer` defines a small DeepLearning4J regression network (63 inputs -> 100 -> 50 -> 1 output, ReLU hidden layers, MSE loss).

**Important and honest note:** the network is currently *infrastructure*. Its training step uses a placeholder (random input with a fixed target value) and it does **not** drive the lineup decisions. The lineup is decided entirely by the scoring formula in section 4. Training the network on real data and letting it replace the hand-set weights is listed under future work. Known issues of this part:

- The network expects 63 inputs but the export has 56 features (harmless while training is a placeholder).
- The player's rating is both one of the features and the target (target leakage), so the export would need to be changed before real training.
- Generating the file is slow (one query per player per table), which is why the generated file is included in the repository.

---

## 9. Design decisions and bugs found along the way

| # | Problem | Cause | Fix |
|---|---|---|---|
| 1 | Lineup identical for every opponent | The opponent bonus was a flat constant for all players in a group, so it never changed their relative order | Multiply the opponent effect by the player's own strength (4.5) |
| 2 | Wrong manager displayed | Search used only the last word of the club name ("United") | Exact full-name match first, fuzzy match only as a fallback |
| 3 | Almost all bonuses saturated at the maximum | Raw statistics from many tables were *summed* (mixing minutes with per-game values) | Average instead of sum, skip minutes/rank/age columns, scale down |
| 4 | Opponent statistics always zero | The team column is named `Team`, `team` or `Club` depending on the table | Detect the column name from table metadata |
| 5 | Different opponents produced identical bonuses | `oppFactor` was clamped to +-1.0 and several opponents exceeded the limit, so they collapsed to the same value | Remove the clamp on `oppFactor` and rely on the final bonus clamp |
| 6 | Midfield reacted half as much as the other groups | Midfield multiplier was 0.75 instead of 1.5 | Equalised to 1.5 |
| 7 | Startup stack trace from the neural network | Labels were passed to ND4J with the wrong shape | Use a 2-D label array |
| 8 | Pitch image and data paths only worked on one computer | Absolute `C:\Users\...` paths | Relative path for the image; credentials moved to an environment variable |

---

## 10. How the system was verified

Verification was done with console logging of every score (`AI LOGIC: [player vs opponent] | Base | Bonus | Final`) and manual comparisons:

- **Same team against opponents of different strength.** The selected players and the printed bonuses differ between a weak and a strong opponent.
- **Direction check.** An attacker's bonus is larger against a weak defence and smaller against a strong one. For example, in an early run Haaland's bonus was 0.30 against Burnley and 0.04 against Liverpool.
- **Determinism.** The same matchup run twice produces identical output, and swapping the order in which the two teams are chosen gives the same two lineups.
- **Structural check across clubs.** Each team returns eleven players with the correct position counts, and the players belong to the right club.
- **Debug traces of the opponent factor.** Printing `oDefWeakRaw`, `oAttStrRaw` and `oppFactor` per opponent exposed the clamp problem in row 5 above.

---

## 11. Detailed setup

**Requirements**
- JDK 21 or newer (the `pom.xml` targets Java 21; the project was developed and tested with JDK 25) and Maven (bundled with IntelliJ).
- MySQL 8.0 or newer.
- JavaFX 21, MySQL connector, DeepLearning4J/ND4J, Slugify and OpenCSV are resolved by Maven.

**Database**
```
mysql -u root -p -e "CREATE DATABASE football_ai"
mysql -u root -p football_ai < database/football_ai.sql
```
The dump contains the statistics tables, the roster and the managers, including the slug columns.

**Configuration.** The database connection is `jdbc:mysql://localhost:3306/football_ai` with user `root`. The password is read from the environment variable **`DB_PASS`**, so no secret is stored in the repository. In IntelliJ: *Run -> Edit Configurations -> Environment variables -> `DB_PASS=<your password>`*. If the password is wrong or MySQL is not running, the two team drop-downs will be empty and an error is printed in the console.

**Run.** Start `footballai.AppLauncher` from IntelliJ (running from the command line was not tested).
- `training_data.csv` is included, so the slow feature export does not run on startup.
- The pitch image `football_pitch.jpg` must be in the project root (the working directory).
- Warnings printed at startup (SLF4J, `sun.misc.Unsafe`, "Unsupported JavaFX configuration") come from third-party libraries and are harmless. The console should show `AI is learning player importance patterns...` and `Model weights optimized via Backpropagation.` without an error in between.

### Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Red errors on `import javafx...`, or the package shows as `java.footballai` | IntelliJ was opened on the wrong folder. Close the project and open the folder that contains `pom.xml`, then use the Maven tool window -> *Reload All Maven Projects*. |
| The window opens but both team drop-downs are empty | The app cannot connect to MySQL. Check that MySQL is running, that the database `football_ai` was imported, and that `DB_PASS` is set to *your* MySQL password in *Run -> Edit Configurations -> Environment variables*. The console shows `Access denied ... (using password: NO)` when the variable is missing. |
| `DB_PASS` is set in Windows but the app does not see it | Environment variables are only passed to programs started afterwards. Restart IntelliJ. |
| Errors about the Java version, or "release version not supported" | The `pom.xml` targets Java 21, so JDK 21 or newer is required. Select one under *File -> Project Structure -> Project -> SDK* (IntelliJ can download it: *Add SDK -> Download JDK*). |
| Maven cannot download the libraries, or the first import is very slow | Internet access is required for the first import, and the ND4J native libraries are large (1-2 GB). Wait for the progress bar; if it stopped, use *Reload All Maven Projects*. |
| `Public Key Retrieval is not allowed` in the console | Append `&allowPublicKeyRetrieval=true` to the `URL` constant in `DatabaseManager.java`. |
| The SQL import fails (for example an unknown collation) | The dump was created with MySQL 8.0. Use MySQL 8.0 or newer; MariaDB and MySQL 5.7 are not supported. |
| The pitch is plain green | `football_pitch.jpg` was not found. It must be in the project root (the working directory). |

If you prefer not to use an environment variable, you can type your own password directly into the `PASS` constant in `DatabaseManager.java` on your own computer. Do not commit that change.

---

## 12. Known limitations

- **It is a heuristic, not a validated model.** "Best lineup" means best according to the weighting in section 4. It was not tested against real match results, and the constants (0.5, 1.5, /20, /50, the clamp bounds) are reasoned design choices, not fitted values.
- **Rating coverage is incomplete.** Only about 1,136 of roughly 2,900 players have a real rating. All others start from a neutral 6.5, so many fringe players look similar to each other.
- **Missing and sparse data.** Many players have little or no data in the other tables, so their `pAtt` / `pDef` / `pMid` are close to zero.
- **Not every position changes for every matchup.** Core starters usually stay and marginal slots change. A group whose base ratings are far apart cannot be reordered by a small opponent adjustment.
- **Only 38 of the 61 tables feed the score.** Tables are chosen by keyword relevance. Cards, penalties, saves, clean sheets, awards, league tables and raw match logs are not used by the score.
- **Averaging is diluted.** A table that does not contain the player (or club) contributes 0 to the group average, because the match is by `LIKE '%name%'` on the first row.
- **Opponent measures are partly noisy.** The opponent keyword groups also match some player-level tables. Where such a table has a team column, the query reads the first row for that club (one player's values), not a club aggregate.
- **Name problems.** Some accented names are corrupted differently in different tables (for example `raoben-dias`, `joako-gvardiol`), so a few players do not match across tables. Very short names could also match the wrong row with `LIKE`.
- **Goalkeepers have no dedicated formula;** they are scored with the default (midfield) branch.
- **Fixed formation** (4-3-3 only) and **a single season** (2023/24) with static data.
- **The neural network is a placeholder** (section 8).

---

## 13. Future work

- Estimate a base rating for unrated players from their own statistics instead of the neutral 6.5.
- Separate attacking output into **player styles** (for example possession/creativity versus directness/pace) so the system can react to an opponent's *playing style*, not only its overall strength.
- Calibrate the weights with real match outcomes and train the neural network on real data (fixing the feature count and target leakage first).
- Normalise player names once, at import time, so every player matches across all tables.
- Use more of the unused tables (discipline, set pieces, league position) and add a dedicated goalkeeper formula.
- Support additional formations and other seasons.

---

## 14. Project structure

| Path | Purpose |
|---|---|
| `src/main/java/footballai/AppLauncher.java` | Entry point: optional feature export, network setup, starts the GUI |
| `.../MainGUI.java` | JavaFX interface: team selection, pitch rendering, manager cards |
| `.../DatabaseManager.java` | Database access, scoring (`predictPlayerScore`, `scanGroup`, `scanAllColumns`), lineup selection, manager lookup |
| `.../AIFeatureFactory.java` | Generates `training_data.csv` |
| `.../NeuralNetworkTrainer.java` | DeepLearning4J network definition and placeholder training |
| `.../CSVImporter.java` | One-time tool that loaded CSV files into MySQL (documentation only) |
| `.../DataCleaner.java` | One-time tool that added `player_slug` / `manager_slug` (documentation only) |
| `.../Player.java` | Simple data class used by the GUI |
| `football_pitch.jpg` | Pitch background image |
| `training_data.csv` | Pre-generated feature export |
| `database/football_ai.sql` | Full database dump |
| `pom.xml` | Maven build file |