package ch.epfl.bio410.graph;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;

import java.util.ArrayList;


/**
 * Class implementing a "PartitionedGraph" object. A "PartitionedGraph" object is a list of "Spots" objects,
 * with additional methods to draw the tracking graph of particles.
 */
public class PartitionedGraph extends ArrayList<Spots> {

    public Spots getPartitionOf(Spot spot) {
        for (Spots spots : this) {
            for (Spot test : spots) {
                if (spot.equals(test))
                    return spots;
            }
        }
        return null;
    }

    public Spots createPartition(Spot spot) {
        Spots spots = new Spots();
        spots.add(spot);
        add(spots);
        return spots;
    }

    public Overlay drawSpots(ImagePlus imp) {
        Overlay overlay = imp.getOverlay();
        if (overlay == null) overlay = new Overlay();
        int radius = 5; //ToDo make radius global
        for(Spots spots : this) {
            for(Spot spot : spots) {
                double xp = spot.x + 0.5 - radius;
                double yp = spot.y + 0.5 - radius;
                OvalRoi roi = new OvalRoi(xp, yp, 2 * radius, 2 * radius);
                roi.setPosition(spot.t + 1); // display roi in one frqme
                roi.setStrokeColor(spots.color);
                roi.setStrokeWidth(1);
                overlay.add(roi);
            }
        }
        ImagePlus out = imp.duplicate();
        out.setTitle("Spots " + imp.getTitle() );
        out.show();
        out.getProcessor().resetMinAndMax();
        out.setOverlay(overlay);
        return overlay;
    }


    public Overlay drawLines(ImagePlus imp) {
        Overlay overlay = imp.getOverlay();
        if (overlay == null) overlay = new Overlay();
        int radius = 3;
        for (Spots spots : this) {
            if (spots.isEmpty()) break;

            for (int i = 1; i < spots.size(); i++) {
                Spot spot = spots.get(i);
                Spot prev = spots.get(i - 1);
                Line line = new Line(prev.x + 0.5, prev.y + 0.5, spot.x + 0.5, spot.y + 0.5);
                line.setStrokeColor(spots.color);
                line.setStrokeWidth(2);
                overlay.add(line);

                OvalRoi roi1 = new OvalRoi(spot.x + 0.5 - radius, spot.y + 0.5 - radius, 2 * radius, 2 * radius);
                roi1.setPosition(spot.t + 1); // display roi in one frame
                roi1.setFillColor(spots.color);
                roi1.setStrokeWidth(1);
                overlay.add(roi1);

                OvalRoi roi2 = new OvalRoi(prev.x + 0.5 - radius, prev.y + 0.5 - radius, 2 * radius, 2 * radius);
                roi2.setPosition(prev.t + 1); // display roi in one frame
                roi2.setFillColor(spots.color);
                roi2.setStrokeWidth(1);
                overlay.add(roi2);
            }
        }
        ImagePlus out = imp.duplicate();
        out.setTitle("Trajectories " + imp.getTitle() );
        out.show();
        out.getProcessor().resetMinAndMax();
        out.setOverlay(overlay);
        return overlay;
    }
    public void calculate_statistics(ImagePlus feature_im){
        for (int i = this.size() - 1; i >= 0; i--) {
            Spots spots = this.get(i);
            for (Spot spot : spots) {
                spot.calculate_mean_var(feature_im);
            }
        }

    }

    public class FrameStats {
        public double[] frameMeans;
        public double[] frameVariances;
        public int[] counts;

        public FrameStats(double[] frameMeans, double[] frameVariances,int[] counts) {
            this.frameMeans = frameMeans;
            this.frameVariances = frameVariances;
            this.counts = counts;
        }
    }

    //calculates the mean intensities per frame
    public FrameStats get_stat_pframe(){

        int no_frames = this.size();
        double[] sumMeans = new double[no_frames + 1];
        double[] sumVariances = new double[no_frames + 1];
        int[] count = new int[no_frames + 1];

        // Accumulate mean and variance per frame
        for (Spots spots : this) {
            for (Spot spot : spots) {
                int frame = spot.t;
                sumMeans[frame] += spot.mean;
                sumVariances[frame] += spot.variance;
                count[frame]++;
            }
        }

        // Compute averages
        double[] frameMeans = new double[no_frames + 1];
        double[] frameVariances = new double[no_frames + 1];

        for (int i = 0; i <= no_frames; i++) {
            if (count[i] > 0) {
                frameMeans[i] = sumMeans[i] / count[i];
                frameVariances[i] = sumVariances[i] / count[i];
            } else {
                frameMeans[i] = Double.NaN;
                frameVariances[i] = Double.NaN;
            }
        }

        return new FrameStats(frameMeans, frameVariances,count);


    }

    public void remove_not_filled_enough(ImagePlus feature_im, double intensityThreshold, double varianceThreshold) {
        for (int i = this.size() - 1; i >= 0; i--) {
            Spots spots = this.get(i);

            for (int j = spots.size() - 1; j >= 0; j--) {
                Spot spot = spots.get(j);

                // Remove if below mean threshold or above variance threshold
                if (spot.mean < intensityThreshold || spot.variance > varianceThreshold) {
                    spots.remove(j);
                }
            }
        }
    }


    public void merge_too_close(ImagePlus feature_im){

        return;
    }

}

