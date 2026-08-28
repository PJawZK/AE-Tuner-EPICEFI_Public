package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/**
 * Canonical data roles used by the live transient-event detector.
 *
 * User-facing labels intentionally use the displayed TunerStudio/MegaLogViewer
 * names where known. Internal ECU names remain in the candidate list only so the
 * plugin can resolve the channels on different EPICEFI builds.
 */
public enum ChannelRole {
    TIME("Time", "Time", "seconds", "sec", "Seconds"),
    RPM("RPM", "RPM", "RPMValue", "instantRpm"),
    TPS("TPS", "TPS", "TPSValue", "TPS1"),
    MAP("MAP", "MAP", "MAPValue", "instantMAPValue"),
    BARO("baroPressure", "baroPressure", "Baro pressure", "baro"),
    FALLBACK_MAP("fallbackMap", "fallbackMap", "MAP estimate", "mapEstimate"),
    EFFECTIVE_MAP("effectiveMap", "effectiveMap", "Effective MAP"),
    MAP_PRED_ACTIVE("isMapPredictionActive", "isMapPredictionActive", "MAP prediction active"),
    MAP_PRED_RESET_CNT("predTimerResetCnt", "predTimerResetCnt", "MAP prediction timer reset count"),
    MAP_PRED_EVENT_OVER("mapPredEventOver", "mapPredEventOver", "MAP prediction event over"),
    LAMBDA("Lambda", "Lambda", "lambdaValue", "RealLambdaValue1", "Lambda 1"),
    AFR("AFR", "AFR", "AFRValue", "RealAFRValue"),
    TARGET_LAMBDA("Target lambda", "Target lambda", "targetLambda"),
    TARGET_AFR("Target AFR", "Target AFR", "targetAFR"),
    PW("Fuel: Last inj pulse width", "Fuel: Last inj pulse width", "actualLastInjection", "injectionPulseWidth"),
    INJ_DUTY("Injector duty cycle", "Injector duty cycle", "Fuel: injector duty cycle", "injectorDutyCycle"),
    IGNITION_TIMING("Timing: ignition",
            "Timing: ignition", "correctedIgnitionAdvance",
            "Ignition: Running advance", "runningAdvance",
            "Ignition timing", "ignitionAdvance", "actualIgnitionAdvance",
            "currentIgnitionAdvance", "ignitionTiming"),
    BOOST_TARGET("Boost: Target", "Boost: Target", "boostControlTarget", "boostTarget", "boostTargetKpa", "targetBoost"),
    ENGINE_RUNNING("running", "ready", "running", "Engine running", "isRunning"),
    ENGINE_CRANKING("cranking", "crank", "cranking", "Engine cranking", "isCranking"),
    IGNITION_ON("ignitionOn", "ignitionOn", "Ignition On", "ignitionOnState"),
    MAIN_RELAY_HAS_IGN("Main relay: Has IGN voltage",
            "Main relay: Has IGN voltage", "mainRelayHasIgnVoltage", "hasIgnitionVoltage"),
    IGNITION_FAULT("ignitionFault", "ignitionFault"),
    INJECTOR_FAULT("injectorFault", "injectorFault"),
    TRIGGER_ERROR("Error: Trigger", "Error: Trigger", "cel_trigger_error", "Trigger Error"),
    TRIGGER_ERROR_COUNT("Trigger Error Counter",
            "Trigger Error Counter", "totalTriggerErrorCounter", "Total Trigger Error Counter"),
    TOTAL_SPARK_CUT("totalSparkCut", "totalSparkCut"),
    IGN_CUT_CODE("Ign: Cut Code", "sparkCutReason", "Ign: Cut Code"),
    FUEL_CUT_CODE("Fuel: Cut Code", "fuelCutReason", "Fuel: Cut Code"),
    STOP_ENGINE_CODE("stopEngineCode", "stopEngineCode"),
    IGN_OVERDWELL("Ignition: overDwellNotScheduled",
            "overDwellNotScheduledCounter", "Ignition: overDwellNotScheduled", "overDwellNotScheduled"),
    IGN_OVERCHARGE_WARNINGS("Ignition: overcharge warnings",
            "dwellOverChargeCounter", "Ignition: overcharge warnings", "overchargeWarnings"),
    IGN_UNDERCHARGE_WARNINGS("Ignition: undecharge warnings",
            "dwellUnderChargeCounter", "Ignition: undecharge warnings", "undechargeWarnings", "underchargeWarnings"),
    IGN_SPARK_OUT_OF_ORDER("Ignition: sparkOutOfOrder",
            "sparkOutOfOrderCounter", "Ignition: sparkOutOfOrder", "sparkOutOfOrder"),
    FUEL_PRESSURE_HIGH("Fuel pressure _high",
            "Fuel pressure _high", "Fuel pressure (high)", "Fuel Pressure High",
            "highFuelPressure", "fuelPressureHigh", "fuelPressure"),
    FUEL_PRESSURE_LOW("Fuel pressure _low",
            "Fuel pressure _low", "Fuel pressure (low)", "Fuel Pressure Low",
            "lowFuelPressure", "fuelPressureLow"),
    AE_ADD_MS("Fuel: TPS AE add fuel ms", "Fuel: TPS AE add fuel ms", "tpsAccelFuel"),
    EXTRA_FUEL("Fuel: TPS extraFuel", "Fuel: TPS extraFuel", "extraFuel"),
    TPS_FROM("Fuel: TPS AE from", "Fuel: TPS AE from", "tpsFrom"),
    TPS_TO("Fuel: TPS AE to", "Fuel: TPS AE to", "tpsTo"),
    DELTA_TPS("Fuel: TPS AE change", "Fuel: TPS AE change", "deltaTps"),
    AE_DELTA_MAX_STEP("Fuel: AE delta max step", "Fuel: AE delta max step", "aeDeltaMaxStep"),
    AE_DELTA_TIMED("Fuel: AE delta timed", "Fuel: AE delta timed", "aeDeltaTimed"),
    AE_DELTA_SPAN("Fuel: AE delta span", "Fuel: AE delta span", "aeDeltaSpan"),
    AE_DELTA_FLOOR("Fuel: AE delta from floor", "Fuel: AE delta from floor", "aeDeltaFloor"),
    AE_DELTA_NEWEST_PAIR("Fuel: AE delta newest pair", "Fuel: AE delta newest pair", "aeDeltaNewestPair"),
    AE_WINDOW_SAMPLES("Fuel: AE window samples", "Fuel: AE window samples", "aeWindowSamples"),
    AE_WINDOW_MS("Fuel: AE window", "Fuel: AE window", "aeWindowMs"),
    AE_DELTA_STRIDE("Fuel: AE delta stride", "Fuel: AE delta stride", "aeDeltaStride"),
    SMOOTHED_DELTA_TPS("smoothedDeltaTps", "smoothedDeltaTps"),
    ACCEL_THRESHOLD("AccelThreshold", "AccelThreshold"),
    TPS_AE_CYCLE_MULT("tpsAeCycleMult", "tpsAeCycleMult"),
    TPS_AE_CYCLE_CNT("Engine cycles AE duration", "Engine cycles AE duration", "tpsAeCycleCnt"),
    AE_ABOVE_THRESHOLD("Fuel: TPS AE Active", "Fuel: TPS AE Active", "isAboveAccelThreshold"),
    AE_EVENT_JUST_OCCURRED("AE event just occurred", "AE event just occurred", "m_accelEventJustOccurred"),
    AE_EXTRA_SHOT("Fuel: TPSAE ExtraShot", "Fuel: TPSAE ExtraShot", "extraShot"),
    INSTANT_PULSE_PW("aeInstantPulsePw", "aeInstantPulsePw"),
    INSTANT_PULSE_CNT("aeInstantPulseCnt", "aeInstantPulseCnt"),
    EXTRA_SHOT_TIMER("m_tpsExtraShotTimer", "m_tpsExtraShotTimer"),
    WALL_CORRECTION("Fuel: wall correction", "Fuel: wall correction", "wallFuelCorrectionValue", "wallCorrection"),
    WALL_WETTING_PW("fuel wallwetting injection time", "fuel wallwetting injection time", "wallFuelCorrection", "wallWettingInjectionTime"),
    DFCO("dfcoActive", "dfcoActive"),
    FUEL_CUT("Total fuel cut", "Total fuel cut", "totalFuelCut"),
    STFT1("STFT: Bank 1", "STFT: Bank 1", "stftCorrection1", "Gego", "egoCorrectionForVeAnalyze"),
    COOLANT("CLT", "CLT", "coolant"),
    IAT("MAT", "MAT", "intake", "IAT"),
    BATTERY("Batt V", "Batt V", "VBatt"),
    GEAR("Detected gear", "Detected gear", "detectedGear", "Gear"),
    VSS("Vehicle speed", "Vehicle speed", "vehicleSpeedKph", "vehicleSpeedKphFrontAvg");

    private final String label;
    private final String[] candidates;

    ChannelRole(String label, String... candidates) {
        this.label = label;
        this.candidates = candidates;
    }

    public String getLabel() {
        return label;
    }

    public String[] getCandidates() {
        return candidates;
    }
}
