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

public class STLConfig {
  /** The number of observations in each cycle of the seasonal component, n_p */
  private int numberOfObservations;
  /** The number of passes through the inner loop, n_i */
  private int numberOfInnerLoopPasses = 1;
  /** The number of robustness iterations of the outer loop, n_o */
  private int numberOfRobustnessIterations = 1;
  /** The smoothing parameter for the low pass filter, like n_l */
  private double lowPassFilterBandwidth = 0.25;
  /** The smoothing parameter for the trend component, like n_t */
  private double trendComponentBandwidth = 0.25;
  /** The smoothing parameter for the seasonal component, like n_s */
  private double seasonalComponentBandwidth = 0.25;
  /** Whether the series is periodic, if this is true, then seasonalComponentBandwidth is ignored. */
  private boolean periodic = false;
  /** The total length of the time series */
  private int numberOfDataPoints;

  public STLConfig() {}

  public int getNumberOfObservations() {
    return numberOfObservations;
  }

  public void setNumberOfObservations(int numberOfObservations) {
    this.numberOfObservations = numberOfObservations;
  }

  public int getNumberOfInnerLoopPasses() {
    return numberOfInnerLoopPasses;
  }

  public void setNumberOfInnerLoopPasses(int numberOfInnerLoopPasses) {
    this.numberOfInnerLoopPasses = numberOfInnerLoopPasses;
  }

  public int getNumberOfRobustnessIterations() {
    return numberOfRobustnessIterations;
  }

  public void setNumberOfRobustnessIterations(int numberOfRobustnessIterations) {
    this.numberOfRobustnessIterations = numberOfRobustnessIterations;
  }

  public double getLowPassFilterBandwidth() {
    return lowPassFilterBandwidth;
  }

  public void setLowPassFilterBandwidth(double lowPassFilterBandwidth) {
    this.lowPassFilterBandwidth = lowPassFilterBandwidth;
  }

  public double getTrendComponentBandwidth() {
    return trendComponentBandwidth;
  }

  public void setTrendComponentBandwidth(double trendComponentBandwidth) {
    this.trendComponentBandwidth = trendComponentBandwidth;
  }

  public double getSeasonalComponentBandwidth() {
    return seasonalComponentBandwidth;
  }

  public void setSeasonalComponentBandwidth(double seasonalComponentBandwidth) {
    this.seasonalComponentBandwidth = seasonalComponentBandwidth;
  }

  public boolean isPeriodic() {
    return periodic;
  }

  public void setPeriodic(boolean periodic) {
    this.periodic = periodic;
  }

  public int getNumberOfDataPoints() {
    return numberOfDataPoints;
  }

  public void setNumberOfDataPoints(int numberOfDataPoints) {
    this.numberOfDataPoints = numberOfDataPoints;
  }

  public void check() {
    checkPeriodicity(numberOfObservations);

    // TODO: Check n_t, needs to be n_t >= 1.5 * n_p / (1 - 1.5/n_s)
    if (periodic) {
      double tWindowSpan = (1.5 * numberOfObservations) / (1.0 - 1.5 / (numberOfDataPoints * 10.0 + 1.0)) / numberOfDataPoints;
      setTrendComponentBandwidth(tWindowSpan);
    }
  }

  private int checkPeriodicity(int numberOfObservations) {
    if (numberOfObservations < 2) {
      throw new IllegalArgumentException("Periodicity (numberOfObservations) must be >= 2");
    }
    if (numberOfDataPoints <= 2 * numberOfObservations) {
      throw new IllegalArgumentException("numberOfDataPoints(total length) must contain at least 2 * Periodicity (numberOfObservations) points");
    }
    return numberOfObservations;
  }
}
