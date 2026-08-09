package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import javax.swing.JLabel;
import javax.swing.JTextArea;

final class UiRefreshPresenter {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private final JLabel sampleRateLabel;
    private final JTextArea calibrationLabel;
    private final JTextArea eventCountLabel;
    private final JTextArea fuelPathStatusLabel;
    private final JTextArea sessionModeLabel;
    private final JTextArea guidanceLabel;
    private final JTextArea mapCollectionLabel;
    private final JTextArea sessionReviewLabel;
    private final JTextArea recommendationHistoryText;
    private final JLabel overviewConnectionLabel;
    private final JLabel overviewRateLabel;
    private final StatusCard calibrationCard;

    UiRefreshPresenter(JLabel sampleRateLabel,
                       JTextArea calibrationLabel,
                       JTextArea eventCountLabel,
                       JTextArea fuelPathStatusLabel,
                       JTextArea sessionModeLabel,
                       JTextArea guidanceLabel,
                       JTextArea mapCollectionLabel,
                       JTextArea sessionReviewLabel,
                       JTextArea recommendationHistoryText,
                       JLabel overviewConnectionLabel,
                       JLabel overviewRateLabel,
                       StatusCard calibrationCard) {
        this.sampleRateLabel = sampleRateLabel;
        this.calibrationLabel = calibrationLabel;
        this.eventCountLabel = eventCountLabel;
        this.fuelPathStatusLabel = fuelPathStatusLabel;
        this.sessionModeLabel = sessionModeLabel;
        this.guidanceLabel = guidanceLabel;
        this.mapCollectionLabel = mapCollectionLabel;
        this.sessionReviewLabel = sessionReviewLabel;
        this.recommendationHistoryText = recommendationHistoryText;
        this.overviewConnectionLabel = overviewConnectionLabel;
        this.overviewRateLabel = overviewRateLabel;
        this.calibrationCard = calibrationCard;
    }

    void refreshTechnicalStatus(double sampleRateHz,
                                String eventCount,
                                String fuelPathStatus,
                                String sessionMode,
                                String guidance,
                                String mapCollection) {
        setLabelTextIfChanged(sampleRateLabel, sampleRateText(sampleRateHz));
        setTextIfChanged(eventCountLabel, eventCount);
        setTextIfChanged(fuelPathStatusLabel, fuelPathStatus);
        setTextIfChanged(sessionModeLabel, sessionMode);
        setTextIfChanged(guidanceLabel, guidance);
        setTextIfChanged(mapCollectionLabel, mapCollection);
    }

    void refreshOverviewHeader(String configurationName, int subscribed, double sampleRateHz) {
        setLabelTextIfChanged(overviewConnectionLabel, "TunerStudio project: "
                + (configurationName == null ? "not connected" : configurationName)
                + "  •  " + subscribed + " live channels");
        setLabelTextIfChanged(overviewRateLabel, sampleRateText(sampleRateHz));
    }

    void refreshCalibration(boolean running,
                            double secondsRemaining,
                            long detectionArmedNano,
                            long nowNano,
                            TpsNoiseCalibration.Result result) {
        if (running) {
            String seconds = F1.format(secondsRemaining);
            setTextIfChanged(calibrationLabel, "TPS calibration running: " + seconds
                    + " s remaining. Event capture paused.");
            calibrationCard.setValue("RUNNING  •  " + seconds
                    + " s remaining  •  Do not touch throttle", CardState.ACTIVE);
            return;
        }
        if (detectionArmedNano > 0L && nowNano < detectionArmedNano) {
            String seconds = F1.format(Math.max(0.0,
                    (detectionArmedNano - nowNano) / 1000000000.0));
            setTextIfChanged(calibrationLabel, "TPS calibration: event detection arming in "
                    + seconds + " s");
            calibrationCard.setValue("Arming event detection  •  " + seconds
                    + " s remaining", CardState.INFO);
            return;
        }
        if (result != null) {
            setTextIfChanged(calibrationLabel, "TPS calibration: " + result.toDisplayText());
            calibrationCard.setValue("Complete  •  Recommended "
                    + F3.format(result.getRecommendedThreshold()) + " %/s", CardState.GOOD);
        } else {
            setTextIfChanged(calibrationLabel, "TPS calibration: not run");
            calibrationCard.setValue("Not run  •  Press Start TPS noise calibration", CardState.WAITING);
        }
    }

    void refreshSessionReview(String text) {
        setTextIfChanged(sessionReviewLabel, text);
    }

    void refreshRecommendationHistory(String text) {
        setTextIfChanged(recommendationHistoryText, text);
        recommendationHistoryText.setCaretPosition(0);
    }

    private static String sampleRateText(double sampleRateHz) {
        return "Sample rate: " + (sampleRateHz > 0.0 ? F1.format(sampleRateHz) + " Hz" : "n/a");
    }

    private static void setLabelTextIfChanged(JLabel label, String text) {
        if (!text.equals(label.getText())) {
            label.setText(text);
        }
    }

    private static void setTextIfChanged(JTextArea area, String text) {
        String normalized = text == null ? "" : text;
        if (!normalized.equals(area.getText())) {
            area.setText(normalized);
        }
    }
}
