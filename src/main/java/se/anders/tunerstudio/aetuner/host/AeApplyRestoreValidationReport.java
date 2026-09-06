package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningArea;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Deterministic evidence export for one physical-validation Lab session. */
public final class AeApplyRestoreValidationReport {
    private AeApplyRestoreValidationReport() { }

    public static String csv(AeApplyRestoreValidationLabModel model) {
        if (model == null) throw new IllegalArgumentException("validation Lab model is required");
        String generatedAt = Instant.now().toString();
        StringBuilder out = new StringBuilder(256 * 1024);
        out.append("report_generated_at_utc,plugin_version,authority_signature,")
                .append("guided_areas,guided_tasks,display_label,controller_name,")
                .append("flat_index,coordinates,dimensions,catalog_shape,controller_kind,")
                .append("value_type,unit,scale,minimum,maximum,decimals,bit_range,")
                .append("original_value,temporary_value,apply_readback,restore_readback,")
                .append("result,note,message\n");

        for (AeApplyRestoreValidationTargets.Target target
                : AeApplyRestoreValidationTargets.all()) {
            AeApplyRestoreValidationLabModel.Record record = model.recordFor(target);
            if (record == null) {
                throw new IllegalStateException(
                        "validation report missing physical record " + target.identity());
            }
            AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
            csv(out, generatedAt);
            csv(out, AeTunerPlugin.VERSION);
            csv(out, AeControllerDefinitionCatalog.AUTHORITY_SIGNATURE);
            csv(out, areaText(target.getAreas()));
            csv(out, taskText(target.getTasks()));
            csv(out, target.getParameter().getDisplayName());
            csv(out, target.getControllerName());
            csv(out, target.isIndexed() ? Integer.toString(target.getFlatIndex()) : "");
            csv(out, target.coordinateText());
            csv(out, dimensionsText(definition.getDimensions()));
            csv(out, target.getParameter().getShape().name());
            csv(out, definition.getKind().name());
            csv(out, definition.getValueType().name());
            csv(out, definition.getUnit());
            csv(out, number(definition.getScale()));
            csv(out, number(definition.getMinimum()));
            csv(out, number(definition.getMaximum()));
            csv(out, Integer.toString(definition.getDecimals()));
            csv(out, definition.getBitRange());
            csv(out, numberOrBlank(record.getOriginalValue()));
            csv(out, numberOrBlank(record.getTemporaryValue()));
            csv(out, numberOrBlank(record.getApplyReadback()));
            csv(out, numberOrBlank(record.getRestoreReadback()));
            csv(out, record.getStatus().label);
            csv(out, record.getNote());
            csvLast(out, record.getMessage());
        }
        return out.toString();
    }

    public static String summary(AeApplyRestoreValidationLabModel model) {
        if (model == null) throw new IllegalArgumentException("validation Lab model is required");
        StringBuilder out = new StringBuilder();
        out.append("AE Tuner Apply/Restore Validation Lab\n")
                .append("====================================\n")
                .append("Plugin: ").append(AeTunerPlugin.VERSION).append('\n')
                .append("Frozen controller authority: ")
                .append(AeControllerDefinitionCatalog.AUTHORITY_SIGNATURE).append('\n')
                .append("Physical target count: ").append(model.totalCount()).append('\n')
                .append("PASS: ").append(model.count(
                        AeApplyRestoreValidationLabModel.ValidationStatus.PASS)).append('\n')
                .append("FAIL: ").append(model.count(
                        AeApplyRestoreValidationLabModel.ValidationStatus.FAIL)).append('\n')
                .append("SKIP: ").append(model.count(
                        AeApplyRestoreValidationLabModel.ValidationStatus.SKIP)).append('\n')
                .append("NOT TESTED: ").append(model.count(
                        AeApplyRestoreValidationLabModel.ValidationStatus.NOT_TESTED)).append('\n')
                .append("\nSafety boundary: working-tune Apply/Restore only; no burn.\n")
                .append("PASS means the recorded temporary Apply readback and exact original Restore readback both matched.\n")
                .append("Shared Guided task aliases are represented once per unique physical controller target in validation-results.csv.\n");
        return out.toString();
    }

    private static String areaText(List<GuidedTuningArea> areas) {
        StringBuilder text = new StringBuilder();
        for (GuidedTuningArea area : areas) {
            if (text.length() > 0) text.append(" | ");
            text.append(area.displayName);
        }
        return text.toString();
    }

    private static String taskText(List<GuidedTuningRecipe> tasks) {
        StringBuilder text = new StringBuilder();
        for (GuidedTuningRecipe task : tasks) {
            if (text.length() > 0) text.append(" | ");
            text.append(task.displayName);
        }
        return text.toString();
    }

    private static String dimensionsText(int[] dimensions) {
        if (dimensions == null || dimensions.length == 0) return "";
        StringBuilder text = new StringBuilder();
        for (int dimension : dimensions) {
            if (text.length() > 0) text.append('x');
            text.append(dimension);
        }
        return text.toString();
    }

    private static String numberOrBlank(double value) {
        return Double.isFinite(value) ? number(value) : "";
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) return "";
        return String.format(Locale.ROOT, "%.10f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static void csv(StringBuilder out, String value) {
        out.append(escape(value)).append(',');
    }

    private static void csvLast(StringBuilder out, String value) {
        out.append(escape(value)).append('\n');
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0
                && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
