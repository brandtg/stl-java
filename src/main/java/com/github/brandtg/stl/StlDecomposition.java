/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.brandtg.stl;

import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.analysis.interpolation.NevilleInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunctionLagrangeForm;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This package contains an implementation of STL:
 * A Seasonal-Trend Decomposition Procedure based on Loess.
 *
 * <p>
 *  Robert B. Cleveland et al.,
 *  "STL: A Seasonal-Trend Decomposition Procedure based on Loess," in Journal
 *  of Official Statistics Vol. 6 No. 1, 1990, pp. 3-73
 * </p>
 */
public class StlDecomposition {
  /** The configuration with which to run STL. */
  private final StlConfig config;

  /**
   * Constructs an STL function that can de-trend data.
   *
   * <p>
   * n.b. The Java Loess implementation only does linear local polynomial
   * regression, but R supports linear (degree=1), quadratic (degree=2), and a
   * strange degree=0 option.
   * </p>
   *
   * <p>
   * Also, the Java Loess implementation accepts "bandwidth", the fraction of
   * source points closest to the current point, as opposed to integral values.
   * </p>
   *
   * @param numberOfObservations The number of observations in a season.
   */
  public StlDecomposition(int numberOfObservations) {
    this.config = new StlConfig(numberOfObservations);
  }

  /**
   * @return The configuration used by this function for fine tuning.
   */
  public StlConfig getConfig() {
    return config;
  }

  /**
   * A convenience method to use objects.
   *
   * @param times
   *  A sequence of time values.
   * @param series
   *  A dependent variable on times.
   * @return
   *  The STL decomposition of the time series.
   */
  public StlResult decompose(List<Number> times, List<Number> series) {
    double[] timesArray = new double[times.size()];
    double[] seriesArray = new double[series.size()];

    int idx = 0;
    for (Number time : times) {
      timesArray[idx++] = time.doubleValue();
    }

    idx = 0;
    for (Number value : series) {
      seriesArray[idx++] = value.doubleValue();
    }

    return decompose(timesArray, seriesArray);
  }

  /**
   * Computes the STL decomposition of a times series.
   *
   * @param times
   *  A sequence of time values.
   * @param series
   *  A dependent variable on times.
   * @return
   *  The STL decomposition of the time series.
   */
  public StlResult decompose(double[] times, double[] series) {
    if (times.length != series.length) {
      throw new IllegalArgumentException("Times (" + times.length +
          ") and series (" + series.length + ") must be same size");
    }
    int numberOfDataPoints = series.length;
    config.check(numberOfDataPoints);

    double[] trend = new double[numberOfDataPoints];
    double[] seasonal = new double[numberOfDataPoints];
    double[] remainder = new double[numberOfDataPoints];
    double[] robustness = null;
    double[] detrend = new double[numberOfDataPoints];
    double[] combinedSmoothed = new double[numberOfDataPoints +
        2 * config.getNumberOfObservations()];

    double[] combinedSmoothedTimes = new double[numberOfDataPoints +
        2 * config.getNumberOfObservations()];
    for (int i = 0; i < combinedSmoothedTimes.length; i++) {
      combinedSmoothedTimes[i] = i;
    }

    for (int l = 0; l < config.getNumberOfRobustnessIterations(); l++) {
      for (int k = 0; k < config.getNumberOfInnerLoopPasses(); k++) {
        // Step 1: De-trending
        for (int i = 0; i < numberOfDataPoints; i++) {
          detrend[i] = series[i] - trend[i];
        }

        // Get cycle sub-series
        int numberOfObservations = config.getNumberOfObservations();
        CycleSubSeries cycle = new CycleSubSeries(times, series, robustness, detrend, numberOfObservations);
        cycle.compute();
        List<double[]> cycleSubseries = cycle.getCycleSubSeries();
        List<double[]> cycleTimes = cycle.getCycleTimes();
        List<double[]> cycleRobustnessWeights = cycle.getCycleRobustnessWeights();

        // Step 2: Cycle-subseries Smoothing
        for (int i = 0; i < cycleSubseries.size(); i++) {
          // Pad times / values
          TimesAndValues padded = padEdges(cycleTimes.get(i), cycleSubseries.get(i));
          double[] paddedTimes = padded.getTs();
          double[] paddedSeries = padded.getXs();

          // Pad weights
          double[] weights = cycleRobustnessWeights.get(i);
          double[] paddedWeights = null;
          if (weights != null) {
            paddedWeights = padEdges(cycleTimes.get(i), weights).getXs();
          }

          // Loess smoothing
          double[] smoothed = loessSmooth(
              paddedTimes,
              paddedSeries,
              config.getSeasonalComponentBandwidth(),
              paddedWeights);

          cycleSubseries.set(i, smoothed);
        }

        // Combine smoothed series into one
        for (int i = 0; i < cycleSubseries.size(); i++) {
          double[] subseriesValues = cycleSubseries.get(i);
          for (int cycleIdx = 0; cycleIdx < subseriesValues.length; cycleIdx++) {
            combinedSmoothed[numberOfObservations * cycleIdx + i] = subseriesValues[cycleIdx];
          }
        }

        // Step 3: Low-Pass Filtering of Smoothed Cycle-Subseries
        double[] filtered = lowPassFilter(combinedSmoothedTimes, combinedSmoothed, null);

        // Step 4: Detrending of Smoothed Cycle-Subseries
        int offset = config.getNumberOfObservations();
        for (int i = 0; i < seasonal.length; i++) {
          seasonal[i] = combinedSmoothed[i + offset] - filtered[i + offset];
        }

        // Step 5: Deseasonalizing
        for (int i = 0; i < numberOfDataPoints; i++) {
          trend[i] = series[i] - seasonal[i];
        }

        // Step 6: Trend Smoothing
        trend = loessSmooth(times, trend, config.getTrendComponentBandwidth(), robustness);
      }

      // --- Now in outer loop ---

      // Calculate remainder
      for (int i = 0; i < numberOfDataPoints; i++) {
        remainder[i] = series[i] - trend[i] - seasonal[i];
      }

      // Calculate robustness weights using remainder
      robustness = robustnessWeights(remainder);
    }

    if (config.isPeriodic()) {
      for (int i = 0; i < config.getNumberOfObservations(); i++) {
        // Compute weighted mean for one season
        double sum = 0.0;
        int count = 0;
        for (int j = i; j < numberOfDataPoints; j += config.getNumberOfObservations()) {
          sum += seasonal[j];
          count++;
        }
        double mean = sum / count;

        // Copy this to rest of seasons
        for (int j = i; j < numberOfDataPoints; j += config.getNumberOfObservations()) {
          seasonal[j] = mean;
        }
      }

      // Recalculate remainder
      for (int i = 0; i < series.length; i++) {
        remainder[i] = series[i] - trend[i] - seasonal[i];
      }
    }

    return new StlResult(times, series, trend, seasonal, remainder);
  }

  /**
   * The cycle subseries of a time series.
   *
   * <p>
   *   The cycle subseries is a set of series whose members are of length
   *   N, where N is the number of observations in a season.
   * </p>
   *
   * <p>
   *   For example, if we have monthly data from 1990 to 2000, the cycle
   *   subseries would be [[Jan_1990, Jan_1991, ...], ..., [Dec_1990, Dec_1991]].
   * </p>
   */
  private static class CycleSubSeries {
    /** Output: The list of cycle subseries series data. */
    private final List<double[]> cycleSubSeries = new ArrayList<double[]>();
    /** Output: The list of cycle subseries times. */
    private final List<double[]> cycleTimes = new ArrayList<double[]>();
    /** Output: The list of cycle subseries robustness weights. */
    private final List<double[]> cycleRobustnessWeights = new ArrayList<double[]>();

    /** Input: The number of observations in a season. */
    private final int numberOfObservations;
    /** Input: The input times. */
    private final double[] times;
    /** Input: The input series data. */
    private final double[] series;
    /** Input: The robustness weights, from STL. */
    private final double[] robustness;
    /** Input: The de-trended series, from STL. */
    private final double[] detrend;

    /**
     * Constructs a cycle subseries computation.
     *
     * @param times
     *  The input times.
     * @param series
     *  A dependent variable on times.
     * @param robustness
     *  The robustness weights from STL loop.
     * @param detrend
     *  The de-trended data.
     * @param numberOfObservations
     *  The number of observations in a season.
     */
    CycleSubSeries(double[] times,
                   double[] series,
                   double[] robustness,
                   double[] detrend,
                   int numberOfObservations) {
      this.times = times;
      this.series = series;
      this.robustness = robustness;
      this.detrend = detrend;
      this.numberOfObservations = numberOfObservations;
    }

    /**
     * @return
     *  A list of size numberOfObservations, whose elements are of length
     *  times.length / numberOfObservations: the cycle subseries.
     */
    List<double[]> getCycleSubSeries() {
      return cycleSubSeries;
    }

    /**
     * @return The times corresponding to getCycleSubSeries.
     */
    List<double[]> getCycleTimes() {
      return cycleTimes;
    }

    /**
     * @return The robustness weights corresponding to getCycleSubSeries.
     */
    List<double[]> getCycleRobustnessWeights() {
      return cycleRobustnessWeights;
    }

    /**
     * Computes the cycle subseries of the input.
     *
     * <p>
     *   Must call this before getters return anything meaningful.
     * </p>
     */
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

  /**
   * Computes robustness weights using bisquare weight function.
   *
   * @param remainder
   *  The remainder, series - trend - seasonal.
   * @return
   *  A new array containing the robustness weights.
   */
  private double[] robustnessWeights(double[] remainder) {
    // Compute "h" = 6 median(|R_v|)
    double[] absRemainder = new double[remainder.length];
    for (int i = 0; i < remainder.length; i++) {
      absRemainder[i] = Math.abs(remainder[i]);
    }
    DescriptiveStatistics stats = new DescriptiveStatistics(absRemainder);
    double outlierThreshold = 6 * stats.getPercentile(50);

    // Compute robustness weights
    double[] robustness = new double[remainder.length];
    for (int i = 0; i < remainder.length; i++) {
      robustness[i] = biSquareWeight(absRemainder[i] / outlierThreshold);
    }

    return robustness;
  }

  /**
   * The bisquare weight function.
   *
   * @param value
   *  Any real number.
   * @return
   *  <pre>
   *    (1 - value^2)^2 for 0 <= value < 1
   *    0 for value > 1
   *  </pre>
   */
  private double biSquareWeight(double value) {
    if (value < 0) {
      throw new IllegalArgumentException("Invalid value, must be >= 0: " + value);
    } else if (value < 1) {
      return Math.pow(1 - Math.pow(value, 2), 2);
    } else {
      return 0;
    }
  }

  /**
   * A low pass filter used on combined smoothed cycle subseries.
   *
   * <p>
   *   The filter consists of the following steps:
   *   <ol>
   *     <li>Moving average of length n_p, seasonal size</li>
   *     <li>Moving average of length 3, (magic number from paper)</li>
   *     <li>Loess smoothing</li>
   *   </ol>
   * </p>
   *
   * @param times
   *  The times.
   * @param series
   *  The time series data.
   * @param weights
   *  Weights to use in Loess stage.
   * @return
   *  A smoother, less noisy series.
   */
  private double[] lowPassFilter(double[] times, double[] series, double[] weights) {
    // Find the next odd integer >= n_p (see: section 3.4)
    double nextOdd = config.getNumberOfObservations() % 2 == 1 ?
        config.getNumberOfObservations() :
        config.getNumberOfObservations() + 1;
    // Determine bandwidth as a percentage of points
    double lowPassBandwidth = nextOdd / series.length;

    // Apply moving average of length n_p, twice
    series = movingAverage(series, config.getNumberOfObservations());
    series = movingAverage(series, config.getNumberOfObservations());
    // Apply moving average of length 3
    series = movingAverage(series, 3);
    // Loess smoothing with d = 1, q = n_l
    series = loessSmooth(times, series, lowPassBandwidth, weights);
    return series;
  }

  /**
   * Performs weighted Loess smoothing on a series.
   *
   * <p>
   *   Does not assume contiguous time.
   * </p>
   *
   * @param times
   *  The times.
   * @param series
   *  The time series data.
   * @param bandwidth
   *  The amount of neighbor points to consider for each point in Loess.
   * @param weights
   *  The weights to use for smoothing, if null, equal weights are assumed.
   * @return
   *  Loess-smoothed series.
   */
  private double[] loessSmooth(double[] times,
                               double[] series,
                               double bandwidth,
                               double[] weights) {
    if (weights == null) {
      return new LoessInterpolator(
          bandwidth,
          config.getLoessRobustnessIterations()).smooth(times, series);
    } else {
      return new LoessInterpolator(
          bandwidth,
          config.getLoessRobustnessIterations()).smooth(times, series, weights);
    }
  }

  /**
   * Computes the moving average.
   *
   * <p>
   *   The first "window" values are meaningless in the return value.
   * </p>
   *
   * @param series
   *  An input series of data.
   * @param window
   *  The moving average sliding window.
   * @return
   *  A new series that contains moving average of series.
   */
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

  private static class TimesAndValues {
    private final double[] ts;
    private final double[] xs;

    TimesAndValues(double[] ts, double[] xs) {
      this.ts = ts;
      this.xs = xs;
    }

    public double[] getTs() {
      return ts;
    }

    public double[] getXs() {
      return xs;
    }
  }

  private TimesAndValues padEdges(double[] ts, double[] xs) {
    // Find step between times
    double step = Math.abs(ts[1] - ts[0]);
    // Times (assuming uniform
    double[] paddedTimes = new double[ts.length + 2];
    System.arraycopy(ts, 0, paddedTimes, 1, ts.length);
    paddedTimes[0] = paddedTimes[1] - step;
    paddedTimes[paddedTimes.length - 1] = paddedTimes[paddedTimes.length - 2] + step;

    // Series
    double[] paddedSeries = new double[xs.length + 2];
    System.arraycopy(xs, 0, paddedSeries, 1, xs.length);

    // Extrapolate both sides as per:
    // http://stackoverflow.com/questions/36656857/extrapolation-outofrangeexception-apache-commons-math
    PolynomialFunctionLagrangeForm interpolate = new NevilleInterpolator().interpolate(ts, xs);
    paddedSeries[0] = interpolate.value(paddedTimes[0]);
    paddedSeries[paddedSeries.length - 1] = interpolate.value(paddedTimes[paddedTimes.length - 1]);

    return new TimesAndValues(paddedTimes, paddedSeries);
  }

  /**
   * Runs STL on a CSV of time,measure.
   *
   * <p>
   *   Outputs a CSV of time,measure,trend,seasonal,remainder.
   * </p>
   *
   * @param args
   *  args[0] = numberOfObservations
   * @throws Exception
   *  If could not process data
   */
  public static void main(String[] args) throws Exception {
    List<Number> times = new ArrayList<Number>();
    List<Number> measures = new ArrayList<Number>();

    // Read from STDIN
    String line;
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split(",");
      times.add(Long.valueOf(tokens[0]));
      measures.add(Double.valueOf(tokens[1]));
    }

    // Compute STL
    StlDecomposition stl = new StlDecomposition(Integer.valueOf(args[0]));
    stl.getConfig().setSeasonalComponentBandwidth(
        Double.valueOf(System.getProperty(
            "seasonal.bandwidth", String.valueOf(StlConfig.DEFAULT_SEASONAL_BANDWIDTH))));
    stl.getConfig().setTrendComponentBandwidth(
        Double.valueOf(System.getProperty(
            "trend.bandwidth", String.valueOf(StlConfig.DEFAULT_TREND_BANDWIDTH))));
    stl.getConfig().setNumberOfInnerLoopPasses(
        Integer.valueOf(System.getProperty(
            "inner.loop", String.valueOf(StlConfig.DEFAULT_INNER_LOOP_PASSES))));
    StlResult res = stl.decompose(times, measures);

    // Output to STDOUT
    for (int i = 0; i < times.size(); i++) {
      System.out.println(String.format("%d,%02f,%02f,%02f,%02f",
          (long) res.getTimes()[i],
          res.getSeries()[i],
          res.getTrend()[i],
          res.getSeasonal()[i],
          res.getRemainder()[i]));
    }
  }
}
