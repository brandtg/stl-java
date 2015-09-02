package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Implementation of STL: A Seasonal-Trend Decomposition Procedure based on Loess.
 *
 * <p>
 *   Robert B. Cleveland et al., "STL: A Seasonal-Trend Decomposition Procedure based on Loess,"
 *   in Journal of Official Statistics Vol. 6 No. 1, 1990, pp. 3-73
 * </p>
 *
 * @author Greg Brandt (gbrandt@linkedin.com)
 * @author Jieying Chen (jjchen@linkedin.com)
 * @author James Hong (jhong@linkedin.com)
 */
public class STLDecomposition {
  private static final int LOESS_ROBUSTNESS_ITERATIONS = 4; // same as R implementation

  private final Config config;

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
  public STLDecomposition(Config config) {
    config.check();
    this.config = config;
  }

  public static class Config {
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

    public Config() {}

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

    public void check() {
      checkPeriodicity(numberOfObservations);

      // TODO: Check n_t, needs to be n_t >= 1.5 * n_p / (1 - 1.5/n_s)
    }

    private int checkPeriodicity(int numberOfObservations) {
      if (numberOfObservations < 2) {
        throw new IllegalArgumentException("Periodicity (numberOfObservations) must be >= 2");
      }
      return numberOfObservations;
    }
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

          // Apply robustness weight if have passed one inner loop
          if (robustness != null) {
            detrend[i] *= robustness[i];
          }
        }

        // Get cycle sub-series with padding on either side
        int numberOfObservations = config.getNumberOfObservations();
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
          double[] smoothed = loessSmooth(cycleTimes[i], cycleSubseries[i], config.getSeasonalComponentBandwidth());
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
        trend = loessSmooth(trend, config.getTrendComponentBandwidth());
      }

      // --- Now in outer loop ---

      // Calculate remainder
      for (int i = 0; i < series.length; i++) {
        remainder[i] = series[i] - trend[i] - seasonal[i];
      }

      // Calculate robustness weights using remainder
      robustness = robustnessWeights(remainder);
    }

    return new STLResult(times, series, trend, seasonal, remainder);
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

  private double[] lowPassFilter(double[] series) {
    // Apply moving average of length n_p, twice
    series = movingAverage(series, config.getNumberOfObservations());
    series = movingAverage(series, config.getNumberOfObservations());
    // Apply moving average of length 3
    series = movingAverage(series, 3);
    // Loess smoothing with d = 1, q = n_l
    series = loessSmooth(series, config.getLowPassFilterBandwidth());
    return series;
  }

  private double[] loessSmooth(double[] series, double bandwidth) {
    double[] times = new double[series.length];
    for (int i = 0; i < series.length; i++) {
      times[i] = i;
    }
    return loessSmooth(times, series, bandwidth);
  }

  private double[] loessSmooth(double[] times, double[] series, double bandwidth) {
    return new LoessInterpolator(bandwidth, LOESS_ROBUSTNESS_ITERATIONS).smooth(times, series);
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

  /**
   * Accepts an input JSON file containing raw time series, e.g.
   *
   * <pre>
   *   {
   *     "times": [1,2,3,4,...],
   *     "series": [100,200,300,400,...]
   *   }
   * </pre>
   *
   * And outputs the STL de-trended data:
   *
   * <pre>
   *   {
   *     "times": [1,2,3,4,...],
   *     "series": [100,200,300,400,...],
   *     "seasonal": [...],
   *     "trend": [...],
   *     "remainder": [...]
   *   }
   * </pre>
   *
   * n.b. This isn't really meant to be used on anything besides src/test/resources/sample-timeseries.json,
   * just an example.
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("usage: input_file output_file");
      System.exit(1);
    }

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode tree = objectMapper.readTree(new File(args[0]));
    int n = tree.get("times").size();
    long[] tsLong = new long[n];
    double[] ts = new double[n];
    double[] ys = new double[n];

    for (int i = 0; i < n; i++) {
      tsLong[i] =  tree.get("times").get(i).asLong();
      ts[i] = tree.get("times").get(i).asDouble();
      ys[i] = tree.get("series").get(i).asDouble();
    }

    // This configuration was chosen to work with monthly data over 20 years
    STLDecomposition.Config config = new STLDecomposition.Config();
    config.setNumberOfObservations(12);
    config.setNumberOfInnerLoopPasses(2);
    config.setNumberOfRobustnessIterations(1);
    config.setSeasonalComponentBandwidth(0.75);
    config.setLowPassFilterBandwidth(0.30);
    config.setTrendComponentBandwidth(0.10);
    STLDecomposition stl = new STLDecomposition(config);
    STLResult res = stl.decompose(tsLong, ys);

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(args[1]), res);
  }
}
