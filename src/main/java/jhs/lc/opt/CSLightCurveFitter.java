package jhs.lc.opt;

import java.util.Random;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MathException;
import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.optimization.RealPointValuePair;
import jhs.lc.data.LightCurvePoint;
import jhs.math.util.MathUtil;

public class CSLightCurveFitter {	
	private final SolutionSampler sampler;
	private final int populationSize;
	
	private double epsilon = 0.01;
	
	private int maxCSIterationsWithClustering = 100;
	private int maxExtraCSIterations = 5;
	private int maxGradientDescentIterations = 10;

	private double expansionFactor = 3.0;
	private double displacementFactor = 0.03;
	private double convergeDistance = 0.0001;
	private double circuitShuffliness = 0.5;
	
	private double lambda = 0.0003;

	public CSLightCurveFitter(SolutionSampler sampler, int populationSize) {
		this.sampler = sampler;
		this.populationSize = populationSize;
	}

	public final double getEpsilon() {
		return epsilon;
	}

	public final void setEpsilon(double epsilon) {
		this.epsilon = epsilon;
	}

	public final int getMaxCSIterationsWithClustering() {
		return maxCSIterationsWithClustering;
	}

	public final void setMaxCSIterationsWithClustering(int maxCSIterationsWithClustering) {
		this.maxCSIterationsWithClustering = maxCSIterationsWithClustering;
	}

	public final int getMaxExtraCSIterations() {
		return maxExtraCSIterations;
	}

	public final void setMaxExtraCSIterations(int maxExtraCSIterations) {
		this.maxExtraCSIterations = maxExtraCSIterations;
	}

	public final int getMaxGradientDescentIterations() {
		return maxGradientDescentIterations;
	}

	public final void setMaxGradientDescentIterations(int maxGradientDescentIterations) {
		this.maxGradientDescentIterations = maxGradientDescentIterations;
	}

	public final double getExpansionFactor() {
		return expansionFactor;
	}

	public final void setExpansionFactor(double expansionFactor) {
		this.expansionFactor = expansionFactor;
	}

	public final double getDisplacementFactor() {
		return displacementFactor;
	}

	public final void setDisplacementFactor(double displacementFactor) {
		this.displacementFactor = displacementFactor;
	}

	public final double getConvergeDistance() {
		return convergeDistance;
	}

	public final void setConvergeDistance(double convergeDistance) {
		this.convergeDistance = convergeDistance;
	}

	public final double getCircuitShuffliness() {
		return circuitShuffliness;
	}

	public final void setCircuitShuffliness(double circuitShuffliness) {
		this.circuitShuffliness = circuitShuffliness;
	}

	public Solution optimize(LightCurvePoint[] lightCurve) throws MathException {
		double[] allFluxes = LightCurvePoint.fluxArray(lightCurve);
		Solution solutionStage1 = this.optimizeStandardErrorCS(allFluxes);
		Solution solutionStage3 = this.optimizeStandardErrorAGD(allFluxes, solutionStage1, this.maxGradientDescentIterations);
		return solutionStage3;
	}

	public Solution optimizeStandardErrorAGD(double[] fluxArray, Solution initialSolution, int maxIterations) throws MathException {
		MultivariateRealFunction errorFunction = LocalErrorFunction.create(this.sampler, fluxArray, this.lambda);
		return this.optimizeAGD(fluxArray, initialSolution, errorFunction, maxIterations);
	}

	public Solution optimizeAGD(double[] fluxArray, Solution initialSolution, MultivariateRealFunction errorFunction, int maxIterations) throws MathException {
		SolutionSampler sampler = this.sampler;
		ApproximateGradientDescentOptimizer optimizer = new ApproximateGradientDescentOptimizer(sampler.getRandom()) {
			@Override
			protected void informProgress(int iteration, RealPointValuePair pointValue) {
				CSLightCurveFitter.this.informProgress("agd", iteration, pointValue.getValue());
			}			
		};
		optimizer.setMaxIterations(maxIterations);
		optimizer.setEpsilon(this.epsilon);
		double[] initialPoint = sampler.solutionAsParameters(initialSolution);
		RealPointValuePair optPoint = optimizer.optimize(errorFunction, initialPoint);
		return sampler.parametersAsSolution(optPoint.getPointRef());
	}

	public Solution optimizeStandardErrorCS(double[] fluxArray) throws MathException {
		MultivariateRealFunction errorFunction = LocalErrorFunction.create(this.sampler, fluxArray, this.lambda);
		return this.optimizeCircuitSearch(errorFunction);
	}

	public Solution optimizeCircuitSearch(MultivariateRealFunction errorFunction) throws MathException {
		SolutionSampler sampler = this.sampler;
		Random random = sampler.getRandom();
		CircuitSearchOptimizer optimizer = new CircuitSearchOptimizer(random, this.populationSize) {
			@Override
			protected void informProgress(int iteration, RealPointValuePair pointValue) {
				CSLightCurveFitter.this.informProgress("circuit search", iteration, pointValue.getValue());
			}
		};
		optimizer.setCircuitShuffliness(this.circuitShuffliness);
		optimizer.setConvergeDistance(this.convergeDistance);
		optimizer.setDisplacementFactor(this.displacementFactor);
		optimizer.setExpansionFactor(this.expansionFactor);
		optimizer.setMaxIterationsWithClustering(this.maxCSIterationsWithClustering);
		optimizer.setMaxTotalIterations(this.maxCSIterationsWithClustering + this.maxExtraCSIterations);
		int vectorLength = sampler.getNumParameters();
		RealPointValuePair result = optimizer.optimize(errorFunction, vectorLength);
		return sampler.parametersAsSolution(result.getPointRef());
	}

	protected void informProgress(String stage, int iteration, double error) {		
	}
	
	public static double meanSquaredError(LightCurvePoint[] lightCurve, double[] weights, Solution solution) {
		double[] fluxArray = LightCurvePoint.fluxArray(lightCurve);
		return meanSquaredError(fluxArray, weights, solution);
	}

	public static double meanSquaredError(double[] fluxArray, double[] weights, Solution solution) {
		double[] testFluxArray = solution.produceModeledFlux();
		int length = fluxArray.length;
		double sum = 0;
		double weightSum = 0;
		for(int i = 0; i < length; i++) {
			double weight = weights[i];
			double diff = testFluxArray[i] - fluxArray[i];
			sum += (diff * diff) * weight;
			weightSum += weight;
		}
		return weightSum == 0 ? 0 : sum / weightSum;
	}
	
	private static class LocalErrorFunction implements MultivariateRealFunction {
		private final SolutionSampler sampler;
		private final double[] fluxArray;
		private final double[] weights;
		private final double lambda;

		public LocalErrorFunction(SolutionSampler sampler, double[] fluxArray, double[] weights, double lambda) {
			super();
			this.sampler = sampler;
			this.fluxArray = fluxArray;
			this.weights = weights;
			this.lambda = lambda;
		}

		public static LocalErrorFunction create(SolutionSampler sampler, double[] fluxArray, double lambda) {
			double[] weights = sampler.createFluxWeights(fluxArray);
			return new LocalErrorFunction(sampler, fluxArray, weights, lambda);
		}
		
		@Override
		public final double value(double[] parameters) throws FunctionEvaluationException, IllegalArgumentException {
			Solution solution = this.sampler.parametersAsSolution(parameters);
			double baseError = meanSquaredError(this.fluxArray, this.weights, solution); 
			double sdParams = MathUtil.standardDev(parameters, 0);
			double diffWithNormal = sdParams - 1.0;			
			return baseError + (diffWithNormal * diffWithNormal * this.lambda);
		}		
	}
}