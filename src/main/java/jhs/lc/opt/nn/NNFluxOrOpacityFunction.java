package jhs.lc.opt.nn;

import java.awt.geom.Rectangle2D;

import jhs.lc.geom.FluxOrOpacityFunction;
import jhs.math.classification.ClassificationUtil;
import jhs.math.nn.NeuralNetwork;

public final class NNFluxOrOpacityFunction implements FluxOrOpacityFunction {
	private static final long serialVersionUID = 1L;
	private static final double SF = 3.46;
	private static final double SQ_MEAN = 1.0;
	private static final double SQ_SD = Math.sqrt(2.0);
	
	private final NeuralNetwork[] neuralNetworks;
	private final InputType inputDef;
	private final OutputType outputType;
	private final double scale;
	private final Rectangle2D boundingBox;
	private final double outputBias;
	
	public NNFluxOrOpacityFunction(NeuralNetwork[] neuralNetworks, InputType inputDef, OutputType outputType,
			double scale, Rectangle2D boundingBox, double outputBias) {
		this.neuralNetworks = neuralNetworks;
		this.inputDef = inputDef;
		this.outputType = outputType;
		this.boundingBox = boundingBox;
		this.scale = scale;
		this.outputBias = outputBias;
	}
	
	public static NNFluxOrOpacityFunction create(NeuralNetwork[] neuralNetworks, double outputBias, InputType inputDef, OutputType outputType,
			double imageWidth, double imageHeight) {
		Rectangle2D boundingBox = new Rectangle2D.Double(-imageWidth / 2.0, -imageHeight / 2.0, imageWidth, imageHeight);
		double dim = Math.sqrt((imageWidth * imageWidth + imageHeight * imageHeight) / 2);
		double scale = SF / dim;
		return new NNFluxOrOpacityFunction(neuralNetworks, inputDef, outputType, scale, boundingBox, outputBias);
	}

	@Override
	public final double fluxOrOpacity(double x, double y, double z) {
		double scale = this.scale;
		double scaledX = x * scale;
		double scaledY = y * scale;
		double a = this.maxActivation(getInputData(scaledX, scaledY, this.inputDef)) + this.outputBias;
		switch(this.outputType) {
		case BINARY:
			return a >= 0 ? 0 : Double.NaN;
		case OPACITY: {
			double p = (a - 1) / 2;
			if(p < -1) {
				p = -1;
			}
			else if(p > 0) {
				p = 0;
			}
			return p;
		}
		case OPACITY_LOGISTIC: {
			double p = ClassificationUtil.logitToProbability(a);
			return -p;			
		}
		case BRIGHTNESS: {
			double p = ClassificationUtil.logitToProbability(a);
			return p;
		}
		case ALL: {
			double p = ClassificationUtil.logitToProbability(a);
			return p * 2.0 - 1.0;
		}
		default: 
			throw new IllegalStateException("Unknown outputType: " + this.outputType);
		}
	}
	
	private double maxActivation(double[] inputData) {
		double max = Double.NEGATIVE_INFINITY;
		for(NeuralNetwork nn : this.neuralNetworks) {
			double[] aa = nn.activations(inputData);
			double a = aa[0];
			if(a > max) {
				max = a;
			}
		}
		return max;
	}

	@Override
	public final Rectangle2D getBoundingBox() {
		return this.boundingBox;
	}

	public static final double[] getInputData(double x, double y, InputType inputDef) {
		switch(inputDef) {
		case PLAIN: {
			return new double[] { x, y };								
		}
		case QUADRATIC: {
			return new double[] { x, y, (x * x - SQ_MEAN) / SQ_SD,  (y * y - SQ_MEAN) / SQ_SD };				
		}
		case QUADRATIC_WP: {
			return new double[] { x, y, (x * x - SQ_MEAN) / SQ_SD,  (y * y - SQ_MEAN) / SQ_SD, x * y };				
		}
		default:
			throw new IllegalStateException(String.valueOf(inputDef));
		}
	}
	
	public static int getNumInputs(InputType inputType) {
		switch(inputType) {
		case PLAIN:
			return 2;
		case QUADRATIC:
			return 4;
		case QUADRATIC_WP:
			return 5;
		default:
			throw new IllegalStateException(String.valueOf(inputType));
		}
	}
}