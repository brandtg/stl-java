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

/**
 * Configuration for {@link StlDecomposition}.
 */
public final class StlConfig {
  /** Empirically determined number resulted in good residuals. */
  protected static final int DEFAULT_INNER_LOOP_PASSES = 10;
  /** Same as R's robust = FALSE. */
  protected static final int DEFAULT_ROBUSTNESS_ITERATIONS = 1;
  /** Consider 25% neighboring points smoothing in low pass filter. */
  protected static final double DEFAULT_LOW_PASS_FILTER_BANDWIDTH = 0.25;
  /** Consider 75% neighboring points smoothing trend in inner loop. */
  protected static final double DEFAULT_TREND_BANDWIDTH = 0.75;
  /** Consider 75% neighboring points smoothing seasonal in inner loop. */
  protected static final double DEFAULT_SEASONAL_BANDWIDTH = 0.75;
  /** Number of robustness iterations for each invocation of Loess. */
  protected static final int DEFAULT_LOESS_ROBUSTNESS_ITERATIONS = 0;

  /** n_p: The number of observations in each cycle of seasonal component. */
  private final int numberOfObservations;
  /** n_i: The number of passes through the inner loop. */
  private int numberOfInnerLoopPasses = DEFAULT_INNER_LOOP_PASSES;
  /** n_o: The number of robustness iterations of the outer loop. */
  private int numberOfRobustnessIterations = DEFAULT_ROBUSTNESS_ITERATIONS;
  /** n_l: The smoothing parameter for the low pass filter. */
  private double lowPassFilterBandwidth = DEFAULT_LOW_PASS_FILTER_BANDWIDTH;
  /** n_t: The smoothing parameter for the trend component. */
  private double trendComponentBandwidth = DEFAULT_TREND_BANDWIDTH;
  /** n_s: The smoothing parameter for the seasonal component. */
  private double seasonalComponentBandwidth = DEFAULT_SEASONAL_BANDWIDTH;
  /** The number of robustness iterations in each invocation of Loess. */
  private int loessRobustnessIterations = DEFAULT_LOESS_ROBUSTNESS_ITERATIONS;

  /**
   * Set to true if the series is known to be periodic.
   *
   * <p>
   *   If true, trendComponentBandwidth is re-computed in {@link #check(int)},
   *   and post-seasonal smoothing is done in {@link StlDecomposition}.
   * </p>
   */
  private boolean periodic = true;

  /**
   * A configuration for {@link StlDecomposition}.
   *
   * @param numberOfObservations
   *  The number of observations in each period of the time series
   */
  protected StlConfig(int numberOfObservations) {
    this.numberOfObservations = numberOfObservations;
  }

  /**
   * @return The number of observations in a period.
   */
  public int getNumberOfObservations() {
    return numberOfObservations;
  }

  /**
   * @return The number of inner loop passes.
   */
  public int getNumberOfInnerLoopPasses() {
    return numberOfInnerLoopPasses;
  }

  /**
   * @param numberOfInnerLoopPasses
   *  The number of inner loop passes.
   */
  public void setNumberOfInnerLoopPasses(int numberOfInnerLoopPasses) {
    this.numberOfInnerLoopPasses = numberOfInnerLoopPasses;
  }

  /**
   * @return The number of robustness iterations, i.e. outer loop.
   */
  public int getNumberOfRobustnessIterations() {
    return numberOfRobustnessIterations;
  }

  /**
   * @param numberOfRobustnessIterations The number of robustness iterations
   */
  public void setNumberOfRobustnessIterations(int numberOfRobustnessIterations) {
    this.numberOfRobustnessIterations = numberOfRobustnessIterations;
  }

  /**
   * @return The % of points considered by Loess for low pass filter.
   */
  public double getLowPassFilterBandwidth() {
    return lowPassFilterBandwidth;
  }

  /**
   * @param lowPassFilterBandwidth
   *  The % of points considered by Loess for low pass filter.
   */
  public void setLowPassFilterBandwidth(double lowPassFilterBandwidth) {
    this.lowPassFilterBandwidth = lowPassFilterBandwidth;
  }

  /**
   * @return The % of points considered by Loess for trend smoothing.
   */
  public double getTrendComponentBandwidth() {
    return trendComponentBandwidth;
  }

  /**
   * @param trendComponentBandwidth
   *  The % of points considered by Loess for trend smoothing.
   */
  public void setTrendComponentBandwidth(double trendComponentBandwidth) {
    this.trendComponentBandwidth = trendComponentBandwidth;
  }

  /**
   * @return The % of points considered by Loess for seasonal smoothing.
   */
  public double getSeasonalComponentBandwidth() {
    return seasonalComponentBandwidth;
  }

  /**
   * @param seasonalComponentBandwidth
   *  The % of points considered by Loess for seasonal smoothing.
   */
  public void setSeasonalComponentBandwidth(double seasonalComponentBandwidth) {
    this.seasonalComponentBandwidth = seasonalComponentBandwidth;
  }

  /**
   * @return Whether this time series is known to be periodic a priori.
   */
  public boolean isPeriodic() {
    return periodic;
  }

  /**
   * @param periodic
   *  Whether this time series is known to be periodic a priori.
   */
  public void setPeriodic(boolean periodic) {
    this.periodic = periodic;
  }

  /**
   * @return The number of robustness iterations used by LoessInterpolator.
   */
  public int getLoessRobustnessIterations() {
    return loessRobustnessIterations;
  }

  /**
   * @param loessRobustnessIterations
   *  The number of robustness iterations used by LoessInterpolator.
   */
  public void setLoessRobustnessIterations(int loessRobustnessIterations) {
    this.loessRobustnessIterations = loessRobustnessIterations;
  }

  /**
   * Checks consistency of configuration parameters.
   *
   * <p>
   *   Must be called each time this configuration is used.
   * </p>
   *
   * <p>
   *   There must be at least two observations, and at least two periods
   *   in the data.
   * </p>
   *
   * @param numberOfDataPoints The number of data points in the target series.
   */
  protected void check(int numberOfDataPoints) {
    if (numberOfObservations < 2) {
      throw new IllegalArgumentException(
          "Periodicity (numberOfObservations) must be >= 2");
    }

    if (numberOfDataPoints <= 2 * numberOfObservations) {
      throw new IllegalArgumentException(
          "numberOfDataPoints(total length) must contain at least " +
              "2 * Periodicity (numberOfObservations) points");
    }

    if (periodic) {
      // Override trend component bandwidth
      double windowSpan = (1.5 * numberOfObservations) /
          (1.0 - 1.5 / (numberOfDataPoints * seasonalComponentBandwidth)) /
          numberOfDataPoints;
      setTrendComponentBandwidth(windowSpan);
    } else {
      // Check n_t >= 1.5 * n_p / (1 - 1.5 / n_s)
      double trendWindow = trendComponentBandwidth * numberOfDataPoints;
      double seasonalWindow = seasonalComponentBandwidth * numberOfDataPoints;
      double minTrendWindow = 1.5 * numberOfObservations /
          (1 - 1.5 / seasonalWindow);
      if (trendWindow < minTrendWindow) {
        throw new IllegalArgumentException("Trend component bandwidth too " +
            "small: trendWindow=" + trendWindow + " min=" + minTrendWindow);
      }
    }

    // Ensure trend bandwidth in points is >= 2
    // n.b. We use numberOfDataPoints because this applies to combined series
    int trendBandwidthInPoints = (int) (trendComponentBandwidth * numberOfDataPoints);
    if (trendBandwidthInPoints < 2) {
      throw new IllegalArgumentException("Trend component bandwidth " +
          trendComponentBandwidth + " is too small, maps to " +
          trendBandwidthInPoints + " points");
    }

    // Ensure trend bandwidth in points is >= 2
    // n.b. We use numberOfObservations because this applies to cycle subseries
    int numSeasons = numberOfDataPoints / numberOfObservations;
    int seasonalBandwidthInPoints = (int) (seasonalComponentBandwidth * numSeasons);
    if (seasonalBandwidthInPoints < 2) {
      throw new IllegalArgumentException("Seasonal component bandwidth " +
          seasonalComponentBandwidth + " is too small, maps to " +
          seasonalBandwidthInPoints + " points");
    }
  }
}
