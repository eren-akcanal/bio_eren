package ch.epfl.bio410;

import ch.epfl.bio410.cost.AbstractCost;
import ch.epfl.bio410.cost.DistanceAndIntensityCost;
import ch.epfl.bio410.cost.SimpleDistanceCost;
import ch.epfl.bio410.graph.PartitionedGraph;
import ch.epfl.bio410.graph.Spot;
import ch.epfl.bio410.graph.Spots;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.plugin.GaussianBlur3D;
import ij.plugin.ImageCalculator;
import ij.process.ImageProcessor;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import ij.IJ;
import ij.ImagePlus;

import java.awt.*;
import java.util.Vector;


/**
 */
@Plugin(type = Command.class, menuPath = "Plugins>BII>Tracking_Particles")
public class Tracking_Bright_Spots implements Command {

	private final double sigma = 2;  // Detection parameters, sigma of the DoG TODO : adapt it
	private final double threshold = 160;  // Detection parameters, threshold of the localmax TODO : adapt it
	private final double distmax = 15; 	// Cost parameters, maximum distance allowed to link spots together TODO : adapt it
	private final double costmax = 0.1;	// Cost parameters, maximum cost allowed to link spots togethers TODO : adapt it
	private final double lambda = 0.8; 	// Cost parameters, hyperparameter to balance cost function terms TODO : adapt it

	@Override
	public void run() {

		// TODO question 6 - create a minimal GUI
		ImagePlus imp = IJ.getImage();
		GenericDialog Gui=new GenericDialog( "track");
		Gui.addImageChoice("choose image","img");
		Gui.addNumericField("sigma",2);
		Gui.addNumericField("threshold",160);
		Gui.addNumericField("distmax",15);
		Gui.addNumericField("costmax",0.1);
		Gui.addNumericField("lambda",0.8);
		Gui.showDialog();

		if (Gui.wasCanceled()) {
			IJ.log("Dialog canceled");
			return;
		}
		// Extract numeric fields
		Vector<?> fields = Gui.getNumericFields();  // raw type warning avoided with `<?>`
		double sigma     = Double.parseDouble(((TextField) fields.get(0)).getText());
		double threshold = Double.parseDouble(((TextField) fields.get(1)).getText());
		double distmax   = Double.parseDouble(((TextField) fields.get(2)).getText());
		double costmax   = Double.parseDouble(((TextField) fields.get(3)).getText());
		double lambda    = Double.parseDouble(((TextField) fields.get(4)).getText());

		Vector<?> choices = Gui.getChoices();  // Vector of Choice objects
		Choice imageChoice = (Choice) choices.get(0);
		String imageTitle = imageChoice.getSelectedItem();
		imp = WindowManager.getImage(imageTitle);

		IJ.log("sigma = " + sigma);
		IJ.log("threshold = " + threshold);
		IJ.log("distmax = " + distmax);
		IJ.log("costmax = " + costmax);
		IJ.log("lambda = " + lambda);

		//ImagePlus dogged=dog_rep(imp,sigma,10);
		//dogged.show();
		// Detection
		int locality = 3;
		PartitionedGraph frames = detect(imp, sigma, threshold,locality);
		ImagePlus imp_copy = imp.duplicate();
		frames.drawSpots(imp);

		frames.calculate_statistics(imp);
		double th_removal = 50;
		double th_var = 1000;
		frames.remove_not_filled_enough(imp_copy,th_removal,th_var); // removing all rn move the computations to detection so that we can see the values
		frames.drawSpots(imp_copy);
		imp_copy.show();

		PartitionedGraph.FrameStats our_stats=frames.get_stat_pframe();
		plotFrameStatsSeparately(our_stats);

	}

	public void plotFrameStatsSeparately(PartitionedGraph.FrameStats stats) {
		double[] frameMeans = stats.frameMeans;
		double[] frameVariances = stats.frameVariances;

		// Generate X-axis: frame indices (0, 1, 2, ...)
		double[] x = new double[frameMeans.length];
		for (int i = 0; i < x.length; i++) x[i] = i;

		// Plot means
		Plot meanPlot = new Plot("Mean Intensities per Frame", "Frame", "Mean Intensity", x, frameMeans);
		meanPlot.setColor(Color.BLUE);
		meanPlot.addPoints(x, frameMeans, Plot.LINE);
		meanPlot.show();

		// Plot variances
		Plot varPlot = new Plot("Mean Variances per Frame", "Frame", "Mean Variance", x, frameVariances);
		varPlot.setColor(Color.RED);
		varPlot.addPoints(x, frameVariances, Plot.LINE);
		varPlot.show();
	}

	/**
	 * This method allows to track single spots across frames.
	 * The algorithm is working by extending the current trajectories by
	 * appending the first valid spot of the next frame.
	 *
	 * @param frames Graph organized by partition of spots belonging to the same frame
	 * @param cost Cost function for the connection of spots
	 * @return Graph organized by partition of spots belonging to the same trajectory
	 */
	private PartitionedGraph trackToFirstValidTrajectory(PartitionedGraph frames, AbstractCost cost) {
		PartitionedGraph trajectories = new PartitionedGraph();
		for (Spots frame : frames) {
			for (Spot spot : frame) {
				Spots trajectory = trajectories.getPartitionOf(spot);
				if (trajectory == null) trajectory = trajectories.createPartition(spot);
				if (spot.equals(trajectory.last())) {
					int t0 = spot.t;
					for (int t=t0; t < frames.size() - 1; t++) {
						for(Spot next : frames.get(t+1)) {
							if (cost.validate(next, spot) == true) {
								IJ.log("#" + trajectories.size() + " spot " + next + " with a cost:" + cost.evaluate(next, spot));
								spot = next;
								trajectory.add(spot);
								break;
							}
						}
					}
				}
			}
		}
		return trajectories;
	}

	/**
	 * This method allows to track single spots across frames.
	 * The algorithm is working by extending the current trajectories by
	 * appending the nearest valid spot of the next frame.
	 *
	 * @param frames Graph organized by partition of spots belonging to the same frame
	 * @param cost Cost function for the connection of spots
	 * @return Graph organized by partition of spots belonging to the same trajectory
	 */
	private PartitionedGraph trackToNearestTrajectory(PartitionedGraph frames, AbstractCost cost) {
		PartitionedGraph trajectories = new PartitionedGraph();
		for (Spots frame : frames) {
			for (Spot spot : frame) {
				Spots trajectory = trajectories.getPartitionOf(spot);
				if (trajectory == null) trajectory = trajectories.createPartition(spot);
				if (spot.equals(trajectory.last())) {
					int t0 = spot.t;
					// TODO question 4 - add trajectory to the nearest spot of the next frame

					for (int t=t0; t < frames.size() - 1; t++) {
						double min_cost=Double.POSITIVE_INFINITY;
						Spot min_spot=null;
						for(Spot next : frames.get(t+1)) {
							if (cost.validate(next, spot)) {
								double current_cost= cost.evaluate(next,spot);
								if (current_cost<min_cost){
									min_cost  = current_cost;
									min_spot  = next;
								}
							}
						}
						if (min_spot!=null) {
							IJ.log("#" + trajectories.size() + " spot " + min_spot + " with a cost:" + cost.evaluate(min_spot, spot));
							spot = min_spot;
							trajectory.add(spot);
						}
					}
				}
			}
		}
		return trajectories;
	}

	/**
	 * TODO question 1 - fill the method description and input/output parameters
	 * this is a wrap up function that uses DoG and local max functions to  detect the points of interest in the image
	 * DoG calculates the features and localmax finds the local maximums in the image
	 * @param imp The imagePlus object that we want to analise
	 * @param sigma input of dog,standard deviation of the gaussian with lower variance, bigger std is calculated as sqrt(2)*sigma
	 * @param threshold threshold for image intensity for it to be local max  in the feature space
	 * @return returns a list of list of detected spots as PartitionedGraph object which contains list of spots objects
	 */
	private PartitionedGraph detect(ImagePlus imp, double sigma, double threshold,int locality) {
		int nt = imp.getNFrames();
		new ImagePlus("DoG", dog(imp.getProcessor(), sigma));
		PartitionedGraph graph = new PartitionedGraph();
		for (int t = 0; t < nt; t++) {
			imp.setPosition(1, 1, 1 + t);
			ImageProcessor ip = imp.getProcessor();
			ImageProcessor dog = dog(ip, sigma);
			Spots spots = localMax(dog, ip, t, threshold,locality);
			IJ.log("Frame t:" + t + " #localmax:" + spots.size() );
			graph.add(spots);
		}
		return graph;
	}

	/**
	 * TODO question 1 - fill the method description and input/output parameters
	 * Calculates the DoG of the image, DoG is a filtering method,
	 * which corresponds to a bandpass filter
	 * by doing gaussian filtering with 2 different variances and subtracting from each other
	 * @param ip image processor of the image get it by imp.getProcessor()
	 * @param sigma standard deviation of the gaussian with lower variance, bigger std is calculated as sqrt(2)*sigma
	 * @return returns the processor, which has the pixel data of processed image
	 */
	private ImageProcessor dog(ImageProcessor ip, double sigma) {
		ImagePlus g1 = new ImagePlus("g1", ip.duplicate());
		ImagePlus g2 = new ImagePlus("g2", ip.duplicate());
		double sigma2 = (Math.sqrt(2) * sigma);
		GaussianBlur3D.blur(g1, sigma, sigma, 0);
		GaussianBlur3D.blur(g2, sigma2, sigma2, 0);
		ImagePlus dog = ImageCalculator.run(g1, g2, "Subtract create stack");
		return dog.getProcessor();
	}

	/**
	 * TODO question 1 - fill the method description and input/output parameters
	 * this method finds the local maximum of dog features on 3x3 windows,
	 * when the corresponding image intensity is higher than given threshold
	 * basically finds the objects of interest in a feature space
	 * @param dog the processor of the features generated by dog function, give the output of the dog function directly
	 * @param image	the processor of the original image
	 * @param t current frame
	 * @param threshold threshold for image to have a max point, pixels having lover value than this will not be accepted even if they are  local max
	 * @return local maximum points, corresponding to bright spots that we want to track in our image
	 */
	public Spots localMax(ImageProcessor dog, ImageProcessor image, int t, double threshold,int locality) {
		Spots spots = new Spots();
		int rad = 3; //ToDo change into global
		for (int x = 1; x < dog.getWidth() - 1; x++) {
			for (int y = 1; y < dog.getHeight() - 1; y++) {
				double valueImage = image.getPixelValue(x, y);
				if (valueImage >= threshold) {
					double v = dog.getPixelValue(x, y);
					double max = -1;
					for (int k = -locality; k <= locality; k++)
						for (int l = -locality; l <= locality; l++)
							max = Math.max(max, dog.getPixelValue(x + k, y + l));
				 	if (v == max) spots.add(new Spot(x, y, t, valueImage,rad));
				}
			}
		}
		return spots;
	}


	private ImagePlus dog_apply(ImagePlus imp, double sigma1){
		double sigma2 = (Math.sqrt(2) * sigma1);
		ImagePlus imp_blur1 = imp.duplicate();
		IJ.run(imp_blur1, "Gaussian Blur...", "sigma="+sigma1+" stack");
		ImagePlus imp_blur2 = imp.duplicate();
		IJ.run(imp_blur2, "Gaussian Blur...", "sigma="+sigma2+" stack");
		ImagePlus dog = ImageCalculator.run(imp_blur1, imp_blur2, "Subtract create 32-bit stack");
		return dog;
	}
	private ImagePlus rectify(ImagePlus imp){
		ImageStack stack = imp.getStack();

		for (int z = 1; z <= stack.getSize(); z++) {
			ImageProcessor ip = stack.getProcessor(z);

			int width = ip.getWidth();
			int height = ip.getHeight();

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					float v = ip.getf(x, y);
					if (v < 0) ip.setf(x, y, 0f); // Clip to 0
				}
			}
		}
		return imp;
	}
	private ImagePlus dog_rep(ImagePlus imp,double sigma,int iter){
		ImagePlus output = imp.duplicate();
		for (int i=0; i<iter;i++){
			ImagePlus filtered = dog_apply(output,sigma);

			ImageCalculator ic = new ImageCalculator();
			ic.run("Add", output, rectify(filtered)); // No "create" -> modifies `target`

		}
		return output;
	}
	/**
	 * This method changes the color such a way that the trajectory color
	 * of the mother and the two daughter cells are the same.
	 *
	 * @param input the partitioned graph where each partition contains the spots per trajectory
	 * @param proximityDivision threshold on the distance between spots above which
	 *                             the two spots are considered too far to have a parent link.
	 * @return the updated partitioned graph with the right colors
	 */
	public PartitionedGraph colorDivision(PartitionedGraph input, double proximityDivision) {
		PartitionedGraph out = new PartitionedGraph();
		for(Spots trajectory : input) {
			/*
				TODO Bonus question - add your code to assign the same color to the mother and daughter cells
			 */
			for(Spots other_traj: input) {
				boolean ifbreak = false;
				if(trajectory.color!=other_traj.color) {
					for (Spot first : trajectory) {
						if(ifbreak){break;}
						for (Spot second : other_traj) {
							if(first.t==second.t) {
								double dist = first.distance(second);
								if (dist < proximityDivision) {
									other_traj.color = trajectory.color;
									ifbreak = true;
									break;
								}
							}
						}
					}
				}
			}

		}
		return input;
	}

	/**
	 * This main function serves for development purposes.
	 * It allows you to run the plugin immediately out of
	 * your integrated development environment (IDE).
	 *
	 * @param args whatever, it's ignored
	 * @throws Exception
	 */
	public static void main(final String... args) throws Exception {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
	}
}