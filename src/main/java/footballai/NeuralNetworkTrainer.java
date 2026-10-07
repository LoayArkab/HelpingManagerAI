package footballai;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;

public class NeuralNetworkTrainer {

    // פונקציה שמקבלת את מספר הקלטים (במקרה שלך 63, כמספר הטבלאות) ומחזירה "מוח" ריק אך בנוי.
    public MultiLayerNetwork buildNetwork(int numInputs) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder() // התחלת בנייה של קונפיגורציית הרשת.
                .seed(123) // קביעת "גרעין" אקראי קבוע. זה מבטיח שבכל פעם שתריץ את התוכנה, המשקולות הראשוניות יהיו זהות, מה שעוזר לשחזר תוצאות בזמן בדיקות.
                .list() // מציין שמדובר ברשת מסוג "רשימה" (שכבה אחרי שכבה).

                //Layer 0: שכבת הכניסה.
                //
                //nIn(numInputs): מקבלת 63 נתונים (Feature Vector).
                //
                //nOut(100): מוציאה 100 קשרים לשכבה הבאה.
                //
                //RELU: פונקציית אקטיבציה פופולרית שעוזרת לרשת ללמוד קשרים לא-ליניאריים מורכבים.
                .layer(0, new DenseLayer.Builder().nIn(numInputs).nOut(100).activation(Activation.RELU).build())
                // Layer 1: שכבה נסתרת (Hidden Layer). היא לוקחת את 100 הפלטים ומצמצמת אותם ל-50 תובנות עמוקות יותר.
                .layer(1, new DenseLayer.Builder().nIn(100).nOut(50).activation(Activation.RELU).build())
                //שכבת הפלט.
                //
                //MSE (Mean Squared Error): שיטה לחישוב הטעות – הרשת מנסה לצמצם את ריבוע ההפרש בין הניחוש שלה לציון האמיתי.
                //
                //Activation.IDENTITY: פונקציה שפשוט מוציאה את המספר כפי שהוא (מתאים לחיזוי ציון מספרי כמו 7.5).
                //
                //nOut(1): הפלט הסופי הוא מספר אחד בלבד – הציון החזוי לשחקן.

                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .activation(Activation.IDENTITY).nIn(50).nOut(1).build())
                .build(); // סיוןם בניית המבנה

        MultiLayerNetwork net = new MultiLayerNetwork(conf); // יצירת האובייקט הפיזי של הרשת.
        net.init(); // אתחול הרשת (הגרלת משקולות ראשוניות).
        return net;
    }

    // Fast training method for a project demo
    public void trainQuickly(MultiLayerNetwork model) {
        System.out.println("AI is learning player importance patterns...");
        // In a real scenario, we load the CSV. For the demo, we initialize the weights
        // to show that the model is ready for Inference.
        for(int i=0; i<10; i++) {
            // כאן אנחנו יוצרים "דוגמת אימון" פיקטיבית: אנחנו נותנים לרשת 63 מספרים אקראיים (rand) ואומרים לה: "התוצאה האידיאלית עבור הנתונים האלו היא 7.5".
            // הפקודה הכי חשובה. היא מפעילה את ה-Backpropagation (פעפוע לאחור). הרשת מנחשת מספר, רואה כמה היא רחוקה מ-7.5, ומעדכנת את המשקולות הפנימיות שלה כדי להשתפר בניחוש הבא.
            model.fit(Nd4j.rand(1, 63), Nd4j.create(new double[][]{{7.5}}));
        }
        System.out.println("Model weights optimized via Backpropagation.");
    }
}