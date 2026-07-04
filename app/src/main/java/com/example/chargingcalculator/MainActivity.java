package com.example.chargingcalculator;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chargingcalculator.databinding.ActivityMainBinding;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ChargingPrefs";
    private static final String KEY_PRICE = "price_per_hour";
    private static final float DEFAULT_PRICE = 6.50f;

    // 标记当前OCR模式：0=识别开始时间, 1=识别结束时间, 2=一键识别全图（自动分配）
    private static final int OCR_MODE_START = 0;
    private static final int OCR_MODE_END   = 1;
    private static final int OCR_MODE_AUTO  = 2;
    private int ocrMode = OCR_MODE_START;

    // 兼容旧代码
    private boolean isPickingStartTime = true;

    private ActivityMainBinding binding;
    private SharedPreferences prefs;
    private TextRecognizer recognizer;

    private final BroadcastReceiver chargingNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !ChargingNotificationStore.ACTION_NOTIFICATION_TIMES_CHANGED.equals(intent.getAction())) {
                return;
            }
            refreshAutoListenerState();
            applySavedNotificationTimes();
            if (intent.getBooleanExtra(ChargingNotificationStore.EXTRA_TIMES_CHANGED, false)) {
                Toast.makeText(MainActivity.this, "已从通知读取充电时间", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // 图片选择启动器
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        runOcr(imageUri);
                    }
                }
            });

    // 权限请求启动器
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                boolean allGranted = true;
                for (Boolean g : granted.values()) {
                    if (!g) { allGranted = false; break; }
                }
                if (allGranted) {
                    openImagePicker();
                } else {
                    Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

        initViews();
        loadSavedPrice();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ChargingNotificationStore.ACTION_NOTIFICATION_TIMES_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(chargingNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(chargingNotificationReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(chargingNotificationReceiver);
        super.onStop();
    }

    private void initViews() {
        binding.tvAppVersion.setText(
                String.format(Locale.getDefault(), "版本 %s", BuildConfig.VERSION_NAME));

        // ---- 保存默认单价 ----
        binding.btnSavePrice.setOnClickListener(v -> saveDefaultPrice());

        binding.btnOpenNotificationSettings.setOnClickListener(v -> handleNotificationAction());

        // ---- 开始时间：时间选择器 ----
        binding.btnPickStartTime.setOnClickListener(v -> showTimePicker(true));

        // ---- 结束时间：时间选择器 ----
        binding.btnPickEndTime.setOnClickListener(v -> showTimePicker(false));

        // ---- OCR：识别开始时间 ----
        binding.btnOcrStart.setOnClickListener(v -> {
            ocrMode = OCR_MODE_START;
            isPickingStartTime = true;
            checkPermissionAndPickImage();
        });

        // ---- OCR：识别结束时间 ----
        binding.btnOcrEnd.setOnClickListener(v -> {
            ocrMode = OCR_MODE_END;
            isPickingStartTime = false;
            checkPermissionAndPickImage();
        });

        // ---- OCR：一键识别全图（自动分配开始+结束时间）----
        binding.btnOcrAuto.setOnClickListener(v -> {
            ocrMode = OCR_MODE_AUTO;
            checkPermissionAndPickImage();
        });

        // ---- 计算按钮 ----
        binding.btnCalculate.setOnClickListener(v -> calculate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAutoListenerState();
        applySavedNotificationTimes();
    }

    private void openNotificationListenerSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void handleNotificationAction() {
        if (!ChargingNotificationStore.isNotificationListenerEnabled(this)) {
            openNotificationListenerSettings();
            return;
        }

        if (!ChargingNotificationListenerService.isConnected()) {
            Toast.makeText(this, "监听服务未连接，请关闭再开启通知权限", Toast.LENGTH_LONG).show();
            openNotificationListenerSettings();
            return;
        }

        boolean changed = ChargingNotificationListenerService.scanActiveNotificationsNow();
        refreshAutoListenerState();
        applySavedNotificationTimes();
        Toast.makeText(this,
                changed ? "已从当前通知栏读取充电时间" : "已检查通知栏，未找到充电提醒",
                Toast.LENGTH_SHORT).show();
    }

    private void refreshAutoListenerState() {
        boolean enabled = ChargingNotificationStore.isNotificationListenerEnabled(this);
        boolean connected = ChargingNotificationListenerService.isConnected();
        if (!enabled) {
            binding.tvAutoListenerStatus.setText("通知权限：未开启");
            binding.btnOpenNotificationSettings.setText("开启");
        } else if (connected) {
            binding.tvAutoListenerStatus.setText("通知权限：已开启，监听中");
            binding.btnOpenNotificationSettings.setText("刷新");
        } else {
            binding.tvAutoListenerStatus.setText("通知权限：已开启，服务未连接");
            binding.btnOpenNotificationSettings.setText("重连");
        }
        binding.btnOpenNotificationSettings.setVisibility(View.VISIBLE);
    }

    private void applySavedNotificationTimes() {
        ChargingNotificationStore.Snapshot snapshot = ChargingNotificationStore.load(this);
        if (!TextUtils.isEmpty(snapshot.startTime)) {
            binding.etStartTime.setText(snapshot.startTime);
            binding.tvOcrStartResult.setText("最近开始通知：" + snapshot.startTime);
            binding.tvOcrStartResult.setVisibility(View.VISIBLE);
        }
        if (!TextUtils.isEmpty(snapshot.endTime)) {
            binding.etEndTime.setText(snapshot.endTime);
            binding.tvOcrEndResult.setText("最近结束通知：" + snapshot.endTime);
            binding.tvOcrEndResult.setVisibility(View.VISIBLE);
        }
    }

    // ========================================================
    //  单价存储与加载
    // ========================================================
    private void loadSavedPrice() {
        float savedPrice = prefs.getFloat(KEY_PRICE, DEFAULT_PRICE);
        binding.etPricePerHour.setText(String.format(Locale.getDefault(), "%.2f", savedPrice));
        binding.tvSavedPrice.setText(
                String.format(Locale.getDefault(), "已保存默认单价：%.2f 元/小时", savedPrice));
    }

    private void saveDefaultPrice() {
        String priceStr = getText(binding.etPricePerHour);
        if (TextUtils.isEmpty(priceStr)) {
            Toast.makeText(this, "请先输入单价", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            float price = Float.parseFloat(priceStr);
            if (price <= 0) throw new NumberFormatException();
            prefs.edit().putFloat(KEY_PRICE, price).apply();
            binding.tvSavedPrice.setText(
                    String.format(Locale.getDefault(), "已保存默认单价：%.2f 元/小时", price));
            Toast.makeText(this, "默认单价已保存", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "单价格式不正确", Toast.LENGTH_SHORT).show();
        }
    }

    // ========================================================
    //  时间选择器（TimePickerDialog）
    // ========================================================
    private void showTimePicker(boolean forStart) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // 如果已有内容，解析后预填
        String existing = getText(forStart ? binding.etStartTime : binding.etEndTime);
        int[] parsed = parseHourMinute(existing);
        if (parsed != null) { hour = parsed[0]; minute = parsed[1]; }

        new TimePickerDialog(this, (view, h, m) -> {
            String formatted = String.format(Locale.getDefault(), "%02d:%02d", h, m);
            if (forStart) binding.etStartTime.setText(formatted);
            else          binding.etEndTime.setText(formatted);
        }, hour, minute, true).show();
    }

    // ========================================================
    //  计算逻辑
    // ========================================================
    private void calculate() {
        String startStr = getText(binding.etStartTime);
        String endStr   = getText(binding.etEndTime);
        String priceStr = getText(binding.etPricePerHour);

        if (TextUtils.isEmpty(startStr)) {
            Toast.makeText(this, "请输入充电开始时间", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(endStr)) {
            Toast.makeText(this, "请输入充电结束时间", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(priceStr)) {
            Toast.makeText(this, "请输入充电单价", Toast.LENGTH_SHORT).show();
            return;
        }

        int[] start = parseHourMinuteSecond(startStr);
        int[] end   = parseHourMinuteSecond(endStr);

        if (start == null) {
            Toast.makeText(this, "开始时间格式错误，请用 HH:mm 或 HH:mm:ss", Toast.LENGTH_SHORT).show();
            return;
        }
        if (end == null) {
            Toast.makeText(this, "结束时间格式错误，请用 HH:mm 或 HH:mm:ss", Toast.LENGTH_SHORT).show();
            return;
        }

        float price;
        try {
            price = Float.parseFloat(priceStr);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "单价格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        // 转换为秒
        long startSec = start[0] * 3600L + start[1] * 60L + start[2];
        long endSec   = end[0]   * 3600L + end[1]   * 60L + end[2];

        // 跨天处理
        if (endSec < startSec) {
            endSec += 24 * 3600L;
        }

        long diffSec = endSec - startSec;
        long hours   = diffSec / 3600;
        long minutes = (diffSec % 3600) / 60;
        long seconds = diffSec % 60;

        // 精确到秒的小时数
        double totalHours = diffSec / 3600.0;
        double amount     = totalHours * price;

        // 显示结果
        binding.cardResult.setVisibility(View.VISIBLE);

        String durationText;
        if (seconds > 0) {
            durationText = String.format(Locale.getDefault(),
                    "充电时长：%d 小时 %d 分钟 %d 秒", hours, minutes, seconds);
        } else {
            durationText = String.format(Locale.getDefault(),
                    "充电时长：%d 小时 %d 分钟", hours, minutes);
        }
        binding.tvDuration.setText(durationText);
        binding.tvAmount.setText(String.format(Locale.getDefault(), "¥ %.2f", amount));
        binding.tvDetail.setText(String.format(Locale.getDefault(),
                "%.4f 小时 × %.2f 元/小时", totalHours, price));
    }

    // ========================================================
    //  OCR 图片识别
    // ========================================================
    private void checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES});
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void runOcr(Uri imageUri) {
        Toast.makeText(this, "正在识别时间，请稍候...", Toast.LENGTH_SHORT).show();
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String fullText = visionText.getText();
                        if (ocrMode == OCR_MODE_AUTO) {
                            handleOcrAutoResult(fullText);
                        } else {
                            handleOcrSingleResult(fullText);
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "识别失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
        } catch (IOException e) {
            Toast.makeText(this, "无法读取图片：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 单字段模式：仅填充开始或结束时间
     */
    private void handleOcrSingleResult(String text) {
        String chosen = OcrTimeParser.parseSingle(text, isPickingStartTime);
        List<String> times = OcrTimeParser.extractTimes(text);
        if (chosen == null) {
            Toast.makeText(this, "未在图片中识别到时间，请手动输入", Toast.LENGTH_LONG).show();
            updateOcrResultView("未识别到时间");
            return;
        }
        applyTime(chosen);
        String fieldName = isPickingStartTime ? "最近开始提醒" : "最近结束提醒";
        String msg = fieldName + "：" + chosen;
        if (times.size() > 1) {
            msg += "\n全部：" + joinTimes(times);
        }
        updateOcrResultView(msg);
    }

    /**
     * 自动模式：优先按"开始"/"结束"关键词归类时间，缺失字段再从全图时间中兜底。
     */
    private void handleOcrAutoResult(String text) {
        OcrTimeParser.Result parsed = OcrTimeParser.parseAuto(text);
        String startTime = parsed.startTime;
        String endTime   = parsed.endTime;

        // --- 应用结果 ---
        if (startTime == null && endTime == null) {
            Toast.makeText(this, "未识别到充电时间，请手动输入", Toast.LENGTH_LONG).show();
            binding.tvOcrStartResult.setText("未识别到时间");
            binding.tvOcrStartResult.setVisibility(View.VISIBLE);
            return;
        }

        if (startTime != null) {
            binding.etStartTime.setText(startTime);
            binding.tvOcrStartResult.setText("最近开始提醒：" + startTime);
            binding.tvOcrStartResult.setVisibility(View.VISIBLE);
        }
        if (endTime != null) {
            binding.etEndTime.setText(endTime);
            binding.tvOcrEndResult.setText("最近结束提醒：" + endTime);
            binding.tvOcrEndResult.setVisibility(View.VISIBLE);
        }

        Toast.makeText(this,
                "识别完成！开始：" + (startTime != null ? startTime : "未找到")
                + "  结束：" + (endTime != null ? endTime : "未找到"),
                Toast.LENGTH_LONG).show();
    }

    private void applyTime(String time) {
        if (isPickingStartTime) {
            binding.etStartTime.setText(time);
        } else {
            binding.etEndTime.setText(time);
        }
    }

    private void updateOcrResultView(String msg) {
        if (isPickingStartTime) {
            binding.tvOcrStartResult.setText(msg);
            binding.tvOcrStartResult.setVisibility(View.VISIBLE);
        } else {
            binding.tvOcrEndResult.setText(msg);
            binding.tvOcrEndResult.setVisibility(View.VISIBLE);
        }
    }

    private String joinTimes(List<String> times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(times.get(i));
        }
        return sb.toString();
    }

    // ========================================================
    //  工具方法
    // ========================================================
    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    /** 解析 HH:mm 或 HH:mm:ss，返回 [hour, minute, second]，失败返回 null */
    private int[] parseHourMinuteSecond(String s) {
        if (TextUtils.isEmpty(s)) return null;
        String[] parts = s.split(":");
        try {
            if (parts.length == 2) {
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                if (h < 0 || h > 23 || m < 0 || m > 59) return null;
                return new int[]{h, m, 0};
            } else if (parts.length == 3) {
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                int sec = Integer.parseInt(parts[2].trim());
                if (h < 0 || h > 23 || m < 0 || m > 59 || sec < 0 || sec > 59) return null;
                return new int[]{h, m, sec};
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    /** 解析 HH:mm，仅返回 [hour, minute]，用于 TimePickerDialog 预填 */
    private int[] parseHourMinute(String s) {
        int[] full = parseHourMinuteSecond(s);
        if (full == null) return null;
        return new int[]{full[0], full[1]};
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizer.close();
    }
}
