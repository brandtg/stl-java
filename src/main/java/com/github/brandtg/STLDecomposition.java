/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.brandtg;

import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of STL: A Seasonal-Trend Decomposition Procedure based on Loess.
 *
 * <p>
 *   Robert B. Cleveland et al., "STL: A Seasonal-Trend Decomposition Procedure based on Loess,"
 *   in Journal of Official Statistics Vol. 6 No. 1, 1990, pp. 3-73
 * </p>
 *
 * @author Greg Brandt
 * @author Jieying Chen
 * @author James Hong
 */
public class STLDecomposition {
  private static final int LOESS_ROBUSTNESS_ITERATIONS = 4; // same as R implementation

  private final STLConfig config;

  /**
   * Constructs a configuration of STL function that can de-trend data.
   *
   * <p>
   *   n.b. The Java Loess implementation only does  linear local polynomial
   *   regression, but R supports linear (degree=1), quadratic (degree=2), and
   *   a strange degree=0 option.
   * </p>
   *
   * <p>
   *   Also, the Java Loess implementation accepts "bandwidth", the fraction of source points closest
   *   to the current point, as opposed to integral values.
   * </p>
   */
  public STLDecomposition(STLConfig config) {
    config.check();
    this.config = config;
  }

  public STLResult decompose(long[] times, double[] series) {
    double[] trend = new double[series.length];
    double[] seasonal = new double[series.length];
    double[] remainder = new double[series.length];
    double[] robustness = null;

    for (int l = 0; l < config.getNumberOfRobustnessIterations(); l++) {
      for (int k = 0; k < config.getNumberOfInnerLoopPasses(); k++) {
        // Step 1: Detrending
        double[] detrend = new double[series.length];
        for (int i = 0; i < series.length; i++) {
          detrend[i] = series[i] - trend[i];
        }

        // Get cycle sub-series with padding on either side
        int numberOfObservations = config.getNumberOfObservations();
        CycleSubSeries cycle = new CycleSubSeries(times, series, robustness, detrend, numberOfObservations);
        cycle.compute();
        List<double[]> cycleSubseries = cycle.getCycleSubSeries();
        List<double[]> cycleTimes = cycle.getCycleTimes();
        List<double[]> cycleRobustnessWeights = cycle.getCycleRobustnessWeights();

        // Step 2: Cycle-subseries Smoothing
        for (int i = 0; i < cycleSubseries.size(); i++) {
          double[] smoothed = loessSmooth(
              cycleTimes.get(i),
              cycleSubseries.get(i),
              config.getSeasonalComponentBandwidth(),
              cycleRobustnessWeights.get(i));
          cycleSubseries.set(i, smoothed);
        }

        // Combine smoothed series into one
        double[] combinedSmoothed = new double[series.length];
        for (int i = 0; i < cycleSubseries.size(); i++) {
          double[] subseriesValues = cycleSubseries.get(i);
          for (int cycleIdx = 0; cycleIdx < subseriesValues.length; cycleIdx++) {
            combinedSmoothed[numberOfObservations * cycleIdx + i] = subseriesValues[cycleIdx];
          }
        }

        // Step 3: Low-Pass Filtering of Smoothed Cycle-Subseries
        double[] filtered = lowPassFilter(combinedSmoothed, robustness);

        // Step 4: Detrending of Smoothed Cycle-Subseries
        for (int i = 0; i < seasonal.length; i++) {
          seasonal[i] = combinedSmoothed[i] - filtered[i];
        }

        // Step 5: Deseasonalizing
        for (int i = 0; i < series.length; i++) {
          trend[i] = series[i] - seasonal[i];
        }

        // Step 6: Trend Smoothing
        trend = loessSmooth(trend, config.getTrendComponentBandwidth(), robustness);
      }

      // --- Now in outer loop ---

      // Calculate remainder
      for (int i = 0; i < series.length; i++) {
        remainder[i] = series[i] - trend[i] - seasonal[i];
      }

      // Calculate robustness weights using remainder
      robustness = robustnessWeights(remainder);
    }

    // TODO: The R code does cycle subseries weighted mean smoothing on seasonal component here
    /*
     if (periodic) {
        which.cycle <- cycle(x)
        z$seasonal <- tapply(z$seasonal, which.cycle, mean)[which.cycle]
     }
     remainder <- as.vector(x) - z$seasonal - z$trend
     y <- cbind(seasonal = z$seasonal, trend = z$trend, remainder = remainder)
     */

    return new STLResult(times, series, trend, seasonal, remainder);
  }

  private static class CycleSubSeries {
    // Output
    private final List<double[]> cycleSubSeries = new ArrayList<double[]>();
    private final List<double[]> cycleTimes = new ArrayList<double[]>();
    private final List<double[]> cycleRobustnessWeights = new ArrayList<double[]>();

    // Input
    private final int numberOfObservations;
    private final long[] times;
    private final double[] series;
    private final double[] robustness;
    private final double[] detrend;

    CycleSubSeries(long[] times, double[] series, double[] robustness, double[] detrend, int numberOfObservations) {
      this.times = times;
      this.series = series;
      this.robustness = robustness;
      this.detrend = detrend;
      this.numberOfObservations = numberOfObservations;
    }

    public List<double[]> getCycleSubSeries() {
      return cycleSubSeries;
    }

    public List<double[]> getCycleTimes() {
      return cycleTimes;
    }

    public List<double[]> getCycleRobustnessWeights() {
      return cycleRobustnessWeights;
    }

    void compute() {
      for (int i = 0; i < numberOfObservations; i++) {
        int subseriesLength = series.length / numberOfObservations;
        subseriesLength += (i < series.length % numberOfObservations) ? 1 : 0;
        double[] subseriesValues = new double[subseriesLength];
        double[] subseriesTimes = new double[subseriesLength];
        double[] subseriesRobustnessWeights = null;
        if (robustness != null) {
          subseriesRobustnessWeights = new double[subseriesLength];
        }

        for (int cycleIdx = 0; cycleIdx < subseriesLength; cycleIdx++) {
          subseriesValues[cycleIdx] = detrend[cycleIdx * numberOfObservations + i];
          subseriesTimes[cycleIdx] = times[cycleIdx * numberOfObservations + i];
          if (subseriesRobustnessWeights != null) {
            subseriesRobustnessWeights[cycleIdx] = robustness[cycleIdx * numberOfObservations + i];

            // TODO: Hack to ensure no divide by zero
            if (subseriesRobustnessWeights[cycleIdx] < 0.001) {
              subseriesRobustnessWeights[cycleIdx] = 0.01;
            }
          }
        }

        cycleSubSeries.add(subseriesValues);
        cycleTimes.add(subseriesTimes);
        cycleRobustnessWeights.add(subseriesRobustnessWeights);
      }
    }
  }

  private double[] robustnessWeights(double[] remainder) {
    // Compute "h" = 6 median(|R_v|)
    double[] absRemainder = new double[remainder.length];
    for (int i = 0; i < remainder.length; i++) {
      absRemainder[i] = Math.abs(remainder[i]);
    }
    DescriptiveStatistics stats = new DescriptiveStatistics(absRemainder);
    double h = 6 * stats.getPercentile(50);

    // Compute robustness weights
    double[] robustness = new double[remainder.length];
    for (int i = 0; i < remainder.length; i++) {
      robustness[i] = biSquareWeight(absRemainder[i] / h);
    }

    return robustness;
  }

  private double biSquareWeight(double u) {
    if (u < 0) {
      throw new IllegalArgumentException("Invalid u, must be >= 0: " + u);
    } else if (u < 1) {
      return Math.pow(1 - Math.pow(u, 2), 2);
    } else {
      return 0;
    }
  }

  private double[] lowPassFilter(double[] series, double[] weights) {
    // Apply moving average of length n_p, twice
    series = movingAverage(series, config.getNumberOfObservations());
    series = movingAverage(series, config.getNumberOfObservations());
    // Apply moving average of length 3
    series = movingAverage(series, 3);
    // Loess smoothing with d = 1, q = n_l
    series = loessSmooth(series, config.getLowPassFilterBandwidth(), weights);
    return series;
  }

  /**
   * @param weights
   *  The weights to use for smoothing, if null, equal weights are assumed
   * @return
   *  Smoothed series
   */
  private double[] loessSmooth(double[] series, double bandwidth, double[] weights) {
    double[] times = new double[series.length];
    for (int i = 0; i < series.length; i++) {
      times[i] = i;
    }
    return loessSmooth(times, series, bandwidth, weights);
  }

  /**
   * @param weights
   *  The weights to use for smoothing, if null, equal weights are assumed
   * @return
   *  Smoothed series
   */
  private double[] loessSmooth(double[] times, double[] series, double bandwidth, double[] weights) {
    if (weights == null) {
      return new LoessInterpolator(bandwidth, LOESS_ROBUSTNESS_ITERATIONS).smooth(times, series);
    } else {
      return new LoessInterpolator(bandwidth, LOESS_ROBUSTNESS_ITERATIONS).smooth(times, series, weights);
    }
  }

  private double[] weightedMeanSmooth(double[] series, double[] weights) {
    double[] smoothed = new double[series.length];
    double mean = 0;
    double sumOfWeights = 0;
    for (int i = 0; i < series.length; i++) {
      double weight = (weights != null) ? weights[i] : 1; // equal weights if none specified
      mean += weight * series[i];;
      sumOfWeights += weight;
    }
    // TODO: This is a hack to not have NaN values
    if (sumOfWeights == 0) {
      for (int i = 0; i < series.length; i++) {
        smoothed[i] = series[i];
      }
    } else {
      mean /= sumOfWeights;
      for (int i = 0; i < series.length; i++) {
        smoothed[i] = mean;
      }
    }
    return smoothed;
  }


  private double[] movingAverage(double[] series, int window) {
    double[] movingAverage = new double[series.length];

    // Initialize
    double average = 0;
    for (int i = 0; i < window; i++) {
      average += series[i] / window;
      movingAverage[i] = average;
    }

    for (int i = window; i < series.length; i++) {
      average -= series[i - window] / window;
      average += series[i] / window;
      movingAverage[i] = average;
    }

    return movingAverage;
  }
}
