package footballai;

import javafx.application.Application;

public class AppLauncher {
    // מתודת ה-main: נקודת הכניסה הראשית של התוכנית כולה.
    public static void main(String[] args) {
        System.out.println("--- Starting HELPING_MANAGER AI ---");

        // יצירת אובייקט המייצג את קובץ נתוני האימון בתיקיית הפרויקט
        java.io.File file = new java.io.File("training_data.csv");

        // בדיקה: אם קובץ הנתונים (CSV) לא קיים, נקרא ל-FeatureFactory כדי לייצר אותו מ-63 הטבלאות.
        if (!file.exists()) {
            AIFeatureFactory.generateTrainingData();
        }

        try {
            // יצירת מופע חדש של מחלקת המאמן (NeuralNetworkTrainer).
            NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();

            // בניית רשת נוירונית עם 63 קלטים (אחד לכל טבלה) ושמירתה במשתנה הסטטי ב-MainGUI.
            MainGUI.aiModel = trainer.buildNetwork(63);

            // הפעלת תהליך האימון המהיר של המודל כדי לייעל את המשקולות בתוך הרשת.
            trainer.trainQuickly(MainGUI.aiModel);

            System.out.println("Neural Network is trained and ready.");
        } catch (Exception e) {
            // הדפסת עקבות השגיאה במקרה של תקלה באתחול או באימון ה-AI.
            e.printStackTrace();
        }

        // הפעלת הממשק הגרפי (JavaFX) על ידי קריאה למחלקת ה-MainGUI.
        Application.launch(MainGUI.class, args);
    }
}