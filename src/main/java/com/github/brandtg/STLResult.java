package com.github.brandtg;

/**
 * The STL decomposition of a time series.
 *
 * <p>
 *   getData() == getTrend() + getSeasonal() + getRemainder()
 * </p>
 */
public class STLResult {
  private final long[] times;
  private final double[] series;
  private final double[] trend;
  private final double[] seasonal;
  private final double[] remainder;

  public STLResult(long[] times, double[] series, double[] trend, double[] seasonal, double[] remainder) {
    this.times = times;
    this.series = series;
    this.trend = trend;
    this.seasonal = seasonal;
    this.remainder = remainder;
  }

  public long[] getTimes() {
    return times;
  }

  public double[] getSeries() {
    return series;
  }

  public double[] getTrend() {
    return trend;
  }

  public double[] getSeasonal() {
    return seasonal;
  }

  public double[] getRemainder() {
    return remainder;
  }
}
