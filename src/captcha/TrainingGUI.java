package captcha;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mathieu Clément
 * @since 22.12.2013
 */
public class TrainingGUI extends JFrame {
    private final GridLayout gridLayout;
    private final Font symbolAreaFont = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    private final DocumentFilter documentFilter = new DocumentFilter() {
        @Override
        public void insertString(FilterBypass fb, int offset,
                                 String string, AttributeSet attr)
                throws BadLocationException {
            super.insertString(fb, offset, string.toUpperCase(), attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length,
                            String text, AttributeSet attrs)
                throws BadLocationException {
            super.insertString(fb, offset, text.toUpperCase(), attrs);
        }

    };
    JTextArea[] symbolAreas;
    JPanel symbolPanel;
    String inputDirectory;
    JTextField txtField = new JTextField();
    JLabel topLabel = new JLabel();
    int fileIndex = -1;
    private File[] txtFiles;
    private String[] codedFeaturesLines;
    private File currentTxtFile;
    private File outputDir;
    private int nbSymbols;
    private File[] files;

    public static void main(String[] args) {
        new TrainingGUI(args[0]);
    }

    public TrainingGUI(String txtFilesDirectory) throws HeadlessException {
        super("OCR Training");
        this.inputDirectory = txtFilesDirectory;


        this.setSize(1024, 768);
        this.setLocationRelativeTo(null);
        this.setLayout(new BorderLayout());
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);

        this.add(topLabel, BorderLayout.NORTH);

        this.gridLayout = new GridLayout(2, 4);
        this.symbolPanel = new JPanel(gridLayout);
        this.add(symbolPanel, BorderLayout.CENTER);

        this.add(txtField, BorderLayout.SOUTH);
        this.txtField.setHorizontalAlignment(SwingConstants.CENTER);
        this.txtField.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 50));
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

        files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.toLowerCase().endsWith(".txt") &&
                        !makeOutputFile(new File(file.getAbsolutePath() + "/" + s)).exists();
            }
        });
        txtFiles = files;
        Arrays.sort(txtFiles);

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

        this.symbolPanel.removeAll();
        this.symbolPanel.repaint();

        currentTxtFile = txtFiles[fileIndex];

        outputDir = makeOutputDir(this.currentTxtFile);
        this.outputDir.mkdir();

        int bufSize = 0;

        Scanner scanner = new Scanner(currentTxtFile);

        String firstLine = scanner.nextLine();
        Matcher firstLineMatcher = numberSymbolsPattern.matcher(firstLine);
        firstLineMatcher.find();
        nbSymbols = Integer.parseInt(firstLineMatcher.group(1));
        if (nbSymbols == 0) {
            prepareNextFile();
            return;
        }
        this.topLabel.setText(currentTxtFile.getName() + " : " + nbSymbols + " symbols.    " + fileIndex + " / " +
                txtFiles.length + " (" + (100 * fileIndex / txtFiles.length) + " %)");
        String[] drawing = new String[nbSymbols * 100];
        codedFeaturesLines = new String[nbSymbols + 1];
        symbolAreas = new JTextArea[nbSymbols];
        int currentSymbol = 1;

        boolean insideSymbol = false;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (startSymbolPattern.matcher(line).matches()) {
                insideSymbol = true;
                bufSize = 0;
            } else if (stopSymbolPattern.matcher(line).matches()) {
                drawing[bufSize] = "";
                bufSize++;
                insideSymbol = false;

                symbolAreas[currentSymbol - 1] = new JTextArea();
                JTextArea symbolArea = symbolAreas[currentSymbol - 1];
                symbolArea.setFont(symbolAreaFont);
                symbolArea.setBorder(BorderFactory.createLineBorder(Color.GREEN));
                ((AbstractDocument) symbolArea.getDocument()).setDocumentFilter(documentFilter);
                this.symbolPanel.add(symbolArea);
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < bufSize; i++) {
                    stringBuilder.append(drawing[i]);
                    stringBuilder.append("\n");
                }
                symbolArea.setText(stringBuilder.toString());
                symbolArea.repaint();
            } else {
                if (insideSymbol) {
                    drawing[bufSize] = line;
                    bufSize++;
                } else if (line.startsWith("CODED FEATURES")) {
                    codedFeaturesLines[currentSymbol++] = line.substring("CODED FEATURES".length() + 1);
                }
            }
        }

        scanner.close();
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

            this.txtField.setBackground(Color.GREEN);
            this.txtField.repaint();
            Timer timer = new Timer(500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    txtField.setBackground(Color.WHITE);
                    txtField.repaint();
                }
            });
            timer.start();
        } catch (Exception e) {
            e.printStackTrace();
            this.txtField.setBackground(Color.RED);
            this.txtField.repaint();
        }

    }

    private File makeOutputFile(File f) {
        return new File(makeOutputDir(f).getAbsolutePath() + "/" + f.getName());
    }
}
