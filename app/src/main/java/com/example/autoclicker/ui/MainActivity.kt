package com.example.autoclicker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autoclicker.R
import com.example.autoclicker.core.AppContainer
import com.example.autoclicker.core.ClickerAccessibilityService
import com.example.autoclicker.databinding.ActivityMainBinding
import com.example.autoclicker.model.ClickConfig
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.service.ClickerForegroundService
import com.google.gson.Gson
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var adapter: PointAdapter
    private var editingPoint: ClickPoint? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户选择后继续 */ }

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshPermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        requestPermissionsIfNeeded()
        refreshPermissionStatus()
    }

    private fun setupRecyclerView() {
        adapter = PointAdapter(
            onChanged = { commitPointsFromAdapter() },
            onPick = { point -> startPicking(point) }
        )
        binding.recyclerPoints.layoutManager = LinearLayoutManager(this)
        binding.recyclerPoints.adapter = adapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.config.collect { config ->
                    adapter.submit(config.points)
                    binding.etLoopCount.setText(config.loopCount.toString())
                    binding.etJitterPercent.setText(config.jitterPercent.toString())
                    binding.tvStatus.text = getString(R.string.hint_ready)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppContainer.scheduler.running.collect { running ->
                    binding.btnStart.text = getString(if (running) R.string.stop else R.string.start)
                    AppContainer.floatingWindow?.updateState(running, AppContainer.scheduler.progress.value)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppContainer.scheduler.progress.collect { progress ->
                    binding.tvStatus.text = progress
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.profiles.collect { profiles ->
                    val names = listOf(getString(R.string.select_profile)) + profiles.keys.sorted()
                    binding.spinnerProfiles.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnAddPoint.setOnClickListener {
            val point = ClickPoint(x = 300, y = 600)
            vm.addPoint(point)
        }

        binding.btnStart.setOnClickListener {
            if (AppContainer.scheduler.running.value) {
                stopClicking()
            } else {
                startClicking()
            }
        }

        binding.btnShowOverlay.setOnClickListener { showOverlay() }

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etProfileName.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                Toast.makeText(this, "请输入方案名称", Toast.LENGTH_SHORT).show()
            } else {
                vm.saveProfile(name)
                Toast.makeText(this, "已保存方案：$name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }

        binding.spinnerProfiles.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                if (pos == 0) return
                val name = binding.spinnerProfiles.selectedItem as? String ?: return
                lifecycleScope.launch {
                    vm.profiles.value[name]?.let { vm.loadProfile(it) }
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })

        binding.etLoopCount.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etLoopCount.text?.toString()?.toIntOrNull() ?: 0
                vm.config.value.loopCount = value; vm.persist()
            }
        }
        binding.etJitterPercent.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etJitterPercent.text?.toString()?.toIntOrNull() ?: 0
                vm.config.value.jitterPercent = value.coerceIn(0, 100); vm.persist()
            }
        }
    }

    private fun commitPointsFromAdapter() {
        // Adapter 持有的是 config.points 的同一批对象引用，这里只需触发保存
        vm.persist()
    }

    private fun startPicking(point: ClickPoint) {
        editingPoint = point
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        showOverlay()
        AppContainer.floatingWindow?.moveMarkerTo(point)
        AppContainer.floatingWindow?.isPicking = true
        AppContainer.floatingWindow?.onPointPicked = { x, y ->
            point.x = x
            point.y = y
            editingPoint = null
            vm.persist()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "已设置坐标 ($x, $y)", Toast.LENGTH_SHORT).show()
        }
        Toast.makeText(this, "点屏幕上的十字标记取点", Toast.LENGTH_SHORT).show()
    }

    private fun startClicking() {
        if (!ClickerAccessibilityService.isReady()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            return
        }
        if (vm.config.value.points.isEmpty()) {
            Toast.makeText(this, "请先添加至少一个点位", Toast.LENGTH_SHORT).show()
            return
        }
        val configJson = Gson().toJson(vm.config.value)
        val intent = Intent(this, ClickerForegroundService::class.java).apply {
            action = ClickerForegroundService.ACTION_START
            putExtra(ClickerForegroundService.EXTRA_CONFIG, configJson)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopClicking() {
        val intent = Intent(this, ClickerForegroundService::class.java).apply {
            action = ClickerForegroundService.ACTION_STOP
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        if (AppContainer.floatingWindow == null) {
            AppContainer.floatingWindow =
                com.example.autoclicker.overlay.FloatingWindowManager(this).apply {
                    onStart = { startClicking() }
                    onStop = { stopClicking() }
                }
        }
        AppContainer.floatingWindow?.show()
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPermissionStatus() {
        val accessibilityOn = ClickerAccessibilityService.isReady()
        val overlayOn = Settings.canDrawOverlays(this)
        binding.tvAccessStatus.text = getString(
            if (accessibilityOn) R.string.access_on else R.string.access_off
        )
        binding.tvOverlayStatus.text = getString(
            if (overlayOn) R.string.overlay_on else R.string.overlay_off
        )
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }
}
