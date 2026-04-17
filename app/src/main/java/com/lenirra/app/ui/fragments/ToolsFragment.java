package com.lenirra.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.lenirra.app.R;
import com.lenirra.app.utils.RootUtils;

public class ToolsFragment extends Fragment {

    private EditText etTerminalInput;
    private TextView tvTerminalOutput;
    private ScrollView svTerminal;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_tools, container, false);
        initViews(v);
        return v;
    }

    private void initViews(View v) {
        etTerminalInput = v.findViewById(R.id.et_terminal_input);
        tvTerminalOutput = v.findViewById(R.id.tv_terminal_output);
        svTerminal = v.findViewById(R.id.sv_terminal);

        v.findViewById(R.id.btn_run_cmd).setOnClickListener(vv -> runCommand());
        v.findViewById(R.id.btn_clear_terminal).setOnClickListener(vv -> {
            tvTerminalOutput.setText("# KernelForge Terminal (root)\n");
        });

        // Quick action buttons
        v.findViewById(R.id.btn_drop_caches).setOnClickListener(vv -> {
            showConfirmDialog("Drop caches? (frees RAM)", () -> {
                execAndShow("sync && echo 3 > /proc/sys/vm/drop_caches");
                Toast.makeText(requireContext(), "Caches dropped", Toast.LENGTH_SHORT).show();
            });
        });

        v.findViewById(R.id.btn_logcat).setOnClickListener(vv ->
                execAndShow("logcat -d -t 100 *:W"));

        v.findViewById(R.id.btn_wakelock).setOnClickListener(vv ->
                execAndShow("cat /sys/kernel/debug/wakeup_sources 2>/dev/null | " +
                            "awk '{if(NR>1 && $4>0) print}' | sort -k4 -n -r | head -20"));

        v.findViewById(R.id.btn_top_procs).setOnClickListener(vv ->
                execAndShow("top -b -n 1 | head -20"));

        v.findViewById(R.id.btn_battery_cal).setOnClickListener(vv -> {
            showConfirmDialog("Calibrate battery? Device will reboot.", () -> {
                execAndShow("rm -f /data/system/batterystats.bin");
                new android.os.Handler().postDelayed(() ->
                        RootUtils.runCommand("reboot"), 2000);
            });
        });

        v.findViewById(R.id.btn_force_stop_bg).setOnClickListener(vv -> {
            new Thread(() -> {
                String result = RootUtils.runCommand("am kill-all");
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "BG apps stopped", Toast.LENGTH_SHORT).show());
            }).start();
        });

        v.findViewById(R.id.btn_prop_editor).setOnClickListener(vv ->
                execAndShow("getprop | head -50"));

        v.findViewById(R.id.btn_net_info).setOnClickListener(vv ->
                execAndShow("ip addr && echo '---' && cat /proc/net/if_inet6 2>/dev/null"));

        v.findViewById(R.id.btn_reboot_recovery).setOnClickListener(vv -> {
            showConfirmDialog("Reboot to Recovery?", () ->
                    RootUtils.runCommand("reboot recovery"));
        });

        v.findViewById(R.id.btn_reboot_bootloader).setOnClickListener(vv -> {
            showConfirmDialog("Reboot to Bootloader?", () ->
                    RootUtils.runCommand("reboot bootloader"));
        });

        v.findViewById(R.id.btn_reboot_normal).setOnClickListener(vv -> {
            showConfirmDialog("Reboot device?", () ->
                    RootUtils.runCommand("reboot"));
        });
    }

    private void runCommand() {
        String cmd = etTerminalInput.getText().toString().trim();
        if (cmd.isEmpty()) return;
        etTerminalInput.setText("");
        appendTerminal("$ " + cmd);
        new Thread(() -> {
            String result = RootUtils.runCommand(cmd);
            requireActivity().runOnUiThread(() -> {
                appendTerminal(result.isEmpty() ? "(no output)" : result);
                svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
    }

    private void execAndShow(String cmd) {
        appendTerminal("$ " + cmd);
        new Thread(() -> {
            String result = RootUtils.runCommand(cmd);
            requireActivity().runOnUiThread(() -> {
                appendTerminal(result.isEmpty() ? "(no output)" : result);
                svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
    }

    private void appendTerminal(String text) {
        String current = tvTerminalOutput.getText().toString();
        tvTerminalOutput.setText(current + "\n" + text);
    }

    private void showConfirmDialog(String message, Runnable onConfirm) {
        new AlertDialog.Builder(requireContext(), R.style.Dialog_KernelForge)
                .setTitle("Confirm")
                .setMessage(message)
                .setPositiveButton("OK", (d, w) -> new Thread(onConfirm).start())
                .setNegativeButton("Cancel", null)
                .show();
    }
}
