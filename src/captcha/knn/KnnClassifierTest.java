package captcha.knn;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

/**
 * @author Mathieu Clément
 * @since 25.12.2013
 */
public class KnnClassifierTest {

    public static final boolean SERIALIZATION_ENABLED = false;
    public static boolean CASE_SENSITIVE = true;


    private static Item<Character, Float[]>[] trainingItems;
    private static Item<Character, Float[]>[] testingItems;
    private static int[] totalSymbols;
    static Semaphore[] totalSymbolsSem = new Semaphore[200];

    static {
        for (int i = 0; i < 200; i++) {
            totalSymbolsSem[i] = new Semaphore(1);
        }
    }

    private static int[][] guesses;
    static Semaphore[] guessesSem = new Semaphore[200];

    static {
        for (int i = 0; i < 200; i++) {
            guessesSem[i] = new Semaphore(1);
        }
    }

    private static int correctGuesses;
    static Semaphore correctGuessesSem = new Semaphore(1);
    private static int totalGuesses;
    static Semaphore totalGuessesSem = new Semaphore(1);
    private static int k;

    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
        String trainingFilename = args[0];
        String testingFilename = args[1];
        k = Integer.parseInt(args[2]);
        if ((k & 1) == 0) throw new IllegalArgumentException("k must be an odd number.");
        if (k != 1) System.out.println("WARNING: It looks like program is buggy or my data was crap with k <> 1");
        int nbFeatures = Integer.parseInt(args[3]);
        CASE_SENSITIVE = Boolean.parseBoolean(args[4]);

        InputStream trainingFis = new KnnClassifier.UnclosableBufferedInputStream(new FileInputStream(trainingFilename));
        InputStream testingFis = new KnnClassifier.UnclosableBufferedInputStream(new FileInputStream(testingFilename));

        trainingItems = load(trainingFilename, nbFeatures, trainingFis);
        testingItems = load(testingFilename, nbFeatures, testingFis);

        int nbTestingItems = testingItems.length;

        totalGuesses = 0;
        correctGuesses = 0;

        guesses = new int[200][200];
        totalSymbols = new int[200];

        int NB_THREADS = Runtime.getRuntime().availableProcessors() / 4 + 1; // Use all processors

        Thread[] threads = new Thread[NB_THREADS];
        for (int i = 0; i < NB_THREADS; i++) {
            threads[i] = new Thread(new MyRunnable(i * nbTestingItems / NB_THREADS, (i + 1) * nbTestingItems / NB_THREADS));
        }
        for (int i = 0; i < NB_THREADS; i++) {
            threads[i].start();
        }
        for (int i = 0; i < NB_THREADS; i++) {
            threads[i].join();
        }

        // Symbol stats
        char[] chars = new char[CASE_SENSITIVE ? 62 : 36];
        {
            int i = 0;
            for (char c = 'A'; c <= 'Z'; c++) chars[i++] = c;
            if (CASE_SENSITIVE) for (char c = 'a'; c <= 'z'; c++) chars[i++] = c;
            for (char c = '0'; c <= '9'; c++) chars[i++] = c;
        }

        // Symbol stats
        // Table header
        System.out.print("     ");
        for (char c : chars) System.out.print("  " + c);
        System.out.println("  | Accuracy");

        if (CASE_SENSITIVE) {
            System.out.println("--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        } else {
            System.out.println
                    ("--------------------------------------------------------------------------------------------------------------------");
        }


        float[] symbolMeans = new float[chars.length];
        int smi = 0;

        // Lines of statistics
        int varianceN = 0;
        float varianceSum = 0;
        float varianceSumSqr = 0;
        for (char cLeft : chars) {
            System.out.print(cLeft + "  | ");
            for (char cTop : chars) {
                int g = guesses[cLeft][cTop];
                if (g == 0) System.out.print("   ");
                else if (cLeft == cTop) System.out.print("  X");
                else System.out.printf("%3d", g);
            } // end for cRight
            float mean = (float) guesses[cLeft][cLeft] /
                    totalSymbols[cLeft];
            if (!Float.isNaN(mean)) {
                varianceN++;
                varianceSum += mean;
                varianceSumSqr += mean * mean;
            }
            symbolMeans[smi++] = mean;
            System.out.printf("  |  %.0f %% (%02d/%02d)", 100.0 * mean,
                    guesses[cLeft][cLeft], totalSymbols[cLeft]);
            System.out.println();
            if (CASE_SENSITIVE) {
                System.out.println("--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
            } else {
                System.out.println
                        ("--------------------------------------------------------------------------------------------------------------------");
            }
        } // end for cLeft
        Arrays.sort(symbolMeans);

        /**
         * def naive_variance(data):
         variance = (Sum_sqr - (Sum*Sum)/n)/(n - 1)
         return variance
         */

        // Global stats
        System.out.println();
        System.out.println("Score: " + correctGuesses + " / " + totalGuesses + " (" +
                (100.0 * ((float) correctGuesses / totalGuesses)) + " %)");


        float median = symbolMeans[symbolMeans.length / 2];
        float variance = (varianceSumSqr - (varianceSum * varianceSum) / varianceN) / (varianceN - 1);
        System.out.println("Median: " + 100.0 * median);
        System.out.println("Variance: " + 100.0 * 100.0 * variance);
        System.out.println("Standard deviation: " + Math.sqrt(100.0 * 100.0 * variance));
    } // end main

    private static Item<Character, Float[]>[] load(String filename, int nbFeatures, InputStream trainingFis) throws IOException, ClassNotFoundException {
        Item<Character, Float[]>[] items;
        if (filename.toLowerCase().endsWith(".ser")) {
            items = KnnClassifier.unserialize(trainingFis);
            System.err.println(items.length + " samples unserialized.");
        } else {
            items = KnnClassifier.parse(trainingFis, nbFeatures);
            System.err.println(items.length + " samples parsed.");
            if (SERIALIZATION_ENABLED) {
                String outputFilename = filename.substring(0, filename.lastIndexOf('.')) + ".ser";
                FileOutputStream fos = new FileOutputStream(outputFilename);
                KnnClassifier.serialize(items, fos);
                System.err.println("Serialized data is saved in " + outputFilename);
                fos.close();
            }
        }
        return items;
    }

    private static class MyRunnable implements Runnable {

        private int first, last;

        public MyRunnable(int first, int last) {
            this.first = first;
            this.last = last;
        }

        @Override
        public void run() {
            for (int i = first; i < last; i++) {
                Item<Character, Float[]> testingItem = testingItems[i];
                Character actual = testingItem.output;
                try {
                    totalSymbolsSem[actual].acquire();
                    totalSymbols[actual]++;
                    totalSymbolsSem[actual].release();
                    Character guessed = KnnClassifier.findNeighbour(k, trainingItems, testingItem.inputs);

                    guessesSem[actual].acquire();
                    guesses[actual][guessed]++;
                    guessesSem[actual].release();

                    if (actual.equals(guessed)) {
                        correctGuessesSem.acquire();
                        correctGuesses++;
                        correctGuessesSem.release();
                    }

                    totalGuessesSem.acquire();
                    totalGuesses++;
                    totalGuessesSem.release();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    }
} // end class
