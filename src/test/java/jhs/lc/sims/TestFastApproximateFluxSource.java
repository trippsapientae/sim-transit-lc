package jhs.lc.sims;

import static org.junit.Assert.*;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.junit.Test;

import jhs.lc.geom.EvaluatableSurfaceSphereFactory;
import jhs.lc.geom.FluxOrOpacityFunction;
import jhs.lc.geom.LimbDarkeningParams;
import jhs.lc.geom.RotationAngleSphereFactory;
import jhs.lc.tools.inputs.OptSpec;
import jhs.math.util.MathUtil;

public class TestFastApproximateFluxSource {
	@Test
	public void testFluxArrayMatchesAngularOne() {
		// Note: It doesn't work with positive flux values, because the viewport is only 2x2 in the angular flux source.
		
		double discRadius = 0.5;
		FluxOrOpacityFunction brightnessSource = new FluxOrOpacityFunction() {			
			private static final long serialVersionUID = 1L;

			@Override
			public Rectangle2D getBoundingBox() {
				return new Rectangle2D.Double(-1.5, -1.0, 3.0, 2.0);
				//return new Rectangle2D.Double(-2.0, -2.0, 4.0, 4.0);
				//return new Rectangle2D.Double(-1.5, -0.7, 3.0, 1.4);
				//return new Rectangle2D.Double(-discRadius, -discRadius, discRadius * 2.03, discRadius * 2.05);
			}
			
			@Override
			public double fluxOrOpacity(double x, double y, double z) {
				double d = Math.sqrt(x * x + y * y);
				if(d >= discRadius) {
					return Double.NaN;
				}
				if(x >= 0) {
					return -0.5;
				}
				else {
					return 0;
				}
			}
		};
		double orbitRadius = 200.0;
		double orbitalPeriod = 200.0;
		double viewportAngle = Math.atan(1.0 / orbitRadius) * 2;
		double timeSpan = orbitalPeriod * viewportAngle / Math.PI;
		System.out.println("viewportAngle: " + viewportAngle);
		System.out.println("timeSpan: " + timeSpan);
		double startTimestamp = -(timeSpan / 2);
		double endTimestamp = +(timeSpan / 2);
		double[] timestamps = AngularSimulation.timestamps(startTimestamp, endTimestamp, 51);

		SimulatedFluxSource angularFluxSource = this.getFluxSource(true, timestamps, orbitalPeriod);
		double[] flux1 = angularFluxSource.produceModeledFlux(brightnessSource, orbitRadius);
		double minFlux1 = MathUtil.min(flux1);
		System.out.println("MinFlux1: "+ minFlux1);
		SimulatedFluxSource fastFluxSource = this.getFluxSource(false, timestamps, orbitalPeriod);
		assertTrue(fastFluxSource instanceof FastApproximateFluxSource);
		double[] flux2 = fastFluxSource.produceModeledFlux(brightnessSource, orbitRadius);
		double minFlux2 = MathUtil.min(flux2);
		System.out.println("MinFlux2: "+ minFlux2);
		assertArrayEquals(flux1, flux2, 0.003);
	}	

	private SimulatedFluxSource getFluxSource(boolean angular, double[] timestamps, double orbitalPeriod) {
		LimbDarkeningParams ldParams = new LimbDarkeningParams(0.90, -0.2, 0.1);
		double inclineAngle = 0.002;
		int widthPixels = 150;
		int heightPixels = 150;
		double peakFraction = 0.5;
		if(angular) {
			return new AngularFluxSource(timestamps, peakFraction, widthPixels, heightPixels, inclineAngle, orbitalPeriod, ldParams);
		}
		else {
			try {
				return new FastApproximateFluxSource(timestamps, ldParams, inclineAngle, orbitalPeriod, peakFraction, widthPixels, heightPixels);
			} catch(AngleUnsupportedException au) {
				throw new IllegalStateException("Cannot handle a rotation of " + au.getValue() + " radians with 'fast' optimization. Use -angular option instead.");
			}
		}
	}
}