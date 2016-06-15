package plugins.DBV_Scheibenwischer;


import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.*;

import java.io.File;
import java.io.FileFilter;

import java.awt.Font;
import java.awt.Color;


/**
 * Created by floric on 5/19/16.
 */
public class WiperRecognition implements PlugIn {

    private static final String FOLDER_PATH = "/Users/Tim/Documents/Studium/Leipzig Master/SS 16/Bildverarbeitung/Testdaten";
    private static final int BINARIZE_THRESHOLD = 12;
    private static final int ERODE_PASSES = 12;
    private static final int DILATE_PASSES = 8;
    private static final float DETECT_MIN_VAL = 0.025f;
    private static final float DETECT_MAX_VAL = 0.17f;

    @Override
    public void run(String arg) {
        File folder = new File(FOLDER_PATH);

        // check for folder existence
        if (!folder.exists()) {
            System.out.println("Folder doesn't exist!");
            return;
        }

        // filter folder
        FileFilter imageFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".png");
            }
        };

        int wiperImagesCount = 0;

        // go through images
        for (File f : folder.listFiles(imageFilter)) {
            System.out.println("Analyze: " + f.getAbsolutePath());

            ImagePlus img = IJ.openImage(f.getAbsolutePath());

            // equalize image
            equalize(img);


            // convert image to 8bit gray values
            ImageConverter ic = new ImageConverter(img);
            ic.convertToGray8();

            // convert image to binary image based on threshold
            BinaryProcessor proc = new BinaryProcessor(new ByteProcessor(img.getImage()));
            proc.threshold(BINARIZE_THRESHOLD);

            // erode image
            for (int passIndex = 0; passIndex < ERODE_PASSES; passIndex++) {
                proc.erode();
            }

            // dilate image
            for (int passIndex = 0; passIndex < DILATE_PASSES; passIndex++) {
                proc.dilate();
            }

            // fill holes
            fill(proc);

            // detectWiper wiper in binary image
            boolean foundWiper = detectWiper(proc);

            if (foundWiper) {

                showResult(img);  
                img.show();
                wiperImagesCount++;
            }

        }

        System.out.println("Found: " + wiperImagesCount + " images!");
    }
             
private static void showResult(ImagePlus imp) {

        ImageProcessor ip = imp.getChannelProcessor();
        int fontSize = ip.getWidth() / 20; 
        int smallFontSize = fontSize/2;
        // Postion in 2 quarter of the image
        int horizontalPosition = ip.getWidth()/16*10;
        int verticalPosition = ip.getHeight() / 16 * 2;
                                
        Font fontii = new Font("Arial", Font.PLAIN, smallFontSize); 
        ip.setFont(fontii);
        ip.setColor(Color.RED);
        String str1 = "Scheibenwischer detektiert" ;
        ip.drawString(str1,  horizontalPosition, verticalPosition + smallFontSize);

            imp.show();
    }


    private boolean detectWiper(BinaryProcessor ip) {
        long totalImageSize = ip.getWidth() * ip.getHeight();

        for (int x = 0; x < ip.getWidth(); x++) {
            for (int y = 0; y < ip.getHeight(); y++) {
                if (ip.get(x, y) == 0) {
                    long islandsArea = countBlackPixelsAround(ip, x, y);
                    float relativeSize = (float) islandsArea / totalImageSize;

                    if (relativeSize > DETECT_MIN_VAL && relativeSize < DETECT_MAX_VAL) {
                        System.out.println("Wiper detected: " + relativeSize);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private long countBlackPixelsAround(BinaryProcessor ip, int x, int y) {
        long sum = 0;

        if (x >= ip.getWidth() || y >= ip.getHeight() || x < 0 || y < 0 || ip.get(x, y) != 0) {
            return sum;
        } else {
            ip.set(x, y, 255);

            sum += 1;
            sum += countBlackPixelsAround(ip, x + 1, y);
            sum += countBlackPixelsAround(ip, x + 1, y + 1);
            sum += countBlackPixelsAround(ip, x + 1, y - 1);
            sum += countBlackPixelsAround(ip, x, y + 1);
        }

        return sum;
    }

    private void equalize(ImagePlus imp) {
        if (imp.getBitDepth() == 32) {
            IJ.showMessage("Contrast Enhancer", "Equalization of 32-bit images not supported.");
            return;
        }

        ImageProcessor ip = imp.getProcessor();

        int[] histogram = ip.getHistogram();

        equalize(ip, histogram);
        imp.getProcessor().resetMinAndMax();
    }

    private void equalize(ImageProcessor ip, int[] histogram) {
        ip.resetRoi();
        int range = 0;
        int max = 0;

        if (ip instanceof ShortProcessor) { // Short
            max = 65535;
            range = 65535;
        } else { //bytes
            max = 255;
            range = 255;
        }
        double sum;
        sum = getWeightedValue(histogram, 0);
        for (int i = 1; i < max; i++)
            sum += 2 * getWeightedValue(histogram, i);
        sum += getWeightedValue(histogram, max);
        double scale = range / sum;
        int[] lut = new int[range + 1];
        lut[0] = 0;
        sum = getWeightedValue(histogram, 0);
        for (int i = 1; i < max; i++) {
            double delta = getWeightedValue(histogram, i);
            sum += delta;
            lut[i] = (int) Math.round(sum * scale);
            sum += delta;
        }
        lut[max] = max;
        applyTable(ip, lut);
    }

    private double getWeightedValue(int[] histogram, int i) {
        int h = histogram[i];
        if (h < 2) return (double) h;
        return Math.sqrt((double) (h));
    }

    private void applyTable(ImageProcessor ip, int[] lut) {
        ip.applyTable(lut);
    }

    private void fill(ImageProcessor ip) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(127);
        for (int y = 0; y < height; y++) {
            if (ip.getPixel(0, y) == 255) ff.fill(0, y);
            if (ip.getPixel(width - 1, y) == 255) ff.fill(width - 1, y);
        }
        for (int x = 0; x < width; x++) {
            if (ip.getPixel(x, 0) == 255) ff.fill(x, 0);
            if (ip.getPixel(x, height - 1) == 255) ff.fill(x, height - 1);
        }
        byte[] pixels = (byte[]) ip.getPixels();
        int n = width * height;
        for (int i = 0; i < n; i++) {
            if (pixels[i] == 127)
                pixels[i] = (byte) 255;
            else
                pixels[i] = (byte) 0;
        }
    }
}
