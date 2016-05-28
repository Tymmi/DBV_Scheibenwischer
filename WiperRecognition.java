package plugins.DBV_Scheibenwischer;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.filter.Binary;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;

import java.io.File;
import java.io.FileFilter;

/**
 * Created by floric on 5/19/16.
 */
public class WiperRecognition implements PlugIn {

    @Override
    public void run(String arg) {

        FolderOpener folderOp = new FolderOpener();


        File folder = new File("/home/floric/Studium/Digitale Bildverarbeitung/Testdaten");

        int imageCount = 0;

        for (File f : folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isFile() && pathname.getName().endsWith(".png")) {
                    return true;
                } else {
                    return false;
                }
            }
        })) {
            if (imageCount < 5) {
                System.out.println("Analyze: " + f.getAbsolutePath());

                ImagePlus img = IJ.openImage(f.getAbsolutePath());
                equalize(img);

                // make binary and erode
                ImageConverter ic = new ImageConverter(img);
                ic.convertToGray8();
                BinaryProcessor proc = new BinaryProcessor(new ByteProcessor(img.getImage()));
                proc.threshold(8);
                proc.erode();
                proc.erode();
                fill(img.getProcessor(), 255, 0);

                img = new ImagePlus(img.getTitle(), proc);

                img.show();
            }
            imageCount++;
        }

    }

    public void equalize(ImagePlus imp) {
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

    void applyTable(ImageProcessor ip, int[] lut) {
        ip.applyTable(lut);
    }

    void fill(ImageProcessor ip, int foreground, int background) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(127);
        for (int y = 0; y < height; y++) {
            if (ip.getPixel(0, y) == background) ff.fill(0, y);
            if (ip.getPixel(width - 1, y) == background) ff.fill(width - 1, y);
        }
        for (int x = 0; x < width; x++) {
            if (ip.getPixel(x, 0) == background) ff.fill(x, 0);
            if (ip.getPixel(x, height - 1) == background) ff.fill(x, height - 1);
        }
        byte[] pixels = (byte[]) ip.getPixels();
        int n = width * height;
        for (int i = 0; i < n; i++) {
            if (pixels[i] == 127)
                pixels[i] = (byte) background;
            else
                pixels[i] = (byte) foreground;
        }
    }
}
