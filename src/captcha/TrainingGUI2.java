package captcha;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mathieu Clément
 * @since 22.12.2013
 */
public class TrainingGUI2 extends JFrame {
    JTextArea symbolArea = new JTextArea();
    String inputDirectory;
    JTextField txtField = new JTextField();
    JLabel topLabel = new JLabel();
    int fileIndex = -1;
    private File[] txtFiles;
    private String[] codedFeaturesLines;
    private File currentTxtFile;
    private File outputDir;
    private int nbSymbols;

    public static void main(String[] args) {
        new TrainingGUI2(args[0]);
    }

    public TrainingGUI2(String txtFilesDirectory) throws HeadlessException {
        super("OCR Training");
        this.inputDirectory = txtFilesDirectory;


        this.setSize(1024, 768);
        this.setLocationRelativeTo(null);
        this.setLayout(new BorderLayout());
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);

        this.add(topLabel, BorderLayout.NORTH);
        this.add(symbolArea, BorderLayout.CENTER);
        this.symbolArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        this.add(txtField, BorderLayout.SOUTH);
        this.txtField.setHorizontalAlignment(SwingConstants.CENTER);
        this.txtField.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        this.txtField.requestFocusInWindow();
        this.txtField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                saveCurrentFile();
                try {
                    txtField.setText("");
                    prepareNextFile();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });

        launch();
    }

    void launch() {
        File dir = new File(this.inputDirectory);

        txtFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.toLowerCase().endsWith(".txt") &&
                        !makeOutputFile(file).exists();
            }
        });

        try {
            prepareNextFile();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        this.setVisible(true);
    }

    public static final Pattern startGlobalPattern = Pattern.compile("START GLOBAL DRAWING");

    public static final Pattern stopGlobalPattern = Pattern.compile("STOP GLOBAL DRAWING");
    public static final Pattern startSymbolPattern = Pattern.compile("START SYMBOL (\\d+)");
    public static final Pattern stopSymbolPattern = Pattern.compile("STOP SYMBOL (\\d+)");
    public static final Pattern numberSymbolsPattern = Pattern.compile("Number of symbols: (\\d+)");

    void prepareNextFile() throws FileNotFoundException {
        fileIndex++;

        currentTxtFile = txtFiles[fileIndex];
        this.topLabel.setText(currentTxtFile.getName());
        outputDir = makeOutputDir(this.currentTxtFile);
        this.outputDir.mkdir();

        String[] drawing = new String[200];
        int bufSize = 0;

        Scanner scanner = new Scanner(currentTxtFile);
        boolean insideGlobalDrawing = false;
        boolean insideSymbol = false;
        boolean globalDrawingSeen = false;

        String firstLine = scanner.nextLine();
        Matcher firstLineMatcher = numberSymbolsPattern.matcher(firstLine);
        firstLineMatcher.find();
        nbSymbols = Integer.parseInt(firstLineMatcher.group(1));
        codedFeaturesLines = new String[nbSymbols + 1];
        int currentSymbol = 1;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (!globalDrawingSeen) {
                if (startGlobalPattern.matcher(line).matches()) {
                    insideGlobalDrawing = true;
                    globalDrawingSeen = true;
                }
            } else if (insideGlobalDrawing) {
                if (stopGlobalPattern.matcher(line).matches()) {
                    insideGlobalDrawing = false;
                    continue;
                }

                drawing[bufSize] = line;
                bufSize++;
            } else {
                if (line.startsWith("CODED FEATURES")) {
                    codedFeaturesLines[currentSymbol++] = line.substring("CODED FEATURES".length() + 1);
                }
            }
        }
        scanner.close();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bufSize; i++) {
            sb.append(drawing[i]);
            sb.append('\n');
        }
        this.symbolArea.setText(sb.toString());
        this.symbolArea.repaint();
    }

    private File makeOutputDir(File f) {
        return new File(f.getParentFile().getAbsolutePath() + "/output/");
    }


    private void saveCurrentFile() {
        String text = this.txtField.getText();

        File outputFile = makeOutputFile(this.currentTxtFile);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            writer.append("Input file : " + currentTxtFile.getAbsolutePath());
            writer.append("\n");
            writer.append("Nb symbols: " + nbSymbols);
            writer.append("\n");
            for (int i = 1; i < nbSymbols + 1; i++) {
                writer.append("Symbol " + i + " : " + text.charAt(i - 1) + " (" + (int) text.charAt(i - 1) + ")");
                writer.append("\n");
                writer.append("Features " + i + " : " + codedFeaturesLines[i]);
                writer.append("\n");
                writer.append("\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File makeOutputFile(File f) {
        return new File(makeOutputDir(f).getAbsolutePath() + "/" + f.getName());
    }
}
