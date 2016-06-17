import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.process.*;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by floric on 5/19/16.
 */
public class Wiper_Recognition implements PlugIn {

    private static String FILE_INPUT_PATH = "";
    private static String FILE_OUTPUT_PATH = "";
    private static int BINARIZE_THRESHOLD = 12;
    private static int ERODE_PASSES = 12;
    private static int DILATE_PASSES = 8;
    private static float DETECT_MIN_VAL = 0.025f;
    private static float DETECT_MAX_VAL = 0.17f;

    @Override
    public void run(String arg) {

        readParameters();

        if (DETECT_MIN_VAL > DETECT_MAX_VAL) {
            System.out.println("Invalid detection value borders!");
            return;
        }

        if (ERODE_PASSES < 1 || DILATE_PASSES < 1) {
            System.out.println("Passes count invalid!");
            return;
        }

        // check file existence
        File f = new File(FILE_INPUT_PATH);
        if (!f.exists()) {
            System.out.println("File not found: " + f.getAbsolutePath());
            return;
        }

        ImagePlus img = IJ.openImage(f.getPath());
        ImagePlus img2 = new Duplicator().run(img);

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
            System.out.println("Wiper found");
            showResult(img2);
        } else {
            System.out.println("NO Wiper found");
            img2.show();
        }

        if(FILE_OUTPUT_PATH.isEmpty()){

        //extract input name
        String fname = f.getName();
        int pos = fname.lastIndexOf(".");
        if (pos > 0) {
            fname = fname.substring(0, pos);
        }       
        //extract file path
        int pos2 = FILE_INPUT_PATH.lastIndexOf("/");
        if (pos2 > 0) {
            String dir = FILE_INPUT_PATH.substring(0, pos2+1);
        }
        //standard output file xxx_wiper.png
        FILE_OUTPUT_PATH = dir + fname + "_wiper.png";
        }

        // write output file
        IJ.save(img2, FILE_OUTPUT_PATH);

        // close app
        IJ.run("Quit");
    }

    private void readParameters() {
        String arg;// use macro options or use UI
        if (Macro.getOptions() != null) {
            arg = Macro.getOptions();

            Map<String, String> arguments = new HashMap<String, String>();

            String[] args = arg.split("-");
            System.out.println("Arguments found: " + args.length);
            for (String s : args) {
                String[] parts = s.split(" ");
                if (parts.length != 2) {
                    System.out.println("Skip: " + s);
                    continue;
                }

                String command = parts[0];
                String value = parts[1];

                arguments.put(command, value);
            }

            if (arguments.containsKey("i")) {
                FILE_INPUT_PATH = arguments.get("i");
            }
            if (arguments.containsKey("o")) {
                FILE_OUTPUT_PATH = arguments.get("o");
            }
            if (arguments.containsKey("b")) {
                BINARIZE_THRESHOLD = Integer.parseInt(arguments.get("b"));
            }
            if (arguments.containsKey("ep")) {
                ERODE_PASSES = Integer.parseInt(arguments.get("ep"));
            }
            if (arguments.containsKey("dp")) {
                DILATE_PASSES = Integer.parseInt(arguments.get("dp"));
            }
            if (arguments.containsKey("dmin")) {
                DETECT_MIN_VAL = Float.parseFloat(arguments.get("dmin"));
            }
            if (arguments.containsKey("dmax")) {
                DETECT_MAX_VAL = Float.parseFloat(arguments.get("dmax"));
            }
        } else {
            GenericDialog gd = new GenericDialog("Wiper recognition");
            gd.addStringField("File path", FILE_INPUT_PATH);
            gd.addNumericField("Binarize Threshold", BINARIZE_THRESHOLD, 0);
            gd.addNumericField("Erode Passes", ERODE_PASSES, 0);
            gd.addNumericField("Dilate Passes", DILATE_PASSES, 0);
            gd.addNumericField("Detection Relative Area Min", DETECT_MIN_VAL, 3);
            gd.addNumericField("Detection Relative Area Max", DETECT_MAX_VAL, 3);
            gd.showDialog();

            if (gd.wasCanceled()) return;

            FILE_INPUT_PATH = gd.getNextString();
            BINARIZE_THRESHOLD = (int) gd.getNextNumber();
            ERODE_PASSES = (int) gd.getNextNumber();
            DILATE_PASSES = (int) gd.getNextNumber();
            DETECT_MIN_VAL = (float) gd.getNextNumber();
            DETECT_MAX_VAL = (float) gd.getNextNumber();
        }

    }

    private void showResult(ImagePlus img) {
        ImageProcessor ip = img.getChannelProcessor();
        int fontSize = ip.getWidth() / 20;
        int smallFontSize = fontSize / 2;

        // Position in second quarter of the image
        int horizontalPosition = ip.getWidth() / 160;
        int verticalPosition = ip.getHeight() / 32;

        Font font = new Font("Arial", Font.PLAIN, smallFontSize);
        ip.setFont(font);
        ip.setColor(Color.RED);
        String outputStr = "Scheibenwischer detektiert";
        ip.drawString(outputStr, horizontalPosition, verticalPosition + smallFontSize);

        img.show();
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
