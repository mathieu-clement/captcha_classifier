package captcha;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * @author Mathieu Clément
 * @since 08.12.2013
 */
public class GetSamples {
    private void leftClick(Robot robot) {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private Semaphore mouse = new Semaphore(1, true);

    public void run(int nbSamples, String nameFormat, int startIndex, File outputDir,
                    int windowOffsetX, int windowOffsetY) throws AWTException,
            IOException, InterruptedException {
        Robot robot = new Robot();
//        mouse.acquire();
//        robot.mouseMove(windowOffsetX + 1200, windowOffsetY + 1060);
//        leftClick(robot);
//        mouse.release();

        for (int i = startIndex; i < startIndex + nbSamples; i++) {
            //System.out.println("" + (i - startIndex) + '/' + nbSamples);
            System.out.println(i + "/" + (startIndex + nbSamples));

            // top left (144, 287)
            // bottom right (345, 337)
            // Rectangle size: 201x50
            BufferedImage capture = robot.createScreenCapture(
                    new Rectangle(windowOffsetX + 144, windowOffsetY + 287, 195, 50));
            ImageIO.write(capture, "png",
                    new File(String.format("%s/" + nameFormat, outputDir.getAbsolutePath(), i)));

            // Click refresh
            mouse.acquire();
            robot.mouseMove(windowOffsetX + 75, windowOffsetY + 69);
            leftClick(robot);
            mouse.release();
            robot.delay(500);

            // Wait favicon
            while (robot.getPixelColor(windowOffsetX + 378, windowOffsetY + 42).getGreen() < 100) ;
            robot.delay(2000);
        }
    }

    private static class GetSamplesRunnable implements Runnable {

        private GetSamples inst;
        private int nbSamples, startIndex, windowOffsetX, windowOffsetY;
        private File outputDir;
        private String nameFormat;

        private GetSamplesRunnable(GetSamples inst, int nbSamples, String nameFormat, int startIndex, File outputDir,
                                   int windowOffsetX, int windowOffsetY) {
            this.inst = inst;
            this.nbSamples = nbSamples;
            this.nameFormat = nameFormat;
            this.startIndex = startIndex;
            this.outputDir = outputDir;
            this.windowOffsetX = windowOffsetX;
            this.windowOffsetY = windowOffsetY;
        }

        @Override
        public void run() {
            try {
                inst.run(nbSamples, nameFormat, startIndex, outputDir, windowOffsetX, windowOffsetY);
            } catch (AWTException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        GetSamples inst = new GetSamples();
        Thread[] threads = new Thread[2];
        threads[0] = new Thread(new GetSamplesRunnable(inst, 500, "sample-%04d.png", 1000,
                new File("/home/mathieu/work/decode_captcha/viacar/samples/java/"),
                0, 0));
        threads[1] = new Thread(new GetSamplesRunnable(inst, 500, "sample-%04d.png", 1500,
                new File("/home/mathieu/work/decode_captcha/viacar/samples/java/"),
                0, 465));

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }
}