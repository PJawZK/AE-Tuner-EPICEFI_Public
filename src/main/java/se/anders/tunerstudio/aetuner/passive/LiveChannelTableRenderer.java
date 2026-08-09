package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import javax.swing.table.DefaultTableModel;
import java.text.DecimalFormat;
import java.util.EnumMap;

/** Presentation-only updater for the resolved live-channel table. */
final class LiveChannelTableRenderer {
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private LiveChannelTableRenderer() { }

    static void update(DefaultTableModel model,
                       EnumMap<ChannelRole, String> names,
                       EnumMap<ChannelRole, Double> values) {
        ChannelRole[] roles = ChannelRole.values();
        if (model.getRowCount() != roles.length) {
            model.setRowCount(0);
            for (ChannelRole role : roles) {
                model.addRow(new Object[]{role.getLabel(), "", "", ""});
            }
        }

        // Updating existing cells avoids repeated table revalidation and layout
        // churn during the panel's periodic refresh.
        for (int row = 0; row < roles.length; row++) {
            ChannelRole role = roles[row];
            String channel = names.get(role);
            Double value = values.get(role);
            setIfChanged(model, row, 0, role.getLabel());
            setIfChanged(model, row, 1, channel == null ? "not found" : channel);
            setIfChanged(model, row, 2, value == null ? "" : formatValue(role, value.doubleValue()));
            setIfChanged(model, row, 3, channel == null ? "missing" : "subscribed");
        }
    }

    private static String formatValue(ChannelRole role, double value) {
        if (role == ChannelRole.LAMBDA || role == ChannelRole.TARGET_LAMBDA
                || role == ChannelRole.PW || role == ChannelRole.AE_ADD_MS
                || role == ChannelRole.WALL_WETTING_PW || role == ChannelRole.INSTANT_PULSE_PW) {
            return F3.format(value);
        }
        return F2.format(value);
    }

    private static void setIfChanged(DefaultTableModel model, int row, int column, Object value) {
        Object old = model.getValueAt(row, column);
        if (old == null ? value != null : !old.equals(value)) {
            model.setValueAt(value, row, column);
        }
    }
}
