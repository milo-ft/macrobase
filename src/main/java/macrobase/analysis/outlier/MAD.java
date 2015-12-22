package macrobase.analysis.outlier;

import macrobase.analysis.summary.result.DatumWithScore;
import macrobase.datamodel.Datum;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class MAD implements OutlierDetector {
    private double median;
    private double MAD;
    private final double madThresh;
    private final boolean threshIsRobustZScore;

    // https://en.wikipedia.org/wiki/Median_absolute_deviation#Relation_to_standard_deviation
    private final double MAD_TO_ZSCORE_COEFFICIENT = 1.4826;

    public MAD(Double madThreshAsRobustZScore) {
        this(madThreshAsRobustZScore, true);
    }

    // if threshIsRobustZScore: treat MAD as a replacement for Z-Score
    //      e.g., anything with (x - med)/(K*MAD) above 3 is marked as an outlier
    // if not: treat thresh as a percentile...
    //      e.g., treat the top 1% of points as an outlier
    public MAD(Double madThresh, boolean threshIsRobustZScore) {
        this.threshIsRobustZScore = threshIsRobustZScore;
        this.madThresh = madThresh;
    }

    @Override
    public BatchResult classifyBatch(List<Datum> data) {
        assert(data.get(0).getMetrics().getDimension() == 1);
        data.sort((x, y) -> Double.compare(x.getMetrics().getEntry(0),
                                           y.getMetrics().getEntry(0)));

        if(data.size() % 2 == 0) {
            median = (data.get(data.size()/2-1).getMetrics().getEntry(0) +
                      data.get(data.size()/2+1).getMetrics().getEntry(0))/2;
        } else {
            median = data.get((int)Math.ceil(data.size()/2)).getMetrics().getEntry(0);
        }

        List<Double> residuals = new ArrayList<>(data.size());
        for(Datum d : data) {
            residuals.add(Math.abs(d.getMetrics().getEntry(0) - median));
        }

        residuals.sort((a, b) -> Double.compare(a, b));

        if(data.size() % 2 == 0) {
            MAD = (residuals.get(data.size()/2-1) +
                   residuals.get(data.size()/2+1))/2;
        } else {
            MAD = residuals.get((int) Math.ceil(data.size() / 2));
        }

        List<DatumWithScore> inliers;
        List<DatumWithScore> outliers;

        if(threshIsRobustZScore) {
            inliers = new ArrayList<>();
            outliers = new ArrayList<>();

            for (Datum d : data) {
                double point = d.getMetrics().getEntry(0);
                double score = Math.abs(point - median) / (MAD * MAD_TO_ZSCORE_COEFFICIENT);

                if (score > madThresh) {
                    outliers.add(new DatumWithScore(d, score));
                } else {
                    inliers.add(new DatumWithScore(d, score));
                }
            }
        } else {
            int splitPoint = (int)(data.size()-data.size()*madThresh);
            List<DatumWithScore> scoredData = new ArrayList<>();
            for(Datum d : data) {
                double point = d.getMetrics().getEntry(0);
                double score = Math.abs(point - median) / (MAD * MAD_TO_ZSCORE_COEFFICIENT);
                scoredData.add(new DatumWithScore(d, score));
            }

            scoredData.sort((a, b) -> a.getScore().compareTo(b.getScore()));

            inliers = scoredData.subList(0, splitPoint);
            outliers = scoredData.subList(splitPoint, scoredData.size());
        }

        return new BatchResult(inliers, outliers);
    }
}