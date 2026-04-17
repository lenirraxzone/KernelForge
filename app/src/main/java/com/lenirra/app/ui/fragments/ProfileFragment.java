package com.lenirra.app.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.lenirra.app.R;
import com.lenirra.app.utils.PrefsManager;
import com.lenirra.app.utils.RootUtils;

public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);
        setupGeneral(v);
        setupSecurity(v);
        setupAbout(v);
        return v;
    }

    private void setupGeneral(View v) {
        SwitchMaterial switchBoot = v.findViewById(R.id.switch_apply_boot);
        SwitchMaterial switchDropCaches = v.findViewById(R.id.switch_drop_caches_boot);

        switchBoot.setChecked(PrefsManager.isApplyOnBoot());
        switchBoot.setOnCheckedChangeListener((btn, checked) ->
                PrefsManager.setApplyOnBoot(checked));

        switchDropCaches.setChecked(PrefsManager.isDropCachesOnBoot());
        switchDropCaches.setOnCheckedChangeListener((btn, checked) ->
                PrefsManager.setDropCachesOnBoot(checked));
    }

    private void setupSecurity(View v) {
        SwitchMaterial switchSelinux = v.findViewById(R.id.switch_selinux_permissive);
        SwitchMaterial switchTcpBbr  = v.findViewById(R.id.switch_tcp_bbr);
        TextView tvSelinuxCurrent    = v.findViewById(R.id.tv_selinux_current);

        // Baca SELinux status saat ini
        new Thread(() -> {
            String status = RootUtils.getSelinuxStatus();
            boolean isPermissive = status.equalsIgnoreCase("Permissive") || status.equals("0");
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                tvSelinuxCurrent.setText(isPermissive ? "Permissive" : "Enforcing");
                tvSelinuxCurrent.setTextColor(isPermissive ? 0xFFFF6B35 : 0xFF2ED573);
                switchSelinux.setChecked(isPermissive);
            });
        }).start();

        switchSelinux.setOnCheckedChangeListener((btn, checked) -> {
            new Thread(() -> {
                if (checked) {
                    RootUtils.setSelinuxPermissive();
                } else {
                    RootUtils.setSelinuxEnforcing();
                }
                String newStatus = checked ? "Permissive" : "Enforcing";
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    tvSelinuxCurrent.setText(newStatus);
                    tvSelinuxCurrent.setTextColor(checked ? 0xFFFF6B35 : 0xFF2ED573);
                    Toast.makeText(requireContext(),
                            "SELinux: " + newStatus, Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        switchTcpBbr.setChecked(PrefsManager.isTcpBbrEnabled());
        switchTcpBbr.setOnCheckedChangeListener((btn, checked) -> {
            PrefsManager.setTcpBbrEnabled(checked);
            if (checked) {
                new Thread(() -> RootUtils.enableTcpBbr()).start();
                Toast.makeText(requireContext(), "TCP BBR enabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAbout(View v) {
        TextView tvVersion = v.findViewById(R.id.tv_app_version);
        tvVersion.setText("KernelForge");

        v.findViewById(R.id.btn_github).setOnClickListener(vv ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/lenirraxzone"))));
    }
}
