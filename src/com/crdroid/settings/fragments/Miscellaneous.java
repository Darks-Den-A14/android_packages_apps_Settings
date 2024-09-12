/*
 * Copyright (C) 2016-2023 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.UserHandle;
import android.provider.Settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog; 

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.crdroid.SystemRestartUtils;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.preferences.GlobalSettingSwitchPreference;
import com.crdroid.settings.preferences.SystemPropertyListPreference;
import com.crdroid.settings.preferences.SecureSettingListPreference;
import com.crdroid.settings.preferences.SecureSettingSwitchPreference;
import com.crdroid.settings.preferences.SystemSettingListPreference;
import com.crdroid.settings.preferences.SystemSettingSeekBarPreference;
import com.crdroid.settings.preferences.SystemSettingSwitchPreference;

import com.crdroid.settings.fragments.misc.SensorBlock;

import java.util.List;

import lineageos.providers.LineageSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@SearchIndexable
public class Miscellaneous extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "Miscellaneous";

    private static final String BACKUP_PERSONALIZATION_SETTINGS = "backup_personalization_settings";
    private static final String RESTORE_PERSONALIZATION_SETTINGS = "restore_personalization_settings";

    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;

    private static final String POCKET_JUDGE = "pocket_judge";
    private static final String SYS_GAMES_SPOOF = "persist.sys.pixelprops.games";
    private static final String SYS_PROP_OPTIONS = "persist.sys.pixelprops.all";
    private static final String SYS_PHOTOS_SPOOF = "persist.sys.pixelprops.gphotos";
    private static final String SYS_NETFLIX_SPOOF = "persist.sys.pixelprops.netflix";
    
    private static final String SYS_GAMEPROP_ENABLED = "persist.sys.gameprops.enabled";
    private static final String KEY_GAME_PROPS_JSON_FILE_PREFERENCE = "game_props_json_file_preference";
    private static final String KEY_PIF_JSON_FILE_PREFERENCE = "pif_json_file_preference";

    private Preference mPocketJudge;
    private Preference mPropOptions;

    private Preference mPifJsonFilePreference;

    private Preference mGamePropsJsonFilePreference;
    private Preference mGamePropsSpoof;

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        addPreferencesFromResource(R.xml.crdroid_settings_misc);
        mGamePropsSpoof = findPreference(SYS_GAMEPROP_ENABLED);
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources res = getResources();

        mPifJsonFilePreference = findPreference(KEY_PIF_JSON_FILE_PREFERENCE);
        mGamePropsJsonFilePreference = findPreference(KEY_GAME_PROPS_JSON_FILE_PREFERENCE);    
        mGamePropsSpoof.setOnPreferenceChangeListener(this);
        mPropOptions = (Preference) findPreference(SYS_PROP_OPTIONS);
        mPropOptions.setOnPreferenceChangeListener(this);
        mPocketJudge = (Preference) prefScreen.findPreference(POCKET_JUDGE);
        boolean mPocketJudgeSupported = res.getBoolean(
                com.android.internal.R.bool.config_pocketModeSupported);
        if (!mPocketJudgeSupported)
            prefScreen.removePreference(mPocketJudge);

        Context mContext = getActivity().getApplicationContext();

        // Initialize ActivityResultLaunchers for file selection
        backupLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    Log.d(TAG, "Backup URI: " + uri.toString());
                    backupSettings(mContext, uri);
                } else {
                    Log.e(TAG, "Backup URI is null");
                }
            } else {
                Log.e(TAG, "Backup activity result not OK or data is null");
            }
        });

        restoreLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    Log.d(TAG, "Restore URI: " + uri.toString());
                    restoreSettings(mContext, uri);
                } else {
                    Log.e(TAG, "Restore URI is null");
                }
            } else {
                Log.e(TAG, "Restore activity result not OK or data is null");
            }
        });

        // Backup settings
        Preference backupPref = findPreference(BACKUP_PERSONALIZATION_SETTINGS);
        backupPref.setOnPreferenceClickListener(preference -> {
            Log.d(TAG, "Backup option clicked");
            chooseFileLocationForBackup();
            return true;
        });

        // Restore settings
        Preference restorePref = findPreference(RESTORE_PERSONALIZATION_SETTINGS);
        restorePref.setOnPreferenceClickListener(preference -> {
            Log.d(TAG, "Restore option clicked");
            chooseFileForRestore();
            return true;
        });

    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
    if (preference == mPifJsonFilePreference) {
        mPifJsonFilePreference.setOnPreferenceClickListener(pref -> {
            openFileSelector(10001);
            return true;
        });
        return true;
    } else if (preference == mGamePropsJsonFilePreference) {
        mGamePropsJsonFilePreference.setOnPreferenceClickListener(pref -> {
            openFileSelector(10002);
            return true;
        });
        return true;
    } else if ("show_pif_properties".equals(preference.getKey())) {
        showPropertiesDialog();
        return true;
    }
    return super.onPreferenceTreeClick(preference); // Default handling
}

   private void openFileSelector(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        startActivityForResult(intent, requestCode);
   }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == 10001) {
                    loadPifJson(uri);
                } else if (requestCode == 10002) {
                    loadGameSpoofingJson(uri);
                }
            }
        }
    }

    private void showPropertiesDialog() {
        StringBuilder properties = new StringBuilder();
        try {
            JSONObject jsonObject = new JSONObject();
            String[] keys = {
                "persist.sys.pihooks_ID",
                "persist.sys.pihooks_BRAND",
                "persist.sys.pihooks_DEVICE",
                "persist.sys.pihooks_FINGERPRINT",
                "persist.sys.pihooks_MANUFACTURER",
                "persist.sys.pihooks_MODEL",
                "persist.sys.pihooks_PRODUCT",
                "persist.sys.pihooks_SECURITY_PATCH",
                "persist.sys.pihooks_DEVICE_INITIAL_SDK_INT"
            };
            for (String key : keys) {
                String value = SystemProperties.get(key, null);
                if (value != null) {
                    String buildKey = key.replace("persist.sys.pihooks_", "");
                    jsonObject.put(buildKey, value);
                }
            }
            properties.append(jsonObject.toString(4));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON from properties", e);
            properties.append(getString(R.string.error_loading_properties));
        }
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.show_pif_properties_title)
            .setMessage(properties.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void loadPifJson(Uri uri) {
        Log.d(TAG, "Loading PIF JSON from URI: " + uri.toString());
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "PIF JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = jsonObject.getString(key);
                    Log.d(TAG, "Setting PIF property: persist.sys.pihooks_" + key + " = " + value);
                    SystemProperties.set("persist.sys.pihooks_" + key, value);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading PIF JSON or setting properties", e);
        }
        mHandler.postDelayed(() -> {
            SystemRestartUtils.showSystemRestartDialog(getContext());
        }, 1250);
    }

    private void loadGameSpoofingJson(Uri uri) {
        Log.d(TAG, "Loading Game Props JSON from URI: " + uri.toString());
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "Game Props JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (key.startsWith("PACKAGES_") && !key.endsWith("_DEVICE")) {
                        String deviceKey = key + "_DEVICE";
                        if (jsonObject.has(deviceKey)) {
                            JSONObject deviceProps = jsonObject.getJSONObject(deviceKey);
                            JSONArray packages = jsonObject.getJSONArray(key);
                            for (int i = 0; i < packages.length(); i++) {
                                String packageName = packages.getString(i);
                                Log.d(TAG, "Spoofing package: " + packageName);
                                setGameProps(packageName, deviceProps);
                            }
                        }            
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading Game Props JSON or setting properties", e);
        }
        mHandler.postDelayed(() -> {
            SystemRestartUtils.showSystemRestartDialog(getContext());
        }, 1250);
    }

    private void setGameProps(String packageName, JSONObject deviceProps) {
        try {
            for (Iterator<String> it = deviceProps.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = deviceProps.getString(key);
                String systemPropertyKey = "persist.sys.gameprops." + packageName + "." + key;
                SystemProperties.set(systemPropertyKey, value);
                Log.d(TAG, "Set system property: " + systemPropertyKey + " = " + value);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing device properties", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mGamePropsSpoof || preference == mPropOptions) {
                    SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.POCKET_JUDGE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.THREE_FINGER_GESTURE, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.AUTO_BRIGHTNESS_ONE_SHOT, 0, UserHandle.USER_CURRENT);
        SystemProperties.set(SYS_GAMES_SPOOF, "false");
        SystemProperties.set(SYS_PHOTOS_SPOOF, "true");
        SystemProperties.set(SYS_NETFLIX_SPOOF, "false");
        SensorBlock.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

private void chooseFileLocationForBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "personalization_settings_backup.json");
        Log.d(TAG, "Launching file picker for backup");
        backupLauncher.launch(intent);
    }

    private void chooseFileForRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        Log.d(TAG, "Launching file picker for restore");
        restoreLauncher.launch(intent);
    }

    private void backupSettings(Context context, Uri uri) {
        try {
            JSONObject json = new JSONObject();

            // Add system settings
            addSystemSettingKeys(json);

            // Add secure settings
            addSecureSettingKeys(json);

            // Add global settings
            addGlobalSettingKeys(json);

            // Write the JSON object to the backup file
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                if (outputStream != null) {
                    outputStream.write(json.toString().getBytes());
                    Toast.makeText(getActivity(), "Personalization settings backed up successfully!", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Failed to backup settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void addSystemSettingKeys(JSONObject json) throws JSONException {
        String[] keys = {"depth_wallpaper_enabled", "depth_wallpaper_subject_image_uri", "depth_wallpaper_opacity", "depth_wallpaper_offset_x", "depth_wallpaper_offset_y", "hide_developer_status_settings", "hide_screen_capture_status", "no_storage_restrict", "sensor_block_settings", "navbar_pulse_enabled", "lockscreen_pulse_enabled", "ambient_pulse_enabled", "audio_effect_mode_enabled", "statusbar_battery_bar", "statusbar_battery_bar_thickness", "statusbar_battery_bar_style", "statusbar_battery_bar_blend_color", "statusbar_battery_bar_blend_color_reverse", "statusbar_battery_bar_color", "statusbar_battery_bar_battery_low_color", "statusbar_battery_bar_animate", "statusbar_battery_bar_enable_charging_color", "statusbar_battery_bar_charging_color", "shake_gestures_enabled", "shake_gestures_action", "shake_gestures_intensity", "three_finger_gesture_action", "three_finger_long_press_action", "hardware_keys_disable", "swap_capacitive_keys", "additional_buttons", "anbi_enabled", "click_partial_screenshot", "power_menu", "power_end_call", "torch_long_press_power_gesture", "torch_long_press_power_timeout", "home_wake_screen", "home_answer_call", "hardware_keys_home_long_press", "hardware_keys_home_double_tap", "back_wake_screen", "hardware_keys_back_long_press", "menu_wake_screen", "hardware_keys_menu_press", "hardware_keys_menu_long_press", "assist_wake_screen", "hardware_keys_assist_press", "hardware_keys_assist_long_press", "app_switch_wake_screen", "hardware_keys_app_switch_press", "hardware_keys_app_switch_long_press", "camera_wake_screen", "camera_sleep_on_release", "camera_launch", "volume_wake_screen", "volume_answer_call", "volbtn_music_controls", "volume_key_cursor_control", "swap_volume_buttons", "theme_style", "notification_sound_vib_screen_on", "alert_slider_notifications", "notification_guts_kill_app_button", "island_notification", "less_boring_heads_up", "heads_up_timeout", "flashlight_on_call", "flashlight_on_call_ignore_dnd", "flashlight_on_call_rate", "battery_lights", "notification_lights", "sensor_block", "game_props_json_file_preference", "update_pif_json", "pif_json_file_preference", "wiki_link", "android.theme.customization.lockscreen_clock_font", "depth_wallpaper", "kg_user_switcher_enabled", "ls_media_art_enabled", "ls_media_art_filter", "ls_media_art_fade_level", "lockscreen_battery_info", "lockscreen_weather_enabled", "lockscreen_weather_location", "lockscreen_weather_text", "double_tap_sleep_lockscreen", "udfps_icon_picker", "udfps_recognizing_animation_preview", "fp_success_vibrate", "fp_error_vibrate", "enable_ripple_effect", "lockscreen_enable_power_menu", "notification_light_color_auto", "notification_light_pulse_override", "notification_light_screen_on_enable", "notification_light_pulse_custom_enable", "status_bar_icons", "status_bar_clock", "clock", "network_traffic_settings", "data_disabled_icon", "show_fourg_icon", "bluetooth_show_battery", "wifi_standard_icon", "statusbar_colored_icons", "statusbar_max_notifications", "statusbar_notif_count", "status_bar_logo", "status_bar_logo_position", "status_bar_logo_style", "status_bar_battery_style", "status_bar_show_battery_percent", "status_bar_battery_text_charging", "batterybar", "statusbar_top_padding", "statusbar_left_padding", "statusbar_right_padding", "double_tap_sleep_gesture", "status_bar_brightness_control", "qs_quick_pulldown", "rising_changelog", "custom_aod_image_enabled", "lockscreen_custom_image", "lockscreen_widgets_enabled", "lockscreen_display_widgets", "main_custom_widgets1", "main_custom_widgets2", "custom_widgets1", "custom_widgets2", "custom_widgets3", "custom_widgets4", "screenshot", "onthego", "airplane", "users", "lockdown", "emergency", "devicecontrols", "doze_fragment", "bootanimation_fragment", "android.theme.customization.fonts", "android.theme.customization.icon_pack", "android.theme.customization.adaptive_icon_shape", "monet_engine", "android.theme.customization.navbar", "settings_theme_style", "android.theme.customization.signal_icon", "android.theme.customization.style", "custom_volume_styles", "android.theme.customization.wifi_icon", "charging_animation", "show_multi_user_avatar_on_homepage", "screen_off_animation", "adaptive_playback_timeout", "island_notification_now_playing", "display_cutout_force_fullscreen_settings", "gaming_mode", "gestures", "navigation", "security", "spoofing", "quickswitch", "enable_rotation_button", "smart_pixels", "backup_personalization_settings", "restore_personalization_settings", "sound_engine", "pulse_settings", "volume_steps", "adaptive_playback_settings", "screenshot_shutter_sound", "show_app_volume", "volume_sound_haptics", "vibrate_on_connect", "vibrate_on_callwaiting", "vibrate_on_disconnect", "status_bar_clock_auto_hide", "status_bar_clock_auto_hide_hduration", "status_bar_clock_auto_hide_sduration", "status_bar_clock_seconds", "statusbar_clock_chip", "status_bar_am_pm", "status_bar_clock_date_display", "status_bar_clock_date_position", "status_bar_clock_date_style", "status_bar_clock_date_format", "battery_light_pulse", "battery_light_full_charge_disabled", "qs_bt_auto_on", "qs_header_image", "qs_colored_icons", "qs_battery_style", "qs_show_battery_percent", "secure_lockscreen_qs_disabled", "qs_dual_tone", "qs_compact_media_player_mode", "qs_transparency", "qs_widgets_enabled", "qs_widgets_photo_showcase_enabled", "qs_widgets_photo_showcase_image", "qs_tile_ui_style", "qs_panel_style", "qs_tile_label_hide", "qs_tile_label_size", "qs_tile_vertical_layout", "qs_layout_columns", "qs_layout_columns_landscape", "qs_panel_row_count", "qs_panel_row_count_landscape", "qqs_layout_rows", "qs_tile_animation_style", "qs_tile_animation_duration", "qs_tile_animation_interpolator", "qs_show_data_usage", "notification_material_dismiss", "always_on_display_schedule", "doze_device_settings", "edge_light_settings", "navbar_visibility", "pixel_nav_animation", "navigation_bar_menu_arrow_keys", "hide_ime_space_enable", "navigation_back_long_press", "navigation_home_long_press", "navigation_home_double_tap", "navigation_app_switch_long_press", "navigation_bar_edge_long_swipe", "smart_pixels_enable", "smart_pixels_pattern", "smart_pixels_on_power_save", "smart_pixels_shift_timeout", "qs_header_provider", "daylight_header_pack", "custom_header_browse", "file_header_select", "status_bar_custom_header_height", "status_bar_custom_header_shadow", "edge_light_enabled", "edge_light_always_trigger_on_pulse", "edge_light_repeat_animation", "edge_light_color_mode", "edge_light_custom_color"}; // Add more keys as needed
        for (String key : keys) {
            json.put(key, Settings.System.getString(getContext().getContentResolver(), key));
        }
    }

    private void addSecureSettingKeys(JSONObject json) throws JSONException {
        String[] keys = {"window_ignore_secure", "pulse_render_style", "pulse_smoothing_enabled", "pulse_vertical_mirror", "visualizer_center_mirrored", "pulse_color_mode", "pulse_custom_gravity", "pulse_color_user", "pulse_lavalamp_speed", "pulse_custom_dimen", "pulse_custom_div", "pulse_filled_block_size", "pulse_empty_block_size", "pulse_custom_fudge_factor", "pulse_solid_units_rounded", "pulse_solid_units_opacity", "pulse_solid_units_count", "pulse_solid_fudge_factor", "doze_always_on_auto_mode", "monet_engine_custom_color", "monet_engine_color_override", "monet_engine_custom_bgcolor", "monet_engine_bgcolor_override", "monet_engine_luminance_factor", "monet_engine_chroma_factor", "monet_engine_tint_background", "show_clipboard_overlay", "screen_off_udfps_enabled", "enable_camera_privacy_indicator", "enable_location_privacy_indicator", "enable_projection_privacy_indicator", "advanced_reboot", "advanced_reboot_secured", "gms_enabled", "pocket_mode_enabled", "always_on_pocket_mode_enabled", "volume_panel_on_left", "volume_show_media_button", "volume_show_volume_percent", "volume_dialog_dismiss_timeout", "qs_show_brightness_slider", "qs_brightness_slider_position", "qs_show_auto_brightness", "doze_enabled", "doze_always_on", "doze_on_charge", "pulse_on_new_tracks", "doze_pick_up_gesture", "doze_tilt_gesture", "doze_handwave_gesture", "doze_pocket_gesture", "raise_to_wake_gesture", "doze_gesture_vibrate", "navbar_layout_views", "navbar_inverse_layout", "network_traffic_location", "network_traffic_mode", "network_traffic_autohide", "network_traffic_autohide_threshold", "network_traffic_refresh_interval", "network_traffic_units", "network_traffic_hidearrow"}; // Add more keys as needed
        for (String key : keys) {
            json.put(key, Settings.Secure.getString(getContext().getContentResolver(), key));
        }
    }

    private void addGlobalSettingKeys(JSONObject json) throws JSONException {
        String[] keys = {"heads_up_notifications_enabled"}; // Add more keys as needed
        for (String key : keys) {
            json.put(key, Settings.Global.getString(getContext().getContentResolver(), key));
        }
    }

    private void restoreSettings(Context context, Uri uri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                StringBuilder builder = new StringBuilder();
                int ch;
                while ((ch = inputStream.read()) != -1) {
                    builder.append((char) ch);
                }

                JSONObject json = new JSONObject(builder.toString());

                // Iterate through the JSON object and restore the values
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = json.get(key);

                    if (value instanceof Integer || value instanceof String) {
                        applySetting(context, key, value);
                    }
                }

                // Force refresh settings
                context.getContentResolver().notifyChange(Settings.System.CONTENT_URI, null);
                context.getContentResolver().notifyChange(Settings.Secure.CONTENT_URI, null);
                context.getContentResolver().notifyChange(Settings.Global.CONTENT_URI, null);

                Toast.makeText(getActivity(), "Personalization settings restored successfully!", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Failed to restore settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void applySetting(Context context, String key, Object value) {
        ContentResolver resolver = context.getContentResolver();

        if (isSystemSettingKey(key)) {
            if (value instanceof Integer) {
                Settings.System.putInt(resolver, key, (Integer) value);
            } else if (value instanceof String) {
                Settings.System.putString(resolver, key, (String) value);
            }
        } else if (isSecureSettingKey(key)) {
            if (value instanceof Integer) {
                Settings.Secure.putInt(resolver, key, (Integer) value);
            } else if (value instanceof String) {
                Settings.Secure.putString(resolver, key, (String) value);
            }
        } else if (isGlobalSettingKey(key)) {
            if (value instanceof Integer) {
                Settings.Global.putInt(resolver, key, (Integer) value);
            } else if (value instanceof String) {
                Settings.Global.putString(resolver, key, (String) value);
            }
        }
    }

    private boolean isSystemSettingKey(String key) {
        // List of known System settings keys
        return Arrays.asList("depth_wallpaper_enabled", "depth_wallpaper_subject_image_uri", "depth_wallpaper_opacity", "depth_wallpaper_offset_x", "depth_wallpaper_offset_y", "hide_developer_status_settings", "hide_screen_capture_status", "no_storage_restrict", "sensor_block_settings", "navbar_pulse_enabled", "lockscreen_pulse_enabled", "ambient_pulse_enabled", "audio_effect_mode_enabled", "statusbar_battery_bar", "statusbar_battery_bar_thickness", "statusbar_battery_bar_style", "statusbar_battery_bar_blend_color", "statusbar_battery_bar_blend_color_reverse", "statusbar_battery_bar_color", "statusbar_battery_bar_battery_low_color", "statusbar_battery_bar_animate", "statusbar_battery_bar_enable_charging_color", "statusbar_battery_bar_charging_color", "shake_gestures_enabled", "shake_gestures_action", "shake_gestures_intensity", "three_finger_gesture_action", "three_finger_long_press_action", "hardware_keys_disable", "swap_capacitive_keys", "additional_buttons", "anbi_enabled", "click_partial_screenshot", "power_menu", "power_end_call", "torch_long_press_power_gesture", "torch_long_press_power_timeout", "home_wake_screen", "home_answer_call", "hardware_keys_home_long_press", "hardware_keys_home_double_tap", "back_wake_screen", "hardware_keys_back_long_press", "menu_wake_screen", "hardware_keys_menu_press", "hardware_keys_menu_long_press", "assist_wake_screen", "hardware_keys_assist_press", "hardware_keys_assist_long_press", "app_switch_wake_screen", "hardware_keys_app_switch_press", "hardware_keys_app_switch_long_press", "camera_wake_screen", "camera_sleep_on_release", "camera_launch", "volume_wake_screen", "volume_answer_call", "volbtn_music_controls", "volume_key_cursor_control", "swap_volume_buttons", "theme_style", "notification_sound_vib_screen_on", "alert_slider_notifications", "notification_guts_kill_app_button", "island_notification", "less_boring_heads_up", "heads_up_timeout", "flashlight_on_call", "flashlight_on_call_ignore_dnd", "flashlight_on_call_rate", "battery_lights", "notification_lights", "sensor_block", "game_props_json_file_preference", "update_pif_json", "pif_json_file_preference", "wiki_link", "android.theme.customization.lockscreen_clock_font", "depth_wallpaper", "kg_user_switcher_enabled", "ls_media_art_enabled", "ls_media_art_filter", "ls_media_art_fade_level", "lockscreen_battery_info", "lockscreen_weather_enabled", "lockscreen_weather_location", "lockscreen_weather_text", "double_tap_sleep_lockscreen", "udfps_icon_picker", "udfps_recognizing_animation_preview", "fp_success_vibrate", "fp_error_vibrate", "enable_ripple_effect", "lockscreen_enable_power_menu", "notification_light_color_auto", "notification_light_pulse_override", "notification_light_screen_on_enable", "notification_light_pulse_custom_enable", "status_bar_icons", "status_bar_clock", "clock", "network_traffic_settings", "data_disabled_icon", "show_fourg_icon", "bluetooth_show_battery", "wifi_standard_icon", "statusbar_colored_icons", "statusbar_max_notifications", "statusbar_notif_count", "status_bar_logo", "status_bar_logo_position", "status_bar_logo_style", "status_bar_battery_style", "status_bar_show_battery_percent", "status_bar_battery_text_charging", "batterybar", "statusbar_top_padding", "statusbar_left_padding", "statusbar_right_padding", "double_tap_sleep_gesture", "status_bar_brightness_control", "qs_quick_pulldown", "rising_changelog", "custom_aod_image_enabled", "lockscreen_custom_image", "lockscreen_widgets_enabled", "lockscreen_display_widgets", "main_custom_widgets1", "main_custom_widgets2", "custom_widgets1", "custom_widgets2", "custom_widgets3", "custom_widgets4", "screenshot", "onthego", "airplane", "users", "lockdown", "emergency", "devicecontrols", "doze_fragment", "bootanimation_fragment", "android.theme.customization.fonts", "android.theme.customization.icon_pack", "android.theme.customization.adaptive_icon_shape", "monet_engine", "android.theme.customization.navbar", "settings_theme_style", "android.theme.customization.signal_icon", "android.theme.customization.style", "custom_volume_styles", "android.theme.customization.wifi_icon", "charging_animation", "show_multi_user_avatar_on_homepage", "screen_off_animation", "adaptive_playback_timeout", "island_notification_now_playing", "display_cutout_force_fullscreen_settings", "gaming_mode", "gestures", "navigation", "security", "spoofing", "quickswitch", "enable_rotation_button", "smart_pixels", "backup_personalization_settings", "restore_personalization_settings", "sound_engine", "pulse_settings", "volume_steps", "adaptive_playback_settings", "screenshot_shutter_sound", "show_app_volume", "volume_sound_haptics", "vibrate_on_connect", "vibrate_on_callwaiting", "vibrate_on_disconnect", "status_bar_clock_auto_hide", "status_bar_clock_auto_hide_hduration", "status_bar_clock_auto_hide_sduration", "status_bar_clock_seconds", "statusbar_clock_chip", "status_bar_am_pm", "status_bar_clock_date_display", "status_bar_clock_date_position", "status_bar_clock_date_style", "status_bar_clock_date_format", "battery_light_pulse", "battery_light_full_charge_disabled", "qs_bt_auto_on", "qs_header_image", "qs_colored_icons", "qs_battery_style", "qs_show_battery_percent", "secure_lockscreen_qs_disabled", "qs_dual_tone", "qs_compact_media_player_mode", "qs_transparency", "qs_widgets_enabled", "qs_widgets_photo_showcase_enabled", "qs_widgets_photo_showcase_image", "qs_tile_ui_style", "qs_panel_style", "qs_tile_label_hide", "qs_tile_label_size", "qs_tile_vertical_layout", "qs_layout_columns", "qs_layout_columns_landscape", "qs_panel_row_count", "qs_panel_row_count_landscape", "qqs_layout_rows", "qs_tile_animation_style", "qs_tile_animation_duration", "qs_tile_animation_interpolator", "qs_show_data_usage", "notification_material_dismiss", "always_on_display_schedule", "doze_device_settings", "edge_light_settings", "navbar_visibility", "pixel_nav_animation", "navigation_bar_menu_arrow_keys", "hide_ime_space_enable", "navigation_back_long_press", "navigation_home_long_press", "navigation_home_double_tap", "navigation_app_switch_long_press", "navigation_bar_edge_long_swipe", "smart_pixels_enable", "smart_pixels_pattern", "smart_pixels_on_power_save", "smart_pixels_shift_timeout", "qs_header_provider", "daylight_header_pack", "custom_header_browse", "file_header_select", "status_bar_custom_header_height", "status_bar_custom_header_shadow", "edge_light_enabled", "edge_light_always_trigger_on_pulse", "edge_light_repeat_animation", "edge_light_color_mode", "edge_light_custom_color").contains(key);
    }

    private boolean isSecureSettingKey(String key) {
        // List of known Secure settings keys
        return Arrays.asList("window_ignore_secure", "pulse_render_style", "pulse_smoothing_enabled", "pulse_vertical_mirror", "visualizer_center_mirrored", "pulse_color_mode", "pulse_custom_gravity", "pulse_color_user", "pulse_lavalamp_speed", "pulse_custom_dimen", "pulse_custom_div", "pulse_filled_block_size", "pulse_empty_block_size", "pulse_custom_fudge_factor", "pulse_solid_units_rounded", "pulse_solid_units_opacity", "pulse_solid_units_count", "pulse_solid_fudge_factor", "doze_always_on_auto_mode", "monet_engine_custom_color", "monet_engine_color_override", "monet_engine_custom_bgcolor", "monet_engine_bgcolor_override", "monet_engine_luminance_factor", "monet_engine_chroma_factor", "monet_engine_tint_background", "show_clipboard_overlay", "screen_off_udfps_enabled", "enable_camera_privacy_indicator", "enable_location_privacy_indicator", "enable_projection_privacy_indicator", "advanced_reboot", "advanced_reboot_secured", "gms_enabled", "pocket_mode_enabled", "always_on_pocket_mode_enabled", "volume_panel_on_left", "volume_show_media_button", "volume_show_volume_percent", "volume_dialog_dismiss_timeout", "qs_show_brightness_slider", "qs_brightness_slider_position", "qs_show_auto_brightness", "doze_enabled", "doze_always_on", "doze_on_charge", "pulse_on_new_tracks", "doze_pick_up_gesture", "doze_tilt_gesture", "doze_handwave_gesture", "doze_pocket_gesture", "raise_to_wake_gesture", "doze_gesture_vibrate", "navbar_layout_views", "navbar_inverse_layout", "network_traffic_location", "network_traffic_mode", "network_traffic_autohide", "network_traffic_autohide_threshold", "network_traffic_refresh_interval", "network_traffic_units", "network_traffic_hidearrow").contains(key);
    }

    private boolean isGlobalSettingKey(String key) {
        // List of known Global settings keys
        return Arrays.asList("heads_up_notifications_enabled").contains(key);
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_misc) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();

                    boolean mPocketJudgeSupported = res.getBoolean(
                            com.android.internal.R.bool.config_pocketModeSupported);
                    if (!mPocketJudgeSupported)
                        keys.add(POCKET_JUDGE);

                    return keys;
                }
            };
}
