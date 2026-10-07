package footballai;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import javafx.application.Platform;

public class MainGUI extends Application {

    // הגדרת המרכיבים הראשיים של הממשק: פאנל המגרש, תיבות הבחירה ומודל ה-AI.
    private Pane pitchPane;
    private ComboBox<String> myTeamCombo;
    private ComboBox<String> opponentTeamCombo;
    public static MultiLayerNetwork aiModel;

    @Override
    public void start(Stage primaryStage) {
        // הגדרות בסיסיות לחלון: כותרת, אפשור כפתורי מערכת (X, הגדלה) ואפשרות לשינוי גודל.
        primaryStage.setTitle("HELPING_MANAGER AI");
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.setResizable(true);

        // יצירת סרגל הצד (Sidebar) בצבע כהה עם ריווחים מתאימים.
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(25));
        sidebar.setStyle("-fx-background-color: #1a1a1a;");
        sidebar.setPrefWidth(320);

        // הוספת כותרת ראשית לסרגל הצד.
        Label titleLabel = new Label("HELPING_MANAGER AI");
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        titleLabel.setPadding(new Insets(15, 0, 0, 0));

        // הגדרת תגיות ותיבות בחירה (ComboBox) לבחירת קבוצת הבית והחוץ.
        Label yourTeamLabel = new Label("YOUR TEAM:");
        yourTeamLabel.setTextFill(Color.WHITE);
        yourTeamLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        myTeamCombo = new ComboBox<>();
        myTeamCombo.setPromptText("Select Home Team");
        myTeamCombo.setMaxWidth(Double.MAX_VALUE);

        Label opponentLabel = new Label("OPPONENT TEAM:");
        opponentLabel.setTextFill(Color.WHITE);
        opponentLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        opponentTeamCombo = new ComboBox<>();
        opponentTeamCombo.setPromptText("Select Away Team");
        opponentTeamCombo.setMaxWidth(Double.MAX_VALUE);

        // מילוי תיבות הבחירה ברשימת הקבוצות שנשלפו מה-Database.
        List<String> allTeams = DatabaseManager.getAllTeams();
        myTeamCombo.getItems().addAll(allTeams);
        opponentTeamCombo.getItems().addAll(allTeams);

        // יצירת כפתור ה-Generate בעיצוב ירוק בולט.
        Button generateBtn = new Button("GENERATE LINEUP");
        generateBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        generateBtn.setMaxWidth(Double.MAX_VALUE);

        // הוספת כל האלמנטים לסרגל הצד.
        sidebar.getChildren().addAll(titleLabel, yourTeamLabel, myTeamCombo, opponentLabel, opponentTeamCombo, generateBtn);

        // יצירת פאנל המגרש (Pitch).
        pitchPane = new Pane();
        pitchPane.setPrefSize(950, 800);

        // טעינת תמונת רקע של מגרש כדורגל וקשירת הגודל שלה לגודל הפאנל (שינוי גודל דינמי).
        try {
            String path = "football_pitch.jpg";
            ImageView pitchView = new ImageView(new Image(new FileInputStream(path)));
            pitchView.fitWidthProperty().bind(pitchPane.widthProperty());
            pitchView.fitHeightProperty().bind(pitchPane.heightProperty());
            pitchPane.getChildren().add(pitchView);
        } catch (FileNotFoundException e) {
            // במקרה שהתמונה לא נמצאה, המגרש ייצבע בירוק חלק.
            pitchPane.setStyle("-fx-background-color: #1b5e20;");
        }

        // הגדרת פעולה ללחיצה על הכפתור: הפעלת חישוב ה-AI לעדכון ההרכב.
        generateBtn.setOnAction(e -> updateLineupWithAI());

        // ארגון המבנה הראשי: סרגל צד משמאל ומגרש מימין.
        HBox mainLayout = new HBox(sidebar, pitchPane);
        HBox.setHgrow(pitchPane, Priority.ALWAYS); // המגרש יתפוס את כל השטח הפנוי.

        // הוספת ScrollPane כדי לאפשר גלילה במידה והמסך קטן מדי.
        ScrollPane scrollRoot = new ScrollPane(mainLayout);
        scrollRoot.setFitToWidth(true);
        scrollRoot.setFitToHeight(true);

        // יצירת הסצנה והצגת החלון.
        Scene scene = new Scene(scrollRoot, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();

        // הצגת החלון על המסך.
        primaryStage.show();

        // שימוש ב-Platform.runLater כדי לצייר את המגרש הריק רק אחרי שהחלון נפתח והמידות שלו נקבעו.
        Platform.runLater(this::renderEmptyFormation);
    }

    // מתודה לציור מצב התחלתי של המגרש (לפני שנבחרה קבוצה).
    private void renderEmptyFormation() {
        // ניקוי כל השחקנים הקיימים מהמגרש (שמירה רק על תמונת הרקע).
        if (pitchPane.getChildren().size() > 1) {
            pitchPane.getChildren().remove(1, pitchPane.getChildren().size());
        }
        // הצבת תיבות ריקות למאמנים ולשוערים במיקומים יחסיים (אחוזים).
        drawEntity("HOME COACH", "MANAGER", 35.0, 5.0, true);
        drawEntity("AWAY COACH", "MANAGER", 60.0, 5.0, true);

        drawEntity("GK", "HOME", 5.0, 50.0, false);
        drawEntity("GK", "AWAY", 92.0, 50.0, false);

        // ציור מיקומים ריקים לשאר חלקי המגרש (הגנה, קישור, התקפה).
        renderPositionalPlaceholders(18.0, 32.0, 45.0, "HOME");
        renderPositionalPlaceholders(80.0, 65.0, 53.0, "AWAY");
    }

    // פונקציית עזר לציור ריבועי מיקום כלליים (DEF, MID, FWD).
    private void renderPositionalPlaceholders(double xDef, double xMid, double xFwd, String label) {
        double[] defY = {20.0, 40.0, 60.0, 80.0};
        double[] midY = {30.0, 50.0, 70.0};
        double[] fwdY = {30.0, 50.0, 70.0};

        for (double y : defY) drawEntity("DEF", label, xDef, y, false);
        for (double y : midY) drawEntity("MID", label, xMid, y, false);
        for (double y : fwdY) drawEntity("FWD", label, xFwd, y, false);
    }

    // המתודה המופעלת בלחיצה על הכפתור: שואבת הרכב מה-AI ומציגה אותו.
    private void updateLineupWithAI() {
        String myTeam = myTeamCombo.getValue();
        String oppTeam = opponentTeamCombo.getValue();
        if (myTeam == null || oppTeam == null) return;

        // ניקוי המגרש.
        if (pitchPane.getChildren().size() > 1) {
            pitchPane.getChildren().remove(1, pitchPane.getChildren().size());
        }

        // קריאה ל-DatabaseManager לקבלת 11 השחקנים הטובים ביותר לכל צד לפי מודל ה-AI.
        List<Player> homeLineup = DatabaseManager.getOptimalLineup(myTeam, oppTeam, aiModel);
        List<Player> awayLineup = DatabaseManager.getOptimalLineup(oppTeam, myTeam, aiModel);

        // ציור השחקנים על המגרש.
        renderPlayers(homeLineup, "HOME");
        renderPlayers(awayLineup, "AWAY");

        // שליפת שמות המאמנים מה-Database.
        String coachHome = DatabaseManager.getCoachForTeam(myTeam);
        String coachAway = DatabaseManager.getCoachForTeam(oppTeam);

        // הצבת כרטיסי המאמנים בראש המגרש.
        drawEntity(coachHome, "MANAGER", 35.0, 5.0, true);
        drawEntity(coachAway, "MANAGER", 60.0, 5.0, true);
    }

    // פונקציה לציור רשימת שחקנים על המגרש לפי העמדות שלהם.
    private void renderPlayers(List<Player> players, String side) {
        double[] defY = {20.0, 40.0, 60.0, 80.0};
        double[] midY = {30.0, 50.0, 70.0};
        double[] fwdY = {30.0, 50.0, 70.0};
        int dIdx = 0, mIdx = 0, fIdx = 0;

        // קביעת קואורדינטות X (רוחב) באחוזים לפי צד המגרש.
        double xGk = (side.equals("HOME")) ? 5.0 : 92.0;
        double xDef = (side.equals("HOME")) ? 18.0 : 80.0;
        double xMid = (side.equals("HOME")) ? 32.0 : 65.0;
        double xFwd = (side.equals("HOME")) ? 45.0 : 53.0;

        for (Player p : players) {
            String cleanName = cleanPlayerName(p.getName()); // ניקוי תווים משובשים מהשם.
            String pos = p.getPos().toUpperCase();
            // התאמת מיקום השחקן על המגרש לפי העמדה שלו.
            if (pos.contains("GK")) {
                drawEntity(cleanName, p.getNation(), xGk, 50.0, false);
            } else if ((pos.contains("DF") || pos.contains("B")) && dIdx < 4) {
                drawEntity(cleanName, p.getNation(), xDef, defY[dIdx++], false);
            } else if ((pos.contains("MF") || pos.contains("M")) && mIdx < 3) {
                drawEntity(cleanName, p.getNation(), xMid, midY[mIdx++], false);
            } else if ((pos.contains("FW") || pos.contains("S") || pos.contains("W")) && fIdx < 3) {
                drawEntity(cleanName, p.getNation(), xFwd, fwdY[fIdx++], false);
            }
        }
    }

    // הפונקציה החשובה ביותר למיקום: קושרת את כרטיס השחקן לאחוז מסוים מהמגרש (Responsive UI).
    private void drawEntity(String name, String nation, double xPercent, double yPercent, boolean isCoach) {
        VBox card = createPersonCard(name, nation, isCoach);

        // שימוש ב-Property Binding: ה-X וה-Y של הכרטיס מחושבים אוטומטית כשהמגרש משנה גודל.
        card.layoutXProperty().bind(pitchPane.widthProperty().multiply(xPercent / 100.0).subtract(card.widthProperty().divide(2)));
        card.layoutYProperty().bind(pitchPane.heightProperty().multiply(yPercent / 100.0).subtract(card.heightProperty().divide(2)));

        pitchPane.getChildren().add(card);
    }

    // יצירת העיצוב הוויזואלי של כרטיס השחקן/מאמן (צבעים, פונטים ומסגרות).
    private VBox createPersonCard(String name, String nation, boolean isCoach) {
        VBox card = new VBox(2);
        card.setAlignment(Pos.CENTER);

        // בחירת צבעים: שחור למאמן, לבן שקוף לשחקן.
        String bgColor = isCoach ? "#000000" : "rgba(245, 245, 245, 0.95)";
        String borderColor = isCoach ? "#2ecc71" : "#7f8c8d";

        card.setStyle("-fx-background-color: " + bgColor + "; " +
                "-fx-background-radius: 4; " +
                "-fx-padding: 6; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 4;");

        // הגדרת תגית השם.
        Label nameLbl = new Label(name.toUpperCase());
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        nameLbl.setTextFill(isCoach ? Color.WHITE : Color.BLACK);
        nameLbl.setWrapText(true);
        nameLbl.setAlignment(Pos.CENTER);
        nameLbl.setMaxWidth(85);

        // הגדרת תגית הלאום/תפקיד (צבע ירוק זוהר למאמנים).
        Label nationLbl = new Label(nation);
        nationLbl.setFont(Font.font("System", FontWeight.BOLD, 8));
        if (isCoach) {
            nationLbl.setTextFill(Color.SPRINGGREEN);
        } else {
            nationLbl.setTextFill(Color.web("#2980b9"));
        }

        card.getChildren().addAll(nameLbl, nationLbl);
        card.setPrefWidth(95);
        return card;
    }

    // פונקציית עזר לניקוי שמות משחקנים שמגיעים עם קידוד פגום מה-Database.
    private String cleanPlayerName(String name) {
        if (name == null) return "";
        // החלפת תווים פגומים באותיות לטיניות תואמות.
        String fixed = name.replace("Ã¡", "a").replace("Ã±", "n").replace("Ã", "i")
                .replace("Ã³", "o").replace("Ã©", "e").replace("Ãº", "u")
                .replace("Â", "");
        // הסרת כל מה שאינו אות או רווח.
        fixed = fixed.replaceAll("[^a-zA-Z\\s\\-]", "");
        return fixed.trim().toUpperCase();
    }
}