package ch.epfl.bio410.graph;

import ij.IJ;
import ij.ImagePlus;

import java.util.ArrayList;

/**
 * Class implementing a "Spot" object, with different attributes
 */
public class Spot {
	public int x;
	public int y;
	public int t;
	public double value = 0;
	public double mean = -1;
	public double variance = -1;
	public int radius;

	/**
	 * Constructor of the class = mandatory method to build and initialize the "Spot" object
	 */
	public Spot(int x, int y, int t, double value, int rad) {
		this.x = x;
		this.y = y;
		this.t = t;
		this.radius = rad;
		this.value = value;
	}
	//calculates area statistics
	public void calculate_mean_var(ImagePlus feature_im){

		feature_im.setSlice(this.t + 1);
		ArrayList<Float> values = new ArrayList<>();

		// Collect pixel values inside the circle
		for (int dy = -radius; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				if (dx * dx + dy * dy <= radius * radius) {
					int x = (int) Math.round(this.x + dx);
					int y = (int) Math.round(this.y + dy);

					if (x >= 0 && y >= 0 && x < feature_im.getWidth() && y < feature_im.getHeight()) {
						float val = (float) feature_im.getProcessor().getPixelValue(x, y);
						values.add(val);
					}
				}
			}
		}

		// Calculate mean
		double sum = 0.0;
		for (float v : values) {
			sum += v;
		}
		double mean = sum / values.size();

		// Calculate variance
		double varSum = 0.0;
		for (float v : values) {
			varSum += Math.pow(v - mean, 2);
		}
		double variance = varSum / values.size();
		this.mean = mean;
		this.variance = variance;
		IJ.log("stats Frame t:" + this.t + " variance:" + this.variance+"mean"+this.mean );
	}

	public double distance(Spot spot){
		return Math.sqrt(Math.pow(this.x - spot.x, 2) + Math.pow(this.y - spot.y, 2));
	}
}