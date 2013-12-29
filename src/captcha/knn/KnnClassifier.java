package captcha.knn;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * @author Mathieu Clément
 * @since 25.12.2013
 */
public class KnnClassifier {

    public static int countLines(InputStream is) throws IOException {
        int lines = 0;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        while (reader.readLine() != null) lines++;
        reader.close();
        return lines;
    }

    public static Item<Character, Float[]>[] parse(InputStream is, int nbFeatures) throws IOException {
        int lines = countLines(is);
        //is.reset();
        Item[] items = new Item[lines / 2];

        int samples = 0;

        Scanner scanner = new Scanner(new BufferedInputStream(is));


        boolean isInputLine = true;

        for (int j = 0; j < lines; j++) {
            String line = scanner.nextLine();
            if (isInputLine) {
                items[samples] = new Item();
                Float[] inputs = new Float[nbFeatures];
                StringTokenizer tokenizer = new StringTokenizer(line);
                for (int i = 0; i < nbFeatures; i++) {
                    try {
                        inputs[i] = Float.parseFloat(tokenizer.nextToken());
                    } catch (Exception ime) {
                        System.err.println("Could not parse inputs for sample " + samples + " line " + (samples * 2 +
                                1) + " from " + is);
                        throw new RuntimeException(ime);
                    }
                }
                items[samples].inputs = inputs;
            } else {
                items[samples++].output = line.charAt(0);
            }

            // Flip isInputLine
            isInputLine = !isInputLine;
        }

        scanner.close();

        return items;
    }

    public static float taxicabDistance(Float[] a, Float[] b) {
        float distance = 0;
        for (int i = 0; i < a.length; i++) {
            distance += (b[i] > a[i]) ? b[i] - a[i] : a[i] - b[i];
        }
        return distance;
    }

    public static float euclideanDistance(Float[] a, Float[] b) {
        float distance = 0;
        for (int i = 0; i < a.length; i++) {
            float d = b[i] - a[i];
            distance += d * d;
        }
        return (float) Math.sqrt(distance);
    }

    public static float distance(Item<Character, Float[]> trainingItem, Float... features) {
        return euclideanDistance(trainingItem.inputs, features);
    }

    private static class Neighbour {
        int id;
        float distance;
    }

    public static Character findNeighbour(int k, Item<Character, Float[]>[] trainingItems, Float... features) {
        // k is the number of n1eighbours
        Neighbour[] neighbours = new Neighbour[k];
        // Init
        for (int i = 0; i < k; i++) {
            neighbours[i] = new Neighbour();
            neighbours[i].distance = Float.MAX_VALUE;
        }

        for (int i = 0; i < trainingItems.length; i++) {
            Item<Character, Float[]> item = trainingItems[i];
            float distance = distance(item, features);
            if (distance < neighbours[0].distance) {

                // shift 2nd neighbour until last. Last is thrown out
                for (int n = k - 1; n >= 1; n--) {
                    neighbours[n] = neighbours[n - 1];
                }
                // Replace first / nearest neighbour
                neighbours[0] = new Neighbour();
                neighbours[0].distance = distance;
                neighbours[0].id = i;
            }
        }

        // Find most recurrent output
        Map<Character, Integer> occurences = new HashMap<Character, Integer>(k);
        for (Neighbour neighbour : neighbours) {
            Character output = trainingItems[neighbour.id].output;
            occurences.put(output, occurences.containsKey(output) ? occurences.get(output) + 1 : 1);
        }

        int maxOccurences = Integer.MIN_VALUE;
        Character maxOccurencesOutput = null;
        for (Map.Entry<Character, Integer> entry : occurences.entrySet()) {
            if (entry.getValue() > maxOccurences) {
                maxOccurences = entry.getValue();
                maxOccurencesOutput = entry.getKey();
            }
        }
        Character nearestOutput = trainingItems[neighbours[0].id].output;
        if (maxOccurences == occurences.get(nearestOutput)) return nearestOutput;
        else return maxOccurencesOutput;
    }

    public static class UnclosableBufferedInputStream extends BufferedInputStream {

        public UnclosableBufferedInputStream(InputStream in) {
            super(in);
            super.mark(Integer.MAX_VALUE);
        }

        @Override
        public void close() throws IOException {
            super.reset();
        }
    }

    public static Item<Character, Float[]>[] unserialize(InputStream is) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(is);
        Item<Character, Float[]>[] items = (Item<Character, Float[]>[]) in.readObject();
        in.close();
        return items;
    }

    public static void serialize(Item<Character, Float[]>[] items, OutputStream os) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(os);
        out.writeObject(items);
        out.close();
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if (args.length < 3) {
            System.out.println("Usage: KnnClassifier k training_file nbFeatures case_sensitive(true / false)");
            System.exit(1);
        }

        int k = Integer.parseInt(args[0]);

        String inputFilename = args[1];
        InputStream fis = new UnclosableBufferedInputStream(new FileInputStream(inputFilename));

        int nbFeatures = Integer.parseInt(args[2]);

        // If file is serialization file (.ser), read it from there
        Item<Character, Float[]>[] trainingItems;
        if (inputFilename.toLowerCase().endsWith(".ser")) {
            trainingItems = unserialize(fis);
            System.err.println(trainingItems.length + " samples unserialized.");
        } else {
            trainingItems = parse(fis, nbFeatures);
            System.err.println(trainingItems.length + " samples parsed.");
            /*
            String outputFilename = inputFilename.substring(0, inputFilename.lastIndexOf('.')) + ".ser";
            FileOutputStream fos = new FileOutputStream(outputFilename);
            serialize(trainingItems, fos);
            System.err.println("Serialized data is saved in " + outputFilename);
            fos.close();
            */
        }

        // Read features from standard input
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String inputLine;
        while ((inputLine = br.readLine()) != null) {
            Float[] features = new Float[nbFeatures];
            int i = 0;
            Scanner scanner = new Scanner(inputLine);
            while (scanner.hasNextFloat()) {
                features[i++] = scanner.nextFloat();
            }
            System.out.print(findNeighbour(k, trainingItems, features));
        }

        System.out.println();
    }
}
