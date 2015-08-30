package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;

import java.io.File;
import java.io.FileOutputStream;

public class STLDecomposition {
  private final int numberOfObservations;
  private final int numberOfInnerLoopPasses;
  private final int numberOfRobustnessIterations;
  private final int lowPassFilterSmoothing;
  private final int trendComponentSmoothing;
  private final int seasonalComponentSmoothing;

  /**
   * Constructs a configuration of STL function that can de-trend data.
   *
   * <p>
   *   Robert B. Cleveland et al., "STL: A Seasonal-Trend Decomposition Procedure based on Loess,"
   *   in Journal of Official Statistics Vol. 6 No. 1, 1990, pp. 3-73
   * </p>
   *
   * <p>
   *   The three spans (n_s, n_t, and n_l) must be at least three and odd.
   * </p>
   *
   * @param numberOfObservations
   *  The number of observations in each cycle of the seasonal component, n_p
   * @param numberOfInnerLoopPasses
   *  The number of passes through the inner loop, n_i
   * @param numberOfRobustnessIterations
   *  The number of robustness iterations of the outer loop, n_o
   * @param lowPassFilterSmoothing
   *  The smoothing parameter for the low-pass filter, n_l
   * @param trendComponentSmoothing
   *  The smoothing parameter for the trend component, n_t
   * @param seasonalComponentSmoothing
   *  The smoothing parameter for the seasonal component, n_s
   */
  public STLDecomposition(int numberOfObservations,
                          int numberOfInnerLoopPasses,
                          int numberOfRobustnessIterations,
                          int lowPassFilterSmoothing,
                          int trendComponentSmoothing,
                          int seasonalComponentSmoothing) {
    this.numberOfObservations = checkPeriodicity(numberOfObservations);
    this.numberOfInnerLoopPasses = numberOfInnerLoopPasses;
    this.numberOfRobustnessIterations = numberOfRobustnessIterations;
    this.lowPassFilterSmoothing = checkSmoothing("lowPassFilterSmoothing", lowPassFilterSmoothing);
    this.trendComponentSmoothing = checkSmoothing("trendComponentSmoothing", trendComponentSmoothing);
    this.seasonalComponentSmoothing = checkSmoothing("seasonalComponentSmoothing", seasonalComponentSmoothing);
  }

  private int checkPeriodicity(int numberOfObservations) {
    if (numberOfObservations < 2) {
      throw new IllegalArgumentException("Periodicity (numberOfObservations) must be >= 2");
    }
    return numberOfObservations;
  }

  /**
   * Returns the smoothing component value if it is >= 3 and odd.
   */
  private int checkSmoothing(String name, int value) {
    if (value < 3) {
      throw new IllegalArgumentException(name + " must be at least 3: is " + value);
    }
    if (value % 2 == 0) {
      throw new IllegalArgumentException(name + " must be odd: is " + value);
    }
    return value;
  }

  public STLResult decompose(long[] times, double[] series) {
    double[] trend = new double[series.length];
    double[] seasonal = new double[series.length];
    double[] remainder = new double[series.length];

    for (int k = 0; k < numberOfRobustnessIterations; k++) {
      // Step 1: Detrending
      double[] detrend = new double[series.length];
      for (int i = 0; i < series.length; i++) {
        detrend[i] = series[i] - trend[i];
      }

      // Get cycle sub-series with padding on either side
      int cycleSubseriesLength = series.length / numberOfObservations;
      double[][] cycleSubseries = new double[numberOfObservations][cycleSubseriesLength + 2];
      double[][] cycleTimes = new double[numberOfObservations][cycleSubseriesLength + 2];
      for (int i = 0; i < series.length; i += numberOfObservations) {
        for (int j = 0; j < numberOfObservations; j++) {
          int cycleIdx = i / numberOfObservations;
          cycleSubseries[j][cycleIdx + 1] = detrend[i + j];
          cycleTimes[j][cycleIdx + 1] = i + j;
        }
      }

      // Beginning / end times
      for (int i = 0; i < numberOfObservations; i++) {
        cycleTimes[i][0] = cycleTimes[i][1] - numberOfObservations;
        cycleTimes[i][cycleTimes[i].length - 1] = cycleTimes[i][cycleTimes[i].length - 2] + numberOfObservations;
      }

      // Step 2: Cycle-subseries Smoothing
      for (int i = 0; i < cycleSubseries.length; i++) {
        double[] smoothed = loessSmooth(cycleTimes[i], cycleSubseries[i]);
        cycleSubseries[i] = smoothed;
      }

      // Combine smoothed series into one
      double[] combinedSmoothed = new double[series.length + 2 * numberOfObservations];
      for (int i = 0; i < cycleSubseriesLength + 2; i++) {
        for (int j = 0; j < numberOfObservations; j++) {
          combinedSmoothed[i * numberOfObservations + j] = cycleSubseries[j][i];
        }
      }

      // Step 3: Low-Pass Filtering of Smoothed Cycle-Subseries
      double[] filtered = lowPassFilter(combinedSmoothed);

      // Step 4: Detrending of Smoothed Cycle-Subseries
      for (int i = 0; i < seasonal.length; i++) {
        seasonal[i] = combinedSmoothed[i + numberOfObservations] - filtered[i + numberOfObservations];
      }

      // Step 5: Deseasonalizing
      for (int i = 0; i < series.length; i++) {
        trend[i] = series[i] - seasonal[i];
      }

      // Step 6: Trend Smoothing
      trend = loessSmooth(trend);

      // Calculate remainder
      for (int i = 0; i < series.length; i++) {
        remainder[i] = series[i] - trend[i] - seasonal[i];
      }
    }

    return new STLResult(times, series, trend, seasonal, remainder);
  }

  private double[] lowPassFilter(double[] series) {
    // Apply moving average of length n_p, twice
    series = movingAverage(series, numberOfObservations);
    series = movingAverage(series, numberOfObservations);
    // Apply moving average of length 3
    series = movingAverage(series, 3);
    // Loess smoothing with d = 1, q = n_l
    series = loessSmooth(series);
    return series;
  }

  private double[] loessSmooth(double[] series) {
    double[] times = new double[series.length];
    for (int i = 0; i < series.length; i++) {
      times[i] = i;
    }
    return loessSmooth(times, series);
  }

  private double[] loessSmooth(double[] times, double[] series) {
    return new LoessInterpolator().smooth(times, series);
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

  public static void main(String[] args) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode tree = objectMapper.readTree(new File(args[0]));
    int n = tree.get("ts").size();
    long[] tsLong = new long[n];
    double[] ts = new double[n];
    double[] ys = new double[n];

    for (int i = 0; i < n; i++) {
      tsLong[i] =  tree.get("ts").get(i).asLong();
      ts[i] = tree.get("ts").get(i).asDouble();
      ys[i] = tree.get("ys").get(i).asDouble();
    }

    STLDecomposition stl = new STLDecomposition(12, 1, 1, 3, 3, 3);
    STLResult res = stl.decompose(tsLong, ys);

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(args[1]), res);
  }
}
